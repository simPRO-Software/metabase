(ns metabase.pulse.parameters-test
  (:require [clojure.test :refer :all]
            [metabase.pulse.parameters :as params]
            [metabase.pulse.test-util :refer [test-dashboard]]
            [metabase.test :as mt]))

(deftest value-string-test
  (testing "If a filter has multiple values, they are concatenated into a comma-separated string"
    (is (= "CA, NY and NJ"
           (params/value-string (-> test-dashboard :parameters first)))))

  (testing "If a filter has a single default value, it is formatted appropriately"
    (is (= "Q1, 2021"
           (params/value-string (-> test-dashboard :parameters second))))))

(deftest dashboard-url-test
  (mt/with-temporary-setting-values [site-url "https://metabase.com"]
    (testing "A valid dashboard URL can be generated with filters included"
      (is (= "https://metabase.com/dashboard/1?state=CA&state=NY&state=NJ&quarter_and_year=Q1-2021"
             (params/dashboard-url 1 (:parameters test-dashboard)))))

    (testing "If no filters are set, the base dashboard url is returned"
      (is (= "https://metabase.com/dashboard/1"
             (params/dashboard-url 1 {}))))

    (testing "Filters slugs and values are encoded properly for the URL"
      (is (= "https://metabase.com/dashboard/1?%26=contains%3F"
             (params/dashboard-url 1 [{:value "contains?", :slug "&"}]))))

    (testing "A parameter we cannot encode is skipped rather than throwing an NPE and killing the whole
             subscription (BI-118)"
      (testing "a parameter with no :slug"
        (is (= "https://metabase.com/dashboard/1"
               (params/dashboard-url 1 [{:id "abc", :value "CA"}]))))

      (testing "a parameter with a blank :slug"
        (is (= "https://metabase.com/dashboard/1"
               (params/dashboard-url 1 [{:slug "", :value "CA"}]))))

      (testing "a nil among a parameter's values -- the other values still make it into the URL"
        (is (= "https://metabase.com/dashboard/1?state=CA&state=NJ"
               (params/dashboard-url 1 [{:slug "state", :value ["CA" nil "NJ"]}]))))

      (testing "an unencodable parameter does not take its well-formed neighbours down with it"
        (is (= "https://metabase.com/dashboard/1?state=CA"
               (params/dashboard-url 1 [{:id "no-slug", :value "whatever"}
                                        {:slug "state", :value "CA"}])))))))
