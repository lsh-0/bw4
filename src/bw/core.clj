(ns bw.core
  (:require
   [clojure.tools.namespace.repl]
   [clojure.core.async :as async :refer [<! >! >!! <!! go]]
   [taoensso.timbre :refer [log debug info warn error spy]]
   [me.raynes.fs :as fs]
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [bw
    [utils :as utils]
    [specs :as sp]]))

(def testing? false)

(def -state-template
  {:cleanup [] ;; a list of functions that are called on application stop
   :publication nil ;; async/pub, sends messages to subscribers, if any
   :publisher nil ;; async/chan, `publication` reads from this channel and we write to it
   :service-list [] ;; list of known services. each service is a map, each map has a function
   ;; a service can store things in state, just not at the top level
   :service-state {:store {:storage-dir nil ;; in-memory store only (faster everything)
                           ;;:storage-dir "crux-store" ;; permanent store
                           }
                   :scheduler {:scheduler nil}}
   :in-repl? false
   :ui {:gui-showing? false ;; is the gui displayed or not?
        :disable-gui nil ;; gui pre-cleanup function
        :result-list [] ;; the results we're currently dealing with
        :selected-list [] ;; subset of `result-list` that are currently selected by the user

        :selected-service nil ;; the service the user is making requests to
        }

   ;;:known-topics #{} ;; set of all known available topics
   })

(def state nil)

(defn started?
  []
  (some? state))

(defn get-state
  [& path]
  (if (started?)
    (if path
      (get-in @state path)
      @state)
    (error "application has not been started, cannot access path:" path)))

(defn set-state
  [& path]
  (if (started?)
    (swap! state assoc-in (butlast path) (last path))
    (error "application has not been started, cannot update path:" (butlast path)))
  nil)

(defn add-cleanup
  [f]
  (swap! state update-in [:cleanup] conj f)
  nil)

(defn state-bind
  "executes given callback function when value at path in state map changes. 
  trigger is discarded if old and new values are identical"
  [path callback]
  (let [has-changed (fn [old-state new-state]
                      (not= (get-in old-state path)
                            (get-in new-state path)))
        wid (keyword (gensym callback)) ;; :foo.bar$baz@123456789
        rmwatch #(remove-watch state wid)]

    (add-watch state wid
               (fn [_ _ old-state new-state] ;; key, atom, old-state, new-state
                 (when (has-changed old-state new-state)
                   (debug (format "path %s triggered %s" path wid))
                   (try
                     (callback new-state)
                     (catch Exception uncaught-exc
                       (error uncaught-exc "error caught in watch! your callback *must* be catching these or the thread dies silently:" path))))))
    (add-cleanup rmwatch)
    nil))

(defn-spec mk-id ::sp/id
  []
  (java.util.UUID/randomUUID))

;; todo: should :topic-kw be optional here? why is a message tied to a topic?
;; it could be broadcast to any number of topics
(defn-spec message map?
  "creates a simple message that will go to those listening to the 'off-topic' topic by default"
  [topic-kw keyword?, msg any?] ;;map?]
  {:type :message
   :id (mk-id)
   :topic topic-kw
   :message msg
   :response-chan nil ;; a channel is supplied if the message sender wants to receive a response
   })

(defn-spec request map?
  "requests are messages that expect a response and come with a response channel"
  [topic-kw keyword?, msg any?] ;;map?]
  (assoc (message topic-kw msg)
         :response-chan (async/chan 1)))

(defn close-chan!
  "closes channel and empties it of any unconsumed items.
  functions still operating on items will *not* be affected."
  [chan]
  (async/close! chan)
  (async/go-loop []
    (when-let [dropped-val (<! chan)]
      (info (format "dropping %s: %s" (name (:type dropped-val)) (:id dropped-val)))
      (recur))))

;; should :topic be optional here, overriding the one in the `msg`?
(defn emit
  "publishes given `msg` and, if response channel available, returns a `future` that
  pulls a value from the message's `:response-chan` when dereferenced"
  [msg]
  (when-let [publisher (get-state :publisher)]
    (if-not msg
      (error "cannot emit 'nil' as a message")
      (do (async/put! publisher msg)
          (when-let [chan (:response-chan msg)]
            ;; response channel is closed after the result is put on channel.
            ;; see `-add-service`.
            (future
              (debug "pulling from chan" chan)
              (<!! chan)))))))

(defn-spec mkservice ::sp/service
  "returns a 'service' description that the app will use to know which services exist and where to send results"
  [service-id keyword?, topic keyword?, service-fn fn?]
  {:id service-id
   :topic topic
   :func service-fn

   ;; where messages accumulate for this service.
   ;; messages are pulled off and processed sequentially
   :input-chan nil ;; => (async/chan) in `register-service`
   ;;:pool 1 ;; todo: process incoming messages using a pool of workers
   })

