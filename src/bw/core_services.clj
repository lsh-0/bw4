(ns bw.core-services
  (:require
   [bw.core :as core]))

;;

(defn help-service
  [msg]
  (println "hello, world"))

(def service-list
  [(core/mkservice :info, :help, help-service)])
