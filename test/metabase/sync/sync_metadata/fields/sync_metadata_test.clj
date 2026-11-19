(ns metabase.sync.sync-metadata.fields.sync-metadata-test
  (:require [clojure.test :refer :all]
            [metabase.models.table :refer [Table]]
            [metabase.sync.sync-metadata.fields.sync-metadata :as sync-metadata]
            [toucan.db :as db]
            [toucan.util.test :as tt]))

(defn- updates-that-will-be-performed [new-metadata-from-sync metadata-in-application-db]
  (tt/with-temp Table [table]
    (let [update-operations (atom [])]
      (with-redefs [db/update! (fn [model id updates]
                                 (swap! update-operations conj [(name model) id updates]))]
        (#'sync-metadata/update-field-metadata-if-needed!
         table
         new-metadata-from-sync
         metadata-in-application-db)
        @update-operations))))

(deftest database-type-changed-test
  (testing "test that if database-type changes we will update it in the DB"
    (is (= [["Field" 1 {:database_type "Integer"}]]
           (updates-that-will-be-performed
            {:name              "My Field"
             :database-type     "Integer"
             :base-type         :type/Integer
             :database-position 0
             :database-required false}
            {:name              "My Field"
             :database-type     "NULL"
             :base-type         :type/Integer
             :id                1
             :database-position 0
             :database-required false})))))

(deftest database-required-changed-test
  (testing "test that if database-required changes we will update it in the DB"
    (is (= [["Field" 1 {:database_required false}]]
           (updates-that-will-be-performed
            {:name              "My Field"
             :database-type     "Integer"
             :base-type         :type/Integer
             :database-position 0
             :database-required false}
            {:name              "My Field"
             :database-type     "Integer"
             :base-type         :type/Integer
             :id                1
             :database-position 0
             :database-required true})))))

(deftest no-op-test
  (testing "no changes should be made (i.e., no calls to `update!`) if nothing changes"
    (is (= []
           (updates-that-will-be-performed
            {:name              "My Field"
             :database-type     "Integer"
             :base-type         :type/Integer
             :database-position 0
             :database-required false}
            {:name              "My Field"
             :database-type     "Integer"
             :base-type         :type/Integer
             :id                1
             :database-position 0
             :database-required false})))))

(deftest nil-database-type-test
  (testing (str "test that if `database-type` comes back as `nil` in the metadata from the sync process, we won't try "
                "to set a `nil` value in the DB -- this is against the rules -- we should set `NULL` instead. See "
                "`TableMetadataField` schema.")
    (is (= [["Field" 1 {:database_type "NULL"}]]
           (updates-that-will-be-performed
            {:name              "My Field"
             :database-type     nil
             :base-type         :type/Integer
             :database-position 0
             :database-required false}
            {:name              "My Field"
             :database-type     "Integer"
             :base-type         :type/Integer
             :database-position 0
             :id                1
             :database-required false}))))

  (testing (str "if `database-type` comes back as `nil` and was already saved in application DB as `NULL` no changes "
                "should be made")
    (is (= []
           (updates-that-will-be-performed
            {:name              "My Field"
             :database-type     nil
             :base-type         :type/Integer
             :database-position 0
             :database-required false}
            {:name              "My Field"
             :database-type     "NULL"
             :base-type         :type/Integer
             :id                1
             :database-position 0
             :database-required false})))))

(deftest base-type-changed-test
  (testing (str "BI-52 (upstream metabase#37110): when the base type changes, the effective type follows it, the "
                "coercion is cleared, and the field is re-fingerprinted and re-classified")
    (is (= [["Field" 1 {:database_type       "float8"
                        :base_type           :type/Float
                        :effective_type      :type/Float
                        :coercion_strategy   nil
                        :fingerprint_version 0
                        :fingerprint         nil
                        :semantic_type       nil}]]
           (updates-that-will-be-performed
            {:name              "freight"
             :database-type     "float8"
             :base-type         :type/Float
             :database-position 10
             :database-required false}
            {:name              "freight"
             :database-type     "text"
             :base-type         :type/Text
             :effective-type    :type/Text
             :semantic-type     :type/Category
             :id                1
             :database-position 10
             :database-required false})))))

(deftest effective-type-drift-heal-test
  (testing (str "BI-52 (upstream metabase#72591): a field whose base type changed in an earlier sync but whose "
                "effective type was left behind, with no coercion, gets the base type as its effective type")
    (is (= [["Field" 1 {:effective_type :type/Float}]]
           (updates-that-will-be-performed
            {:name              "freight"
             :database-type     "float8"
             :base-type         :type/Float
             :database-position 10
             :database-required false}
            {:name              "freight"
             :database-type     "float8"
             :base-type         :type/Float
             :effective-type    :type/Text
             :semantic-type     :type/Category
             :id                1
             :database-position 10
             :database-required false}))))
  (testing "a field with a coercion strategy keeps its effective type"
    (is (= []
           (updates-that-will-be-performed
            {:name              "created"
             :database-type     "text"
             :base-type         :type/Text
             :database-position 0
             :database-required false}
            {:name              "created"
             :database-type     "text"
             :base-type         :type/Text
             :effective-type    :type/DateTime
             :coercion-strategy :Coercion/ISO8601->DateTime
             :id                1
             :database-position 0
             :database-required false}))))
  (testing "a field whose effective type is its base type is not changed"
    (is (= []
           (updates-that-will-be-performed
            {:name              "total"
             :database-type     "float8"
             :base-type         :type/Float
             :database-position 0
             :database-required false}
            {:name              "total"
             :database-type     "float8"
             :base-type         :type/Float
             :effective-type    :type/Float
             :id                1
             :database-position 0
             :database-required false})))))

(deftest dont-overwrite-semantic-type-test
  (testing "We should not override non-nil `semantic_type`s"
    (is (= []
           (updates-that-will-be-performed
            {:name              "My Field"
             :database-type     "Integer"
             :base-type         :type/Integer
             :semantic-type     nil
             :database-position 0
             :database-required false}
            {:name              "My Field"
             :database-type     "Integer"
             :base-type         :type/Integer
             :semantic-type     :type/Price
             :id                1
             :database-position 0
             :database-required false})))))
