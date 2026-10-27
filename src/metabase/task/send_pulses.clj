(ns metabase.task.send-pulses
  "Tasks related to running `Pulses`."
  (:require [clj-time.core :as time]
            [clj-time.predicates :as timepr]
            [clojure.tools.logging :as log]
            [clojurewerkz.quartzite.jobs :as jobs]
            [clojurewerkz.quartzite.schedule.cron :as cron]
            [clojurewerkz.quartzite.triggers :as triggers]
            [metabase.driver :as driver]
            [metabase.models.pulse :as pulse]
            [metabase.models.pulse-channel :as pulse-channel]
            [metabase.models.task-history :as task-history]
            metabase.pulse
            [metabase.task :as task]
            [metabase.util.i18n :refer [trs]]
            [schema.core :as s]))

;;; ------------------------------------------------- PULSE SENDING --------------------------------------------------

(defn- log-pulse-exception [pulse-id exception]
  (log/error exception (trs "Error sending Pulse {0}" pulse-id)))

(def ^:private Hour
  (s/constrained
   s/Int
   #(and (<= 0 %) (>= 23 %))
   "valid hour"))

(def ^:private Weekday
  (s/pred pulse-channel/day-of-week? "valid day of week"))

(def ^:private MonthDay
  (s/enum :first :last :mid :other))

(def ^:private MonthWeek
  (s/enum :first :last :other))

(s/defn ^:private send-pulses!
  "Send any `Pulses` which are scheduled to run in the given day/hour. We find all `Pulses` that are scheduled to run,
  keep the ones whose ID satisfies `pulse-id-pred`, and send them. The `on-error` function is called if an exception
  is thrown when sending the pulse. Since this is a background process, the exception is only logged and not surfaced
  to the user. The `on-error` function makes it easier to test for when an error doesn't occur"
  ([hour weekday monthday monthweek]
   (send-pulses! hour weekday monthday monthweek log-pulse-exception))

  ([hour weekday monthday monthweek on-error]
   (send-pulses! hour weekday monthday monthweek on-error (constantly true)))

  ([hour :- Hour, weekday :- Weekday, monthday :- MonthDay, monthweek :- MonthWeek, on-error, pulse-id-pred]
   (log/info (trs "Sending scheduled pulses..."))
   (let [pulse-id->channels (->> (pulse-channel/retrieve-scheduled-channels hour weekday monthday monthweek)
                                 (filter (comp pulse-id-pred :pulse_id))
                                 (group-by :pulse_id))]
     (doseq [[pulse-id channels] pulse-id->channels]
       (try
         (task-history/with-task-history {:task         "send-pulse"
                                          :task_details {:pulse-id pulse-id}}
           (log/debug (trs "Starting Pulse Execution: {0}" pulse-id))
           (when-let [pulse (pulse/retrieve-notification pulse-id :archived false)]
             (metabase.pulse/send-pulse! pulse :channel-ids (map :id channels)))
           (log/debug (trs "Finished Pulse Execution: {0}" pulse-id)))
         (catch Throwable e
           (on-error pulse-id e)))))))


;;; ------------------------------------------------------ Task ------------------------------------------------------

(defn- monthday [dt]
  (cond
    (timepr/first-day-of-month? dt) :first
    (timepr/last-day-of-month? dt)  :last
    (= 15 (time/day dt))            :mid
    :else                           :other))

(defn- monthweek [dt]
  (let [curr-day-of-month  (time/day dt)
        last-of-month      (time/day (time/last-day-of-the-month dt))
        start-of-last-week (- last-of-month 7)]
    (cond
      (> 8 curr-day-of-month)                  :first
      (< start-of-last-week curr-day-of-month) :last
      :else                                    :other)))

(defn- in-timezone
  "`dt` in the timezone with ID `tz-id`, or `dt` unchanged when `tz-id` is empty."
  [dt tz-id]
  (if (empty? tz-id)
    dt
    (time/to-time-zone dt (time/time-zone-for-id tz-id))))

(defn- send-scheduled-pulses!
  "Send the pulses scheduled to run at `now`. A pulse's schedule is in the timezone of its creator (see
  [[pulse-channel/pulse-id->timezone]]), so `now` is converted to each of those timezones in turn and only the pulses
  in that timezone are matched against it. Pulses without a creator timezone use the reporting timezone, or UTC."
  [now]
  (let [pulse-id->timezone (pulse-channel/pulse-id->timezone)]
    (doseq [tz-id (conj (set (vals pulse-id->timezone)) nil)]
      (let [dt (in-timezone now (or tz-id (driver/report-timezone)))]
        (send-pulses! (time/hour dt)
                      ;; joda time produces values of 1-7 here (Mon -> Sun) and we subtract 1 from it to
                      ;; make the values zero based to correspond to the indexes in pulse-channel/days-of-week
                      (->> (dec (time/day-of-week dt))
                           (get pulse-channel/days-of-week)
                           :id)
                      (monthday dt)
                      (monthweek dt)
                      log-pulse-exception
                      #(= tz-id (get pulse-id->timezone %)))))))

(jobs/defjob ^{:doc "Triggers the sending of all pulses which are scheduled to run in the current hour"} SendPulses [_]
  (try
    (task-history/with-task-history {:task "send-pulses"}
      (send-scheduled-pulses! (time/now)))
    (catch Throwable e
      (log/error e (trs "SendPulses task failed")))))

(def ^:private send-pulses-job-key     "metabase.task.send-pulses.job")
(def ^:private send-pulses-trigger-key "metabase.task.send-pulses.trigger")

(defmethod task/init! ::SendPulses [_]
  (let [job     (jobs/build
                 (jobs/of-type SendPulses)
                 (jobs/with-identity (jobs/key send-pulses-job-key)))
        trigger (triggers/build
                 (triggers/with-identity (triggers/key send-pulses-trigger-key))
                 (triggers/start-now)
                 (triggers/with-schedule
                   (cron/schedule
                    ;; run at the top of every hour
                    (cron/cron-schedule "0 0 * * * ? *")
                    ;; If send-pulses! misfires, don't try to re-send all the misfired Pulses. Retry only the most
                    ;; recent misfire, discarding all others. This should hopefully cover cases where a misfire
                    ;; happens while the system is still running; if the system goes down for an extended period of
                    ;; time we don't want to re-send tons of (possibly duplicate) Pulses.
                    ;;
                    ;; See https://www.nurkiewicz.com/2012/04/quartz-scheduler-misfire-instructions.html
                    (cron/with-misfire-handling-instruction-fire-and-proceed))))]
    (task/schedule-task! job trigger)))
