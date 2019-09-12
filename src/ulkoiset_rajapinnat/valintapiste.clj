(ns ulkoiset-rajapinnat.valintapiste
  (:require [clojure.core.async :refer [go <!]]
            [full.async :refer [<? go-try]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.headers :refer [user-agent-from-request remote-addr-from-request]]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [mime-application-json get-as-channel post-as-channel status body body-and-close exception-response parse-json-body to-json parse-json-request post-json-options]]
            [ulkoiset-rajapinnat.utils.koodisto :refer [strip-version-from-tarjonta-koodisto-uri]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [schema.core :as s]))

(s/defschema Pistetieto
             {;:aikaleima s/Str
              :tunniste              s/Str
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
          (let [jsession-id "-"
                person-oid  (user :personOid)
                inet-addr   (remote-addr-from-request request)
                user-agent  (user-agent-from-request request)
                url         (resolve-url :valintapiste-service.internal.pisteet-for-hakukohde haku-oid hakukohde-oid jsession-id person-oid inet-addr user-agent)
                response    (<? (get-as-channel url))
                status-code (response :status)]
            (-> channel
                (status status-code)
                (body-and-close (response :body)))
            (log-to-access-log status-code nil))
          (catch Exception e
            (do
              (log/error (format "Virhe hakiessa valintapisteitä " haku-oid " " hakukohde-oid) e)
              (log-to-access-log 500 (.getMessage e))
              (-> channel
                  (status 500)
                  (body (to-json {:error (.getMessage e)}))
                  (close))))
          ))))

(defn fetch-valintapisteet-for-hakemus-oids [request user channel log-to-access-log]
  (let [hakemus-oids (vec (parse-json-request request))
        foo (log/info (str "Haetaan hakemus-oidit hakemuksille " hakemus-oids))]
    (if (nil? hakemus-oids)
      (go [])
      (go (try
            (let [jsession-id "-"
                  person-oid  (user :personOid)
                  inet-addr   (remote-addr-from-request request)
                  user-agent  (user-agent-from-request request)
                  url         (resolve-url :valintapiste-service.internal.pisteet-with-hakemusoids jsession-id person-oid inet-addr user-agent)
                  json        (to-json hakemus-oids)
                  foo         (log/info (str "Post JSON body" json))
                  response    (<? (post-as-channel url json (post-json-options jsession-id) nil))
                  status-code (response :status)]
              (-> channel
                  (status status-code)
                  (body-and-close (response :body)))
              (log-to-access-log status-code nil))
            (catch Exception e
              (do
                (log/error "Virhe hakiessa valintapisteitä hakemuksille " e)
                (log-to-access-log 500 (.getMessage e))
                (-> channel
                    (status 500)
                    (body (to-json {:error (.getMessage e)}))
                    (close))))
            )))))
