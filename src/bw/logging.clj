(ns bw.logging
  (:require
   [taoensso.timbre :as timbre]
   [taoensso.timbre.appenders.core :refer [spit-appender]]
   [orchestra.core :refer [defn-spec]]
   [clojure.spec.alpha :as s]
   [bw
    [specs :as sp]]
   ))

;; https://github.com/ptaoussanis/timbre/blob/56d67dd274d7d11ab31624a70b4b5ae194c03acd/src/taoensso/timbre.cljc#L856-L858
(def colour-log-map
  {:debug :blue
   :info nil
   :warn :yellow
   :error :red
   :fatal :purple
   :report :blue})

(defn anon-println-appender
  "removes the hostname from the output format string"
  [data]
  (let [{:keys [?err timestamp_ msg_ level]} data
        level-colour (colour-log-map level)
        addon (some-> data :context :addon)
        label (or (:dirname addon)
                  (:name addon)
                  "app")
        pattern "%s [%s] [%s] %s"
        msg (force msg_)]
    (when ?err
      (println (timbre/stacktrace ?err)))

    (when-not (empty? msg)
      ;; looks like: "11:17:57.009 [info] [app] checking for updates"
      (println
       (timbre/color-str level-colour
                         (format
                          pattern
                          (force timestamp_)
                          (name level)
                          label
                          msg))))))


(def default-logging-config
  {:min-level :info
   :timestamp-opts {;;:pattern "yyyy-MM-dd HH:mm:ss.SSS"
                    :pattern "HH:mm:ss.SSS"
                    ;; default is `:utc`, `nil` sets tz to current locale.
                    :timezone nil}

   :appenders {:println {:enabled? true
                         :async? false
                         :output-fn :inherit
                         :fn anon-println-appender}}})
