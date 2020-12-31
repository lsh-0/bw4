(ns bw.core-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [bw
    [core :as core]
    [test-helper :as helper]]))

(deftest init
  (testing "app can run with no services"
    (helper/with-running-app
      (is (core/started?)))))

(deftest async-message-sending
  (testing "app can send messages to listening services asynchronously by simply emiting a message."
    (let [test-var (atom nil)
          topic :test-messages
          service (core/mkservice :test-service
                                  topic
                                  (fn [msg]
                                    (reset! test-var (-> msg :message :foo))))
          message (core/message topic {:foo :bar})]
      (helper/with-opts-running-app {:service-list [service]}
        (core/emit message)
        (Thread/sleep 10) ;; doop de doo. this is the uncoordinated, asynchronous bit.
        (is (= :bar @test-var))))))

(deftest message-sending
  (testing "app can send messages to listening services synchronously by dereferencing the result"
    (let [topic :test-messages
          service (core/mkservice :test-service
                                  topic
                                  (fn [msg]
                                    (-> msg :message :foo)))
          message (core/request topic {:foo :bar})]
      (helper/with-opts-running-app {:service-list [service]}
        (is (= :bar @(core/emit message))))))

  (testing "nil results can be sent"
    (let [topic :test-messages
          service (core/mkservice :test-service topic (fn [msg] nil))
          message (core/request topic {:foo :bar})]
      (helper/with-opts-running-app {:service-list [service]}
        (is (= nil @(core/emit message)))))))
