(ns ulkoiset-rajapinnat.odw
  (:require [ulkoiset-rajapinnat.rest :refer [parse-json-request]]
            [clj-log4j2.core :as log]))

(defn hakukohde-json [oid]
  {:hakukohdeOid oid})

(defn odw-resource [config request]
  (map hakukohde-json (parse-json-request request)))