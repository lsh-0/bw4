(ns bw.test-helper
  (:require
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [me.raynes.fs :as fs :refer [with-cwd]]
   [bw
    [core :as core]
    [main :as main]]
   ))

(defmacro with-running-app
  [& form]
  `(try
     (main/start {:initial-state {:service-state {:db {:storage-dir nil}}}})
     ~@form
     (finally
       (main/stop core/state))))

(defmacro with-opts-running-app
  [opts & form]
  `(try
     (main/start ~opts)
     ~@form
     (finally
       (main/stop core/state))))
