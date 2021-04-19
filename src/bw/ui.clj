(ns bw.ui
  (:require
   [orchestra.core :refer [defn-spec]]
   [taoensso.timbre :as timbre :refer [spy info warn error debug]]
   [bw
    [specs :as sp]
    [utils :as utils]
    [core :as core :refer [get-state]]]))

(defn-spec add-result nil?
  "appends an item `x` to the UI's list of 'results'"
  [x any?]
  (swap! core/state update-in [:ui :result-list] conj x)
  nil)

(defn-spec clear-results nil?
  []
  (swap! core/state assoc-in [:ui :result-list] [])
  (swap! core/state assoc-in [:ui :selected-list] [])
  nil)

(defn-spec select-result nil?
  "'selects' an item in the results list"
  [x any?]
  (swap! core/state assoc-in [:ui :selected-list] x)
  nil)


;; (mapv add-result (map bw.github/extract-repo (:body (bw.github/repo-list "lsh-0"))))


(defn-spec select-service! nil?
  [service-id keyword?]
  (when-let [service-map (->> (core/get-state :service-list) (filter #(= service-id (:id %))) first)]
    (swap! core/state assoc-in [:ui :selected-service] service-map))
  nil)

(defn-spec update-uin nil?
  [user-input string?]
  (swap! core/state assoc-in [:ui :user-input] user-input)
  nil)

(defn send-simple-request
  "sends a simple text message to the currently selected service and any results are added to the UI result-list"
  [msg]
  (when-let [selected-service (core/get-state :ui :selected-service)]
    (clear-results)
    (mapv add-result @(core/emit (core/request (:topic selected-service) (str msg))))))
