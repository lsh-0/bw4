(ns bw.cloverage
  (:require
   [bw
    [core :as core]]
   [clojure.test :as test]
   [cloverage.coverage :as c]))

(comment
  "Cloverage hook. runs all of the application tests with profiling and spec checks enabled.
  These checks are disabled by default and only available if passed in from the REPL on app start.")

(defmethod c/runner-fn :boardwalk
  [_]
  (fn [ns-list]
    (with-redefs [core/testing? true]
      (apply require (map symbol ns-list))
      {:errors (reduce + ((juxt :error :fail)
                          (apply test/run-tests ns-list)))})))
