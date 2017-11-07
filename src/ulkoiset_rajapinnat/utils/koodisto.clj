(ns ulkoiset-rajapinnat.utils.koodisto
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise status body body-and-close exception-response response-to-json to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(def koodisto-api "%s/koodisto-service/rest/codeelement/codes/%s/1")

(defn strip-version-from-tarjonta-koodisto-uri [tarjonta-koodisto-uri]
  (if-let [uri tarjonta-koodisto-uri]
    (first (str/split uri #"#"))
    nil))


(defn transform-uri-to-arvo-format [koodisto]
  [(koodisto "koodiUri") (koodisto "koodiArvo")])

(defn fetch-koodisto [host-virkailija koodisto]
  (let [promise (get-as-promise (format koodisto-api host-virkailija koodisto))]
    (chain promise response-to-json #(map transform-uri-to-arvo-format %) #(into (sorted-map) %))))
