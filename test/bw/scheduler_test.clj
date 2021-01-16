(ns bw.scheduler-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [bw
    [core :as core]
    [test-helper :as helper]
    [scheduler :as scheduler]]))

(defn fixture-test-service
  "ensures a simple test service exists"
  [f]
  (let [;; echo-service is listening for messages arriving at :test-messages and simply prints them to stdout
        topic :test-messages
        service (core/mkservice :echo-service
                                topic
                                (fn [msg]
                                  (println "--->" msg)))
        
        ]
    (helper/with-running-app+opts {:service-list (conj scheduler/service-list service)}
      (f))))

(use-fixtures :each fixture-test-service)

(deftest schedule-a-message
  (testing "a simple message can be scheduled"
    (let [;; this message:
          scheduled-message {:foo :bar}
          ;; will be sent to this topic:
          scheduled-topic :test-messages
          ;; every minute, forever.
          schedule "* * * * *"

          topic :scheduler/add
          message (core/request topic {:topic scheduled-topic
                                       :message scheduled-message
                                       :schedule schedule})
          result @(core/emit message)
          ;; _ (Thread/sleep (* 1000 60)) ;; if you want to see the scheduled task appear ;)

          expected {:schedule schedule}
          
          ]
      (is (contains? result :id))
      (is (= expected (dissoc result :id))))))