(defn -add-service
  "subscribe given `service` to incoming messages and start processing incoming messages.
  returns the polling channel."
  [service topic-kw]
  (async/sub (get-state :publication) topic-kw (:input-chan service))
  ;;(debug (format "subbed chan '%s' to pub %s" (:input-chan service) (get-state :publication)))
  (add-cleanup (fn []
                 (async/unsub (get-state :publication) topic-kw (:input-chan service))))

  (debug (format "service %s waiting for messages on %s" (:id service) topic-kw))
  (async/go-loop []
    (when-let [msg (<! (:input-chan service))]
      (debug (format "service '%s' received message: %s" (:id service) msg))
      (let [resp-chan (:response-chan msg)
            result (try
                     ((:func service) msg)
                     (catch Exception e
                       (error e "unhandled exception executing service:" e)))]

        ;; when there is a response channel, stick the response on the channel, even if the response is nil


        (when resp-chan
          (debug "...response channel found, sending result to it:" result)
          ;; this implies a single response only.
          (when result
            (>! resp-chan result))
          (async/close! resp-chan))

        ;; otherwise, discard response

        (recur)))

    ;; message was nil (channel closed), die
    ))

(defn-spec register-service nil?
  "services are just functions waiting for messages they can handle and then doing them"
  [service-map map?]
  (let [service-map (assoc service-map :input-chan (async/chan))
        topic-kw (get service-map :topic :off-topic)

        ;; a service can do a once-off thing before it starts listening
        ;; todo: add complementary `:close-fn` ? no, that can be done by :cleanup
        ;;_ (when-let [init-fn (get service-map :init-fn)]
        ;;    (init-fn))

        subscription-polling (-add-service service-map topic-kw)]

    (swap! state update-in [:service-list] conj service-map)
    ;;(swap! state update-in [:known-topics] conj topic-kw)
    (add-cleanup (fn []
                   ;; closes the actors input channel and empties any pending items
                   (when-let [c (:input-chan service-map)]
                     (close-chan! c))
                   ;; break the actor's polling loop on the subscription
                   ;; with a closed `:input-chan` it shouldn't receive any new messages and
                   ;; it would remain indefinitely parked
                   (close-chan! subscription-polling))))
  nil)

(defn register-all-services
  [service-list]
  (run! register-service service-list))

(defn-spec find-service-list sequential?
  "given a namespace in the form of a keyword, returns the contents of `'bw.$ns/service-list`, if it exists"
  [ns-kw keyword?]
  (let [ns (->> ns-kw name (str "bw.") symbol)]
    (try
      (var-get (ns-resolve ns 'service-list))
      (catch Exception e
        (warn (format "service list not found: '%s/service-list" ns))))))

(defn find-all-services
  "finds the service-list for all given namespace keywords and returns a single list"
  [ns-kw-list]
  (mapcat find-service-list ns-kw-list))

(defn find-service-init
  [ns-kw]
  (let [ns (->> ns-kw name (str "bw.") symbol)]
    (try
      (var-get (ns-resolve ns 'init))
      (catch Exception e
        (debug (format "init not found: '%s/init" ns))))))

(defn find-all-service-init
  [ns-kw-list]
  (mapv find-service-init ns-kw-list))

(defn-spec detect-repl! nil?
  "if we're working from the REPL, we don't want the gui closing the session"
  []
  (swap! state assoc :in-repl? (utils/in-repl?))
  nil)

(defn init
  "app has been started at this point and state is available to be derefed."
  [& [opt-map]]
  (alter-var-root #'state (constantly (atom -state-template)))
  (utils/instrument true)
  (detect-repl!)
  (let [known-services [:core-services
                        :store :scheduler :github]
        known-services (get opt-map :service-list (find-all-services known-services))

        known-service-init [:store :scheduler]
        known-service-init (find-all-service-init known-service-init)

        publisher (async/chan)
        publication (async/pub publisher :topic)
        _ (add-cleanup #(close-chan! publisher))

        state-updates {:publisher publisher
                       :publication publication}

        ;; a map of initial state values can be passed in at init time that
        ;; are deep-merged after all other init has happened.
        ;; note: not sure if `merge-with merge` is best
        state-updates (merge-with merge state-updates (:initial-state opt-map))]

    (swap! state merge state-updates)

    (doseq [init-fn known-service-init]
      (debug "initialising" init-fn)
      (init-fn))

    (register-all-services known-services)))

(defn stop
  []
  (when state
    (doseq [clean-up-fn (:cleanup @state)]
      (debug "calling cleanup fn:" clean-up-fn)
      (clean-up-fn))
    (reset! state nil)))
