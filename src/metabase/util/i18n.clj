(ns metabase.util.i18n
  (:require
   [puppetlabs.i18n.core :as i18n :refer [available-locales]]
   [schema.core :as s]
   [cheshire.generate :as json-gen]
   [cheshire.core :as json])
  (:import java.util.Locale))

(defn available-locales-with-names
  "Returns all locale abbreviations and their full names"
  []
  (map (fn [locale] [locale (.getDisplayName (Locale/forLanguageTag locale))]) (available-locales)))

(defn set-locale
  "This sets the local for the instance"
  [locale]
  (Locale/setDefault (Locale/forLanguageTag locale)))

(defrecord UserLocalizedString [ns-str msg args]
  java.lang.Object
  (toString [_]
    (apply i18n/translate ns-str (i18n/user-locale) msg args))
  schema.core.Schema
  (explain [this]
    (str this))
  json-gen/JSONable
  (to-json [this json-generator]
    (json-gen/to-json (str this) json-generator)))

(defrecord SystemLocalizedString [ns-str msg args]
  java.lang.Object
  (toString [_]
    (apply i18n/translate ns-str (i18n/system-locale) msg args))
  s/Schema
  (explain [this]
    (str this))
  json-gen/JSONable
  (to-json [this json-generator]
    (json-gen/to-json (str this) json-generator)))

(def LocalizedString
  (s/cond-pre UserLocalizedString SystemLocalizedString))

(defmacro tru [msg & args]
  `(UserLocalizedString. (namespace-munge *ns*) ~msg ~(vec args)))

(defmacro trs [msg & args]
  `(SystemLocalizedString. (namespace-munge *ns*) ~msg ~(vec args)))
