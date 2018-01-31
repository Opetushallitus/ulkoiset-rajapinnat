(ns ulkoiset-rajapinnat.utils.koodisto
  (:require [clojure.string :as str]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel parse-json-body-stream]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

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

(defn koodisto-as-channel [koodisto]
  (let [url (resolve-url :koodisto-service.codeelement-codes koodisto)
        options {:as :stream}
        response-mapper (comp #(into (sorted-map) %)
                              #(map transform-uri-to-arvo-format %)
                              parse-json-body-stream)]
    (get-as-channel url options response-mapper)))
