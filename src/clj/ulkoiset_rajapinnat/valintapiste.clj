(ns ulkoiset-rajapinnat.valintapiste
  (:require [clojure.core.async :refer [go]]
            [full.async :refer [<?]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.headers :refer [user-agent-from-request remote-addr-from-request]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-service-ticket-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-channel post-as-channel status body body-and-close to-json parse-json-request post-json-with-cookies]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [schema.core :as s]))

(s/defschema Pistetieto
             {:tunniste              s/Str
              (s/optional-key :arvo) s/Any
              :osallistuminen        (s/enum "EI_OSALLISTUNUT" "OSALLISTUI" "EI_VAADITA" "MERKITSEMATTA")
              :tallettaja            s/Str})

(s/defschema PistetietoWrapper
             {:hakemusOID                 s/Str
              (s/optional-key :sukunimi)  s/Str
              (s/optional-key :etunimet)  s/Str
              :pisteet                    [Pistetieto]})

(defn fetch-valintapisteet-for-hakukohde [haku-oid hakukohde-oid request user channel log-to-access-log]
  (if (or (nil? haku-oid) (nil? hakukohde-oid))
    (go [])
    (go (try
          (let [ticket (<? (fetch-service-ticket-channel "/valintapiste-service/auth/cas" true))
                auth-response (<? (get-as-channel (resolve-url :valintapiste-service.cas-by-ticket ticket) {:follow-redirects false}))
                person-oid  (user :personOid)
                inet-addr   (remote-addr-from-request request)
                user-agent  (user-agent-from-request request)
                jsession-id "-"
                url         (resolve-url :valintapiste-service.pisteet-for-hakukohde haku-oid hakukohde-oid jsession-id person-oid inet-addr user-agent)
                response    (<? (get-as-channel url {:headers {"Cookie" (-> auth-response :headers :set-cookie)}}))
                status-code (response :status)]
            (-> channel
                (status status-code)
                (body-and-close (response :body)))
            (log-to-access-log status-code nil))
          (catch Exception e
            (do
              (log/errorf e "Virhe hakiessa valintapisteitä " haku-oid " " hakukohde-oid)
              (log-to-access-log 500 (.getMessage e))
              (-> channel
                  (status 500)
                  (body (to-json {:error (.getMessage e)}))
                  (close))))
          ))))

(defn fetch-valintapisteet-for-hakemus-oids [request user channel log-to-access-log]
  (let [hakemus-oids (vec (parse-json-request request))
        _            (log/infof "Haetaan hakemus-oidit hakemuksille %s" hakemus-oids)]
    (if (nil? hakemus-oids)
      (go [])
      (go (try
            (let [ticket (<? (fetch-service-ticket-channel "/valintapiste-service/auth/cas" true))
                  auth-response (<? (get-as-channel (resolve-url :valintapiste-service.cas-by-ticket ticket) {:follow-redirects false}))
                  person-oid  (user :personOid)
                  inet-addr   (remote-addr-from-request request)
                  user-agent  (user-agent-from-request request)
                  jsession-id "-"
                  url         (resolve-url :valintapiste-service.pisteet-with-hakemusoids jsession-id person-oid inet-addr user-agent)
                  json        (to-json hakemus-oids)
                  _           (log/infof "Post JSON body %s" json)
                  response    (<? (post-as-channel url json (post-json-with-cookies (-> auth-response :headers :set-cookie)) nil))
                  status-code (response :status)]
              (-> channel
                  (status status-code)
                  (body-and-close (response :body)))
              (log-to-access-log status-code nil))
            (catch Exception e
              (do
                (log/error e "Virhe hakiessa valintapisteitä hakemuksille ")
                (log-to-access-log 500 (.getMessage e))
                (-> channel
                    (status 500)
                    (body (to-json {:error (.getMessage e)}))
                    (close))))
            )))))
