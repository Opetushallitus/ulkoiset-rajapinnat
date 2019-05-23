(ns ulkoiset-rajapinnat.valintapiste
  (:require [clojure.core.async :refer [go <!]]
            [full.async :refer [<? go-try]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.headers :refer [user-agent-from-request remote-addr-from-request]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [mime-application-json get-as-channel status body body-and-close exception-response parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]))

(defn fetch-valintapisteet [haku-oid hakukohde-oid request user channel log-to-access-log]
  (if (or (nil? haku-oid) (nil? hakukohde-oid))
    (go [])
    (go (try
          (let [jsession-id "-"
                person-oid (user :personOid)
                inet-address (remote-addr-from-request request)
                user-agent (user-agent-from-request request)
                url (resolve-url :valintapiste-service.internal.pisteet-for-hakukohde haku-oid hakukohde-oid jsession-id person-oid inet-address user-agent)
                start-time (System/currentTimeMillis)
                response (<? (get-as-channel url))
                status-code (response :status)]
            (-> channel
                (status status-code)
                (body-and-close (response :body)))
            (log-to-access-log status-code nil))
          (catch Exception e
            (do
              (log/error (format "Virhe hakiessa valintapisteitä " haku-oid " " hakukohde-oid) e)
              (-> channel
                  (status 500)
                  (body (to-json {:error (.getMessage e)}))
                  (close))))
          ))))


