(ns ulkoiset-rajapinnat.utils.koodisto
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel get-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def koodisto-api "%s/koodisto-service/rest/codeelement/codes/%s/1")

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

(defn fetch-koodisto [host-virkailija koodisto]
  (let [promise (get-as-promise (format koodisto-api host-virkailija koodisto))]
    (chain promise parse-json-body #(map transform-uri-to-arvo-format %) #(into (sorted-map) %))))

(defn koodisto-as-channel [config koodisto]
  (let [host-virkailija (config :host-virkailija)
        url (format koodisto-api host-virkailija koodisto)
        options {}
        response-mapper (comp #(into (sorted-map) %)
                              #(map transform-uri-to-arvo-format %)
                              parse-json-body)]
    (get-as-channel url options response-mapper)))
