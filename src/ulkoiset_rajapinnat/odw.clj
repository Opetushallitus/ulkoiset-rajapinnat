(ns ulkoiset-rajapinnat.odw
  (:require [ulkoiset-rajapinnat.rest :refer [parse-json-request status body-and-close to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clj-log4j2.core :as log]))

(defn hakukohde-json [oid]
  {:hakukohdeOid oid})

(defn odw-resource [config request]
  (with-channel request channel
    (on-close channel (fn [status] (log/debug "Channel closed!" status)))
    (status channel 200)
    (body-and-close channel (to-json (map hakukohde-json (parse-json-request request))))
    (schedule-task (* 1000 60 60) (close channel))))