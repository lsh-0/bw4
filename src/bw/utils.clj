(ns bw.utils
  (:require
   [clojure.walk :as walk]
   [taoensso.timbre :refer [log debug info warn error spy]]
   [me.raynes.fs :as fs]
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [orchestra.spec.test :as st]
   [bw.specs :as sp]))

(defn underscores-to-hyphens
  "Recursively transforms all map keys in coll with the transform-key fn."
  [coll]
  (letfn [(transform [x]
            (if-not (map? x)
              x
              (into {} (map (fn [[k v]]
                              (let [new-key (-> k name (.replace \_ \-) keyword)]
                                [new-key v]))
                            x))))]
    (walk/postwalk transform coll)))

(defn repl-stack-element?
  [stack-element]
  (and (= "clojure.main$repl" (.getClassName  stack-element))
       (= "doInvoke"          (.getMethodName stack-element))))

(defn in-repl?
  "returns `true` if the current thread is using the REPL"
  []
  (let [current-stack-trace (.getStackTrace (Thread/currentThread))]
    (some repl-stack-element? current-stack-trace)))

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
