(ns bw.core
  (:require
   [clojure.tools.namespace.repl :refer [refresh]]
   [clojure.core.async :as async :refer [<! >! >!! <!! go]]
   [taoensso.timbre :refer [log debug info warn error spy]]
   [me.raynes.fs :as fs]
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]

   ;;[universe.utils :as utils :refer [in? mk-id]]
   ))

(def -state-template
  {:cleanup [] ;; a list of functions that are called on application stop
   :publication nil ;; async/pub, sends messages to subscribers, if any
   :publisher nil ;; async/chan, `publication` reads from this channel and we write to it
   :service-list [] ;; list of known services. each service is a map, each map has a function
   ;; a service can store things in state, just not at the top level
   :service-state {:store {:storage-dir nil ;; in-memory store only (faster everything)
                           ;;:storage-dir "crux-store" ;; permanent store
                           }}
   :known-topics #{} ;; set of all known available topics
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

(defn mk-id
  []
  (java.util.UUID/randomUUID))

(defn-spec message map?
  "creates a simple message that will go to those listening to the 'off-topic' topic by default"
  [topic-kw keyword?, msg map?]
  {:type :message
   :id (mk-id)
   :topic topic-kw
   :message msg
   :response-chan nil ;; a channel is supplied if the message sender wants to receive a response
   })

(defn-spec request map?
  "requests are messages that expect a response and come with a response channel"
  [topic-kw keyword?, msg map?]
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

(defn emit
  "publishes given `msg` and, if response channel available, returns a `future` that
  pulls a value from the message's `:response-chan` when dereferenced"
  [msg]
  (when-let [publisher (get-state :publisher)]
    (if-not msg
      (error "cannot emit 'nil' as a message")      
      (do
        (async/put! publisher msg)
        (when-let [chan (:response-chan msg)]
          ;; response channel is closed after the result is put on channel.
          ;; see `-add-service`.
          (future
            (<!! chan)))))))

(defn-spec mkservice map?
  [service-id keyword?, topic keyword?, service-fn fn?]
  {:id service-id
   :topic topic
   :func service-fn
   
   ;; where messages accumulate for this service.
   ;; messages are pulled off and processed sequentially
   :input-chan (async/chan)
   ;;:pool 1 ;; todo: process incoming messages using a pool of workers
   })

(defn -add-service
  "subscribe given `service` to incoming messages and start processing incoming messages.
  returns the polling channel."
  [service topic-kw]
  (async/sub (get-state :publication) topic-kw (:input-chan service))

  (debug (format "service %s waiting for messages on %s" (:id service) topic-kw))
  (async/go-loop []
    (when-let [msg (<! (:input-chan service))]
      (debug (format "service '%s' received message: %s" (:id service) msg))
      (let [resp-chan (:response-chan msg)
            result (try
                     ((:func service) msg)
                     (catch Exception e
                       (error e "unhandled exception executing service:" e)))
            ]

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

(defn register-service
  "services are just functions waiting for messages they can handle and then doing them"
  [service-map]
  (let [topic-kw (get service-map :topic :off-topic)

        ;; a service can do a once-off thing before it starts listening
        ;; todo: add complementary `:close-fn` ? no, that can be done by :cleanup
        ;;_ (when-let [init-fn (get service-map :init-fn)]
        ;;    (init-fn))

        subscription-polling (-add-service service-map topic-kw)

        ]
    (swap! state update-in [:service-list] conj service-map)
    (swap! state update-in [:known-topics] conj topic-kw)
    (add-cleanup (fn []
                   ;; closes the actors input channel and empties any pending items
                   (close-chan! (:input-chan service-map))
                   ;; break the actor's polling loop on the subscription
                   ;; with a closed `:input-chan` it shouldn't receive any new messages and
                   ;; it would remain indefinitely parked
                   (close-chan! subscription-polling))))
  nil)

(defn register-all-services
  [service-list]
  (run! register-service service-list))
  
(defn init
  "app has been started at this point and state is available to be derefed."
  [& [opt-map]]
  (let [publisher (async/chan)
        publication (async/pub publisher :topic)

        state-updates {:publisher publisher
                       :publication publication}

        ;; a map of initial state values can be passed in at init time that
        ;; are deep-merged after all other init has happened.
        ;; note: not sure if `merge-with merge` is best
        state-updates (merge-with merge state-updates (:initial-state opt-map))]

    (swap! state merge state-updates)

    (register-all-services (:service-list opt-map))

    ;;(test-query)))
    ))

;;

(defn help-service
  [msg]
  (println "hello, world"))

(def service-list
  []) ;;(mkservice :info, :help, help-service)])
                            
