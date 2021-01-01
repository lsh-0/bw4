(ns bw.main
  (:refer-clojure :rename {test clj-test})
  (:require
   [taoensso.timbre :as timbre :refer [spy info warn error debug]]
   [clojure.test]
   [clojure.tools.namespace.repl :as tn :refer [refresh]]
   [clojure.string :refer [lower-case]]
   [me.raynes.fs :as fs]
   [gui.diff :refer [with-gui-diff]]
   [bw
    [core :as core]])
  (:gen-class))

(defn test
  [& [ns-kw fn-kw]]
  (clojure.tools.namespace.repl/refresh) ;; reloads all namespaces, including bw.whatever-test ones
  (timbre/with-merged-config {:level :debug
                              ;; ensure we're not writing logs to files
                              :appenders {:spit nil}}
    (if ns-kw
      (if (some #{ns-kw} [:main :core :store])
        (with-gui-diff
          (if fn-kw
            ;; `test-vars` will run the test but not give feedback if test passes OR test not found
            ;; slightly better than nothing
            (clojure.test/test-vars [(resolve (symbol (str "bw." (name ns-kw) "-test") (name fn-kw)))])
            (clojure.test/run-all-tests (re-pattern (str "bw." (name ns-kw) "-test")))))
        (error "unknown test file:" ns-kw))
      (clojure.test/run-all-tests #"bw\..*-test"))))

(defn find-service-list
  "given a namespace in the form of a keyword, returns the contents of `'bw.$ns/service-list`, if it exists"
  [ns-kw]
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

;;

(def known-services [:core :store])
(def known-service-init [:store])

(defn stop
  [state]
  (when state
    (doseq [clean-up-fn (:cleanup @state)]
      (debug "calling cleanup fn:" clean-up-fn)
      (clean-up-fn))
    (alter-var-root #'core/state (constantly nil))))

(defn start
  [& [opt-map]]
  (if core/state
    (warn "application already started")
    (do
      (alter-var-root #'core/state (constantly (atom core/-state-template)))
      (core/init (merge {:service-list (find-all-services known-services)} opt-map))

      ;; init any services that need it
      (doseq [init-fn (find-all-service-init known-service-init)]
        (debug "initialising" init-fn)
        (init-fn))

      true)))

(defn restart
  []
  (stop core/state)
  (start))
