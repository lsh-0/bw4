(ns bw.utils
  (:require
   [taoensso.timbre :refer [log debug info warn error spy]]
   [me.raynes.fs :as fs]
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [orchestra.spec.test :as st]
   [bw.specs :as sp]
   ))

(defn instrument
  "if `flag` is true, enables spec checking instrumentation, otherwise disables it."
  [flag]
  (if flag
    (do
      (st/instrument)
      (info "instrumentation is ON"))
    (do
      (st/unstrument)
      (info "instrumentation is OFF"))))

(defn-spec expand-path ::sp/file
  "given a path, expands any 'user' directories, relative directories and symbolic links"
  [path ::sp/file]
  (-> path fs/expand-home fs/normalized fs/absolute str))
