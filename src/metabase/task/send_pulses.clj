(ns metabase.task.send-pulses
  "Tasks related to running `Pulses`."
  (:require [clj-time.core :as time]
            [clj-time.predicates :as timepr]
            [clojure.tools.logging :as log]
            [clojurewerkz.quartzite.jobs :as jobs]
            [clojurewerkz.quartzite.schedule.cron :as cron]
            [clojurewerkz.quartzite.triggers :as triggers]
            [medley.core :as m]
            [metabase.driver :as driver]
            [metabase.models.pulse :as pulse]
            [metabase.models.pulse-channel :as pulse-channel :refer [PulseChannel]]
            [metabase.models.task-history :as task-history]
            metabase.pulse
            [metabase.task :as task]
            [metabase.util.i18n :refer [trs]]
            [schema.core :as s]
            [toucan.db :as db])
  (:import org.joda.time.DateTimeZone))

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

(defn- send-channels!
  "Send the `channels` of each Pulse. The `on-error` function is called if an exception is thrown when sending the
  pulse."
  [channels on-error]
  (log/info (trs "Sending scheduled pulses..."))
  (doseq [[pulse-id channels] (group-by :pulse_id channels)]
    (try
      (task-history/with-task-history {:task         "send-pulse"
                                       :task_details {:pulse-id pulse-id}}
        (log/debug (trs "Starting Pulse Execution: {0}" pulse-id))
        (when-let [pulse (pulse/retrieve-notification pulse-id :archived false)]
          (metabase.pulse/send-pulse! pulse :channel-ids (map :id channels)))
        (log/debug (trs "Finished Pulse Execution: {0}" pulse-id)))
      (catch Throwable e
        (on-error pulse-id e)))))

(s/defn ^:private send-pulses!
  "Send any `Pulses` which are scheduled to run in the current day/hour. We use the current time and determine the
  hour of the day and day of the week according to the defined reporting timezone, or UTC. We then find all `Pulses`
  that are scheduled to run and send them. The `on-error` function is called if an exception is thrown when sending
  the pulse. Since this is a background process, the exception is only logged and not surfaced to the user. The
  `on-error` function makes it easier to test for when an error doesn't occur"
  ([hour weekday monthday monthweek]
   (send-pulses! hour weekday monthday monthweek log-pulse-exception))

  ([hour :- Hour, weekday :- Weekday, monthday :- MonthDay, monthweek :- MonthWeek, on-error]
   (send-channels! (pulse-channel/retrieve-scheduled-channels hour weekday monthday monthweek) on-error)))


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

(defn- schedule-time
  "The hour, weekday, monthday and monthweek of `dt`, as a schedule is matched against them."
  [dt]
  {:hour      (time/hour dt)
   ;; joda time produces values of 1-7 here (Mon -> Sun) and we subtract 1 from it to
   ;; make the values zero based to correspond to the indexes in pulse-channel/days-of-week
   :weekday   (->> (dec (time/day-of-week dt))
                   (get pulse-channel/days-of-week)
                   :id)
   :monthday  (monthday dt)
   :monthweek (monthweek dt)})

(defn- channel-schedule-time
  "The time `now` as the schedule of `channel` is matched against it:

  *  a schedule saved with its timezone (see [[pulse-channel/channel-timezone]]) is all in that timezone;
  *  an older schedule has the hour in the reporting timezone, or UTC, but the day and frame in the timezone of the
     Pulse creator, when it has one (see [[pulse-channel/pulse-id->timezone]]);
  *  any other schedule is all in the reporting timezone, or UTC."
  [now pulse-id->timezone channel]
  (let [report-time (schedule-time (in-timezone now (driver/report-timezone)))]
    (if-let [tz-id (pulse-channel/channel-timezone channel)]
      (schedule-time (in-timezone now tz-id))
      (if-let [creator-tz-id (get pulse-id->timezone (:pulse_id channel))]
        (assoc (schedule-time (in-timezone now creator-tz-id)) :hour (:hour report-time))
        report-time))))

(defn- send-scheduled-pulses!
  "Send the pulses scheduled to run at `now`. Schedules are in different timezones (see [[channel-schedule-time]]), so
  the channels scheduled at `now` in any timezone are fetched, and each channel is kept only when it is scheduled at
  `now` in its own timezone."
  [now]
  (let [pulse-id->timezone (try
                             (pulse-channel/pulse-id->timezone)
                             (catch Throwable e
                               (log/error e (trs "Error fetching Pulse creator timezones, using the reporting timezone"))
                               {}))
        report-hour        (time/hour (in-timezone now (driver/report-timezone)))
        schedule-times     (into #{(schedule-time (in-timezone now (driver/report-timezone)))}
                                 (concat
                                  (for [tz-id (DateTimeZone/getAvailableIDs)]
                                    (schedule-time (in-timezone now tz-id)))
                                  (for [tz-id (set (vals pulse-id->timezone))]
                                    (assoc (schedule-time (in-timezone now tz-id)) :hour report-hour))))]
    (let [time+channels (for [{:keys [hour weekday monthday monthweek], :as t} schedule-times
                              channel (pulse-channel/retrieve-scheduled-channels hour weekday monthday monthweek)]
                          [t channel])
          id->details   (when-let [ids (not-empty (set (map (comp :id second) time+channels)))]
                          (db/select-id->field :details PulseChannel :id [:in ids]))]
      (send-channels! (->> time+channels
                           (filter (fn [[t channel]]
                                     (= t (channel-schedule-time now pulse-id->timezone
                                                                 (assoc channel :details (id->details (:id channel)))))))
                           (map second)
                           (m/distinct-by :id))
                      log-pulse-exception))))

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
