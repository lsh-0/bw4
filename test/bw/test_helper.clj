(ns bw.test-helper
  (:require
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [me.raynes.fs :as fs :refer [with-cwd]]
   [bw
    [utils :as utils]
    [core :as core]
    [main :as main]]))

(defn fixture-tempcwd
  "each `deftest` is executed in a new and self-contained location, accessible as fs/*cwd*.
  `(testing ...` sections share the same fixture. beware of cache hits."
  [f]
  (let [temp-dir-path (utils/expand-path (fs/temp-dir "bw-test."))]
    (try
      (debug "sanity check. stopping application if it hasn't already been stopped.")
      (main/stop)
      (with-cwd temp-dir-path
        (debug "created temp working directory" fs/*cwd*)
        (f))
      (finally
        (debug "destroying temp working directory" temp-dir-path)
        (fs/delete-dir temp-dir-path)))))

(defmacro with-running-app
  [& form]
  `(try
     (main/start {:initial-state {:service-state {:store {:storage-dir nil}}}})
     ~@form
     (finally
       (main/stop))))

(defmacro with-running-app+opts
  [opts & form]
  `(try
     (main/start ~opts)
     ~@form
     (finally
       (main/stop))))
