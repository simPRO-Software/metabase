(ns metabase.query-processor.timezone-test
  (:require [clojure.test :refer :all]
            [metabase.driver :as driver]
            [metabase.models.setting :as setting]
            [metabase.query-processor.timezone :as qp.timezone]
            [metabase.test :as mt]))

(deftest database-local-report-timezone-test
  (testing "A Database-local `report-timezone` (the build's timezone) wins over the site-wide value (BI-91)"
    (mt/with-temporary-setting-values [report-timezone "Australia/Brisbane"]
      (testing "without a Database-local value the site-wide value is used"
        (is (= "Australia/Brisbane"
               (driver/report-timezone)
               (qp.timezone/report-timezone-id-if-supported :postgres))))
      (binding [setting/*database-local-values* {:report-timezone "America/New_York"}]
        (testing "the Database-local value is the report timezone"
          (is (= "America/New_York"
                 (driver/report-timezone))))
        (testing "the query session timezone is the Database-local value"
          (is (= "America/New_York"
                 (qp.timezone/report-timezone-id-if-supported :postgres))))
        (testing "results are reported in the Database-local timezone"
          (is (= "America/New_York"
                 (qp.timezone/results-timezone-id :postgres {:timezone "UTC"})))))
      (testing "an invalid Database-local value is not used for the session"
        (binding [setting/*database-local-values* {:report-timezone "Not/AZone"}]
          (is (= nil
                 (qp.timezone/report-timezone-id-if-supported :postgres)))))
      (testing "the site-wide value is unchanged"
        (is (= "Australia/Brisbane"
               (driver/report-timezone)))))))
