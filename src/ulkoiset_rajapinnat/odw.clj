(ns ulkoiset-rajapinnat.odw
  (:require [ulkoiset-rajapinnat.rest :refer [parse-json-request status body-and-close body to-json get-as-promise parse-json-body exception-response]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket fetch-jsessionid]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clj-log4j2.core :as log]
            [manifold.deferred :refer [let-flow catch chain on-realized zip loop]]))

(def hakukohde-api "%s/valintaperusteet-service/resources/hakukohde/%s")
(def hakukohde-valinnanvaihe-api  "%s/valintaperusteet-service/resources/hakukohde/%s/valinnanvaihe")
(def valinnanvaihe-valintatapajono-api "%s/valintaperusteet-service/resources/valinnanvaihe/%s/valintatapajono")
(def valinnanvaihe-sijoittelu-api "%s/valintaperusteet-service/resources/valinnanvaihe/%s/kuuluuSijoitteluun")

(defn hakukohde-url [host hakukohde-oid] (format hakukohde-api host hakukohde-oid))
(defn valinnanvaihe-url [host hakukohde-oid] (format hakukohde-valinnanvaihe-api host hakukohde-oid))
(defn valinnanvaihe-sijoittelu-url [host valinnanvaihe-oid] (format valinnanvaihe-sijoittelu-api host valinnanvaihe-oid))
(defn valinnanvaihe-valintatapajono-url [host valinnanvaihe-oid] (format valinnanvaihe-valintatapajono-api host valinnanvaihe-oid))

(defn handle-response [url response]
  (log/info (str url " " (response :status)))
  (case (response :status)
    200 (parse-json-body response)
    404 nil
    (throw (RuntimeException. (str "Calling " url " failed: status=" (response :status) ", msg=" (response :body))))))

(defn fetch-with-url [session-id url]
  (let [promise (get-as-promise url {:headers {"Cookie" (str "JSESSIONID=" session-id )}})]
    (log/info (str url "(JSESSIONID=" session-id ")"))
    (chain promise #(handle-response url %))))

(defn get-valinnanvaiheet [host session-id hakukohde-oid] (fetch-with-url session-id (valinnanvaihe-url host hakukohde-oid)))
(defn get-valinnanvaihe-sijoittelu [host session-id valinnanvaihe-oid] (fetch-with-url session-id (valinnanvaihe-sijoittelu-url host valinnanvaihe-oid)))
(defn get-valinnanvaihe-valintatapajono [host session-id valinnanvaihe-oid] (fetch-with-url session-id (valinnanvaihe-valintatapajono-url host valinnanvaihe-oid)))

(defn koosta-valinnanvaiheet [host session-id hakukohde-oid]
  (let-flow [valinnanvaiheet (get-valinnanvaiheet host session-id hakukohde-oid)]
           (defn koosta-valinnanvaihe [valinnanvaihe]
             (let-flow [sijoittelu (get-valinnanvaihe-sijoittelu host session-id (get valinnanvaihe "oid"))
                        valintatapajono (get-valinnanvaihe-valintatapajono host session-id (get valinnanvaihe "oid"))]
                       (merge valinnanvaihe {:valintatapajonot valintatapajono} sijoittelu)))
           (apply zip (map #(koosta-valinnanvaihe %) valinnanvaiheet))))


(defn koosta-hakukohde [host session-id hakukohde-oid]
  (let-flow [hakukohde (fetch-with-url session-id (hakukohde-url host hakukohde-oid ))]
    (if (not (nil? hakukohde))
      (let-flow [valinnanvaiheet (koosta-valinnanvaiheet host session-id hakukohde-oid)]
         (merge hakukohde {:valinnanvaiheet valinnanvaiheet})))))

(defn fetch-hakukohteet [hakukohde-oids internal-host-virkailija session-id]
  (apply zip (map #(koosta-hakukohde internal-host-virkailija session-id %) hakukohde-oids)))

(defn odw-resource [config request]
  (with-channel request channel
                (on-close channel (fn [status] (log/debug "Channel closed!" status)))
                (let [host (config :host-virkailija)
                      username (config :ulkoiset-rajapinnat-cas-username)
                      password (config :ulkoiset-rajapinnat-cas-password)]
                  (-> (let-flow [session-id (fetch-jsessionid host "/valintaperusteet-service" username password)
                                 hakukohteet (fetch-hakukohteet (parse-json-request request) host session-id)]
                                (let [json (to-json (filter #(not (nil? %)) hakukohteet))]
                                  (-> channel
                                      (status 200)
                                      (body-and-close json))))
                      (catch Exception (exception-response channel))
                      )
                  (schedule-task (* 1000 60 60) (close channel)))))