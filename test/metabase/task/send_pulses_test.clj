(ns metabase.task.send-pulses-test
  (:require [clj-time.core :as time]
            [clojure.set :as set]
            [clojure.test :refer :all]
            [metabase.email :as email]
            [metabase.email-test :as et]
            [metabase.models.card :refer [Card]]
            [metabase.models.pulse :refer [Pulse]]
            [metabase.models.pulse-card :refer [PulseCard]]
            [metabase.models.pulse-channel :refer [PulseChannel]]
            [metabase.models.pulse-channel-recipient :refer [PulseChannelRecipient]]
            [metabase.models.user :refer [User]]
            metabase.pulse
            [metabase.pulse.test-util :refer [checkins-query-card]]
            [metabase.task.send-pulses :as send-pulses]
            [metabase.test :as mt]))

(deftest send-pulses-test
  (mt/with-temp* [Card                 [{card-id :id}  (assoc (checkins-query-card {:breakout [[:field (mt/id :checkins :date) {:temporal-unit :day}]]})
                                                              :name "My Question Name")]
                  Pulse                [{pulse-id :id} {:alert_condition  "rows"
                                                        :alert_first_only false}]
                  PulseCard             [_             {:pulse_id pulse-id
                                                        :card_id  card-id
                                                        :position 0}]
                  PulseChannel          [{pc-id :id}   {:pulse_id      pulse-id
                                                        :schedule_hour nil
                                                        :schedule_type "hourly"
                                                        :channel_type  :email}]
                  PulseChannelRecipient [_             {:user_id          (mt/user->id :rasta)
                                                        :pulse_channel_id pc-id}]]
    (et/with-fake-inbox
      (let [exceptions (atom [])
            on-error   (fn [_ exception]
                         (swap! exceptions conj exception))]
        (et/with-expected-messages 1
          (#'send-pulses/send-pulses! 0 "fri" :first :first on-error))
        (testing "emails"
          (is (= (et/email-to :rasta
                              {:subject "Alert: My Question Name has results",
                               :body    {"My Question Name" true}})
                 (et/regex-email-bodies #"My Question Name"))))
        (testing "exceptions"
          (is (= []
                 @exceptions)))))))

(deftest dont-send-archived-pulses-test
  (testing (str "Test that when we attempt to send a pulse that is archived, it just skips the pulse and sends "
                "nothing. Previously this failed schema validation (see metabase#8581)")
    (mt/with-temp* [Card                 [{card-id :id}    (assoc (checkins-query-card {:breakout [[:field (mt/id :checkins :date) {:temporal-unit :day}]]})
                                                                  :name "My Question Name")]
                    Pulse                [{pulse-id :id}   {:name "Test", :archived true}]
                    PulseCard             [_               {:pulse_id pulse-id
                                                            :card_id  card-id
                                                            :position 0}]
                    PulseChannel          [{pc-id :id}     {:pulse_id      pulse-id
                                                            :schedule_hour nil
                                                            :schedule_type "hourly"
                                                            :channel_type  :email}]
                    PulseChannelRecipient [_               {:user_id          (mt/user->id :rasta)
                                                            :pulse_channel_id pc-id}]]
      (et/with-fake-inbox
        (let [exceptions (atom [])
              on-error   (fn [_ exception]
                           (swap! exceptions conj exception))]
          (et/with-expected-messages 1
            ;; Send the pulse, though it's not going to send anything. Typically we'd block waiting for the message to
            ;; arrive, but there should be no message
            (#'send-pulses/send-pulses! 0 "fri" :first :first on-error)
            ;; This sends a test message. If we see our test message that means we didn't send a pulse message (which
            ;; is what we want)
            (email/send-message!
              :subject      "Test"
              :recipients   ["rasta@metabase.com"]
              :message-type :html
              :message      "Test Message"))
          (testing "emails"
            (is (= (et/email-to :rasta
                                {:subject "Test"
                                 :body    {"Test Message" true}})
                   (et/regex-email-bodies #"Test Message"))))
          (testing "There shouldn't be any failures, just skipping over the archived pulse"
            (is (= []
                   @exceptions))))))))

(deftest send-scheduled-pulses-in-schedule-timezone-test
  (testing "Schedules are matched in the timezone they were saved in"
    (mt/with-temp* [User         [{brisbane-id :id}  {:settings {:timezone "Australia/Brisbane"}}]
                    User         [{phoenix-id :id}   {:settings {:timezone "America/Phoenix"}}]
                    ;; Tue 8 AM Brisbane, saved with its timezone
                    Pulse        [{saved-weekly :id} {:creator_id (mt/user->id :rasta)}]
                    PulseChannel [_                  {:pulse_id      saved-weekly
                                                      :schedule_type :weekly
                                                      :schedule_hour 8
                                                      :schedule_day  "tue"
                                                      :details       {:schedule_timezone "Australia/Brisbane"}}]
                    ;; Tue 8 AM Brisbane, saved before with the hour in UTC
                    Pulse        [{old-weekly :id}   {:creator_id brisbane-id}]
                    PulseChannel [_                  {:pulse_id      old-weekly
                                                      :schedule_type :weekly
                                                      :schedule_hour 22
                                                      :schedule_day  "tue"}]
                    ;; first calendar day 8 AM Brisbane, saved before with the hour in UTC
                    Pulse        [{old-monthly :id}  {:creator_id brisbane-id}]
                    PulseChannel [_                  {:pulse_id       old-monthly
                                                      :schedule_type  :monthly
                                                      :schedule_hour  22
                                                      :schedule_day   nil
                                                      :schedule_frame :first}]
                    ;; Tue 8 PM Phoenix, saved before with the hour in UTC
                    Pulse        [{old-phoenix :id}  {:creator_id phoenix-id}]
                    PulseChannel [_                  {:pulse_id      old-phoenix
                                                      :schedule_type :weekly
                                                      :schedule_hour 3
                                                      :schedule_day  "tue"}]
                    ;; Tue 8 AM UTC, the creator has no timezone
                    Pulse        [{utc-weekly :id}   {:creator_id (mt/user->id :rasta)}]
                    PulseChannel [_                  {:pulse_id      utc-weekly
                                                      :schedule_type :weekly
                                                      :schedule_hour 8
                                                      :schedule_day  "tue"}]
                    ;; Tue 8 AM UTC, alert schedules were never saved in the creator's timezone
                    Pulse        [{alert-weekly :id} {:creator_id brisbane-id, :alert_condition "rows"}]
                    PulseChannel [_                  {:pulse_id      alert-weekly
                                                      :schedule_type :weekly
                                                      :schedule_hour 8
                                                      :schedule_day  "tue"}]]
      (letfn [(sent-at [dt]
                (let [sent (atom #{})]
                  (with-redefs [metabase.pulse/send-pulse! (fn [{pulse-id :id} & _]
                                                             (swap! sent conj pulse-id))]
                    (#'send-pulses/send-scheduled-pulses! dt))
                  (set/intersection @sent #{saved-weekly old-weekly old-monthly old-phoenix utc-weekly alert-weekly})))]
        (doseq [[description dt expected] [["Mon 22:00 UTC is Tue 8 AM in Brisbane"
                                            (time/date-time 2026 10 26 22) #{saved-weekly old-weekly}]
                                           ["Tue 3 AM UTC is Mon 8 PM in Phoenix"
                                            (time/date-time 2026 10 27 3) #{}]
                                           ["Tue 8 AM UTC"
                                            (time/date-time 2026 10 27 8) #{utc-weekly alert-weekly}]
                                           ["Tue 22:00 UTC is Wed 8 AM in Brisbane"
                                            (time/date-time 2026 10 27 22) #{}]
                                           ["Wed 3 AM UTC is Tue 8 PM in Phoenix"
                                            (time/date-time 2026 10 28 3) #{old-phoenix}]
                                           ["Oct 31 22:00 UTC is Nov 1 8 AM in Brisbane"
                                            (time/date-time 2026 10 31 22) #{old-monthly}]]]
          (testing description
            (is (= expected
                   (sent-at dt)))))))))
