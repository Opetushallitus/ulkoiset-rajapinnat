(ns ulkoiset-rajapinnat.utils.koodisto
  (:require [clojure.string :as str]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [full.async :refer [<? <??]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel parse-json-body-stream]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clojure.core.memoize :as memo]))

(defn strip-version-from-tarjonta-koodisto-uri [tarjonta-koodisto-uri]
  (if-let [uri tarjonta-koodisto-uri]
    (first (str/split uri #"#"))
    nil))

(defn strip-type-and-version-from-tarjonta-koodisto-uri
  [tarjonta-koodisto-uri]
  (if-let [uri tarjonta-koodisto-uri]
    (strip-version-from-tarjonta-koodisto-uri (subs uri (inc (str/index-of uri "_"))))
    nil))

(defn transform-uri-to-arvo-format [koodisto]
  [(koodisto "koodiUri") (koodisto "koodiArvo")])

(defn- log-fetch [resource-name start-time response]
  (log/debug "Fetching '{}' ready with status {}! Took {}ms!" resource-name (response :status) (- (System/currentTimeMillis) start-time))
  response)

(defn koodisto-as-channel [koodisto]
  (let [url (resolve-url :koodisto-service.codeelement-codes koodisto)
        options {:as :stream}
        response-mapper (comp #(into (sorted-map) %)
                              #(map transform-uri-to-arvo-format %)
                              parse-json-body-stream)]
    (get-as-channel url options response-mapper)))

(defn- koodisto-converted-country-code-as-channel [country-code]
  (let [start-time (System/currentTimeMillis)
        mapper (comp parse-json-body-stream (partial log-fetch "koodisto-maakoodi" start-time))]
    (get-as-channel (resolve-url :koodisto-service.rinnasteinen (str "maatjavaltiot2_" country-code)) {:as :stream} mapper)))

(defn fetch-maakoodi-from-koodisto [maakoodi]
  (try
    (get (first (<?? (koodisto-converted-country-code-as-channel maakoodi))) "koodiArvo")
    (catch Exception e
      (do
        (log/error e "Fetching country code from koodisto failed for code: " maakoodi)
        maakoodi))))

(defonce one-week (* 1000 60 60 24 7))

(def fetch-maakoodi-from-koodisto-cache
  (memo/ttl fetch-maakoodi-from-koodisto :ttl/threshold one-week))