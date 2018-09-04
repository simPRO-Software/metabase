(ns metabase.util.i18n
  (:refer-clojure :exclude [ex-info])
  (:require [cheshire.generate :as json-gen]
            [clojure.walk :as walk]
            [puppetlabs.i18n.core :as i18n :refer [available-locales]]
            [schema.core :as s])
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

(def ^:private localized-string-checker
  "Compiled checker for `LocalizedString`s which is more efficient when used repeatedly like in `localized-string?`
  below"
  (s/checker LocalizedString))

(defn localized-string?
  "Returns `true` if `maybe-a-localized-string` is a system or user localized string instance"
  [maybe-a-localized-string]
  (not (localized-string-checker maybe-a-localized-string)))

(defn localized-strings->strings
  "Walks the datastructure `x` and converts any localized strings to regular string"
  [x]
  (walk/postwalk (fn [node]
                   (if (localized-string? node)
                     (str node)
                     node)) x))

(defn ex-info
  "Just like `clojure.core/ex-info` but it is i18n-aware. It will call `str` on `msg` and walk `map` converting any
  `SystemLocalizedString` and `UserLocalizedString`s to a regular string"
  ([msg map]
   (clojure.core/ex-info (str msg) (localized-strings->strings map)))
  ([msg map cause]
   (clojure.core/ex-info (str msg) (localized-strings->strings map) cause)))
