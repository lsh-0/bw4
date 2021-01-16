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
    [utils :as utils]
    [core :as core]])
  (:gen-class))

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (error ex "Uncaught exception on" (.getName thread)))))

(def known-test-ns [:main :core :store :scheduler])

(defn test
  [& [ns-kw fn-kw]]
  (clojure.tools.namespace.repl/refresh) ;; reloads all namespaces, including bw.whatever-test ones
  (timbre/with-merged-config {:level :debug
                              ;; ensure we're not writing logs to files
                              :appenders {:spit nil}}
    (if ns-kw
      (if (some #{ns-kw} known-test-ns)
        (with-gui-diff
          (if fn-kw
            ;; `test-vars` will run the test but not give feedback if test passes OR test not found
            ;; slightly better than nothing
            (clojure.test/test-vars [(resolve (symbol (str "bw." (name ns-kw) "-test") (name fn-kw)))])
            (clojure.test/run-all-tests (re-pattern (str "bw." (name ns-kw) "-test")))))
        (error "unknown test file:" ns-kw))
      (clojure.test/run-all-tests #"bw\..*-test"))))

(defn stop
  []
  (core/stop))

(defn start
  [& [opt-map]]
  (core/init opt-map)
  true)

(defn restart
  []
  (stop)
  (start))

(utils/instrument true)
