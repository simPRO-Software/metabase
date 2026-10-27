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

(deftest send-scheduled-pulses-in-creator-timezone-test
  (testing "Schedules are matched in the timezone of the pulse creator, or UTC when the creator has none"
    (mt/with-temp* [User         [{brisbane-id :id}       {:settings {:timezone "Australia/Brisbane"}}]
                    Pulse        [{brisbane-weekly :id}   {:creator_id brisbane-id}]
                    PulseChannel [_                       {:pulse_id      brisbane-weekly
                                                           :schedule_type :weekly
                                                           :schedule_hour 8
                                                           :schedule_day  "tue"}]
                    Pulse        [{brisbane-monthly :id}  {:creator_id brisbane-id}]
                    PulseChannel [_                       {:pulse_id       brisbane-monthly
                                                           :schedule_type  :monthly
                                                           :schedule_hour  8
                                                           :schedule_day   nil
                                                           :schedule_frame :first}]
                    Pulse        [{utc-weekly :id}        {:creator_id (mt/user->id :rasta)}]
                    PulseChannel [_                       {:pulse_id      utc-weekly
                                                           :schedule_type :weekly
                                                           :schedule_hour 8
                                                           :schedule_day  "tue"}]]
      (letfn [(sent-at [dt]
                (let [sent (atom #{})]
                  (with-redefs [metabase.pulse/send-pulse! (fn [{pulse-id :id} & _]
                                                             (swap! sent conj pulse-id))]
                    (#'send-pulses/send-scheduled-pulses! dt))
                  (set/intersection @sent #{brisbane-weekly brisbane-monthly utc-weekly})))]
        (testing "Mon 22:00 UTC is Tue 8 AM in Brisbane"
          (is (= #{brisbane-weekly}
                 (sent-at (time/date-time 2026 10 26 22)))))
        (testing "Tue 8 AM UTC"
          (is (= #{utc-weekly}
                 (sent-at (time/date-time 2026 10 27 8)))))
        (testing "Tue 22:00 UTC is Wed 8 AM in Brisbane"
          (is (= #{}
                 (sent-at (time/date-time 2026 10 27 22)))))
        (testing "Oct 31 22:00 UTC is Nov 1 8 AM in Brisbane"
          (is (= #{brisbane-monthly}
                 (sent-at (time/date-time 2026 10 31 22)))))))))
