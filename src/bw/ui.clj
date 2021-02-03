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

(defn-spec select-result nil?
  "'selects' an item in the results list"
  [x any?]
  (swap! core/state assoc-in [:ui :selected-list] x)
  nil)
