(ns ulkoiset-rajapinnat.odw
  (:require [ulkoiset-rajapinnat.rest :refer [parse-json-request status body-and-close body to-json get-as-promise parse-json-body exception-response]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket fetch-jsessionid]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clj-log4j2.core :as log]
            [manifold.deferred :refer [let-flow catch chain on-realized zip]]))

(def hakukohde-api "%s/valintaperusteet-service/resources/hakukohde/%s")

(defn parse-response-skip-not-found [response]
  (if (== 200 (response :status))
    (parse-json-body response)
    nil))

(defn fetch-hakukohde [internal-host-virkailija session-id hakukohde-oid]
  (let [promise (get-as-promise (format hakukohde-api internal-host-virkailija hakukohde-oid)
                                {:headers {"Cookie" (str "JSESSIONID=" session-id)}})]
    (log/info (str (format hakukohde-api internal-host-virkailija hakukohde-oid) "(JSESSIONID=" session-id ")"))
    (chain promise parse-response-skip-not-found)))

(defn fetch-hakukohteet [hakukohde-oids internal-host-virkailija session-id]
  (apply zip (map #(fetch-hakukohde internal-host-virkailija session-id %) hakukohde-oids)))

(defn odw-resource [config request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (let [host (config :host-virkailija)
                      username (config :ulkoiset-rajapinnat-cas-username)
                      password (config :ulkoiset-rajapinnat-cas-password)]
                  (-> (let-flow [session-id (fetch-jsessionid host "/valintaperusteet-service" username password)
                                 hakukohteet (fetch-hakukohteet (parse-json-request request) host session-id)]
                                (let [json (to-json (filter #(not= nil %) hakukohteet))]
                                  (-> channel
                                      (status 200)
                                      (body-and-close json))))
                      (catch Exception (exception-response channel))
                      )
                  (schedule-task (* 1000 60 60) (close channel)))))