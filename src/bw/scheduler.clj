(ns bw.scheduler
  (:require
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [taoensso.timbre :refer [log debug info warn error spy]]
   [bw.core :as core])
  (:import
   [it.sauronsoftware.cron4j Scheduler SchedulingPattern]))

(defn-spec valid-schedule? boolean?
  [schedule string?]
  (it.sauronsoftware.cron4j.SchedulingPattern/validate schedule))

(defn get-scheduler
  []
  (core/get-state :service-state :scheduler :scheduler))

(defn-spec started? boolean?
  []
  (if-let [s (get-scheduler)]
    (.isStarted s)
    false))

(defn-spec schedule-fn (s/or :ok :scheduler/schedule :failed nil?)
  [cron string?, f fn?]
  (if (and (started?)
           (valid-schedule? cron))
    {:id (.schedule (get-scheduler) cron f)
     :type :scheduler/schedule
     :cron cron}
    (error (str "scheduler not started or given schedule is invalid: " cron))))

(defn-spec schedule-message (s/or :ok :scheduler/schedule, :failed nil?)
  [cron :scheduler/cron, topic keyword?, message map?]
  (let [message (core/message topic message)
        send-message-fn #(core/emit message)]
    (schedule-fn cron send-message-fn)))

;;

(defn init
  "creates a `it.sauronsoftware.cron4j.Scheduler` object and stores it in application-service state.
  Used to start/stop/restart/schedule jobs."
  []
  (let [scheduler-inst (doto (Scheduler.)
                         (.start))

        ;; jobs-in-config (core/get-state :config :scheduled-jobs)
        ;; 
        ]
    (core/set-state :service-state :scheduler :scheduler scheduler-inst)
    (core/add-cleanup (fn []
                        (when-let [s (get-scheduler)]
                          (when (.isStarted s)
                            (.stop scheduler-inst))))))
  nil)

;;

(defn add-scheduled-service
  [{msg :message}]
  (schedule-message (:cron msg) (:topic msg) (:message msg)))

(def service-list
  [(core/mkservice :schedule, :scheduler/add, add-scheduled-service)])

