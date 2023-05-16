(ns ulkoiset-rajapinnat.core
  (:require [compojure.handler :only [site]]
            [org.httpkit.timer :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.api.exception :as ex]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url url-properties]]
            [ring.util.http-response :refer [ok not-found]]
            [schema.core :as s]
            [compojure.api.middleware :refer [no-response-coercion]]
            [ulkoiset-rajapinnat.utils.audit :refer [audit create-audit-logger]]
            [ulkoiset-rajapinnat.utils.access :refer [access-log
                                                      access-log-with-ticket-check-with-channel
                                                      access-log-with-ticket-check-as-channel
                                                      handle-invalid-request]]
            [ulkoiset-rajapinnat.utils.runtime :refer [shutdown-hook]]
            [ulkoiset-rajapinnat.haku :refer [Haku haku-resource]]
            [ulkoiset-rajapinnat.hakukohde :refer [Hakukohde hakukohde-resource]]
            [ulkoiset-rajapinnat.hakemus :refer [Hakemus hakemus-resource]]
            [ulkoiset-rajapinnat.vastaanotto :refer [Vastaanotto vastaanotto-resource]]
            [ulkoiset-rajapinnat.valintaperusteet :refer [Valintaperusteet valintaperusteet-resource]]
            [ulkoiset-rajapinnat.valintapiste :refer [PistetietoWrapper fetch-valintapisteet-for-hakukohde fetch-valintapisteet-for-hakemus-oids]]
            [ulkoiset-rajapinnat.utils.config :refer [config init-config]]
            [org.httpkit.server :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :refer :all]
            [clojure.tools.reader.edn :as edn])
  (:gen-class)
  (:import [ulkoiset_rajapinnat UlkoisetRajapinnatApi]))

(defn api-opintopolku-routes [audit-logger rajapinnat-api]
  (api
    {:coercion no-response-coercion
     :swagger
     {:ui   (-> @config :server :base-url)
      :spec (str (-> @config :server :base-url) "/swagger.json")
      :data {:info {:title       "Ulkoiset-rajapinnat"
                    :description "Ulkoiset-rajapinnat"}}}
     :exceptions {:handlers {::ex/request-validation handle-invalid-request}}}
    (context (str (-> @config :server :base-url) "/api") []
      :tags ["api"]
      (GET "/healthcheck" []
        :summary "Health check API"
        (access-log (ok "OK")))
      (GET "/haku-for-year/:koulutuksen_alkamisvuosi" [koulutuksen_alkamisvuosi ticket]
        :summary "Haut koulutuksen alkamisvuodella"
        :query-params [ticket :- String]
        :responses {200 {:schema [Haku]}}
        (let [year (Integer/parseInt koulutuksen_alkamisvuosi)]
          (log/info (str "Got incoming request to /haku-for-year/" koulutuksen_alkamisvuosi))
          (comment
            (access-log-with-ticket-check-with-channel
              ticket
              (partial audit audit-logger (str "Haut koulutuksen alkamisvuodella " koulutuksen_alkamisvuosi))
              (partial haku-resource koulutuksen_alkamisvuosi)))
          (access-log-with-ticket-check-as-channel
            ticket
            (partial audit audit-logger (str "Haut koulutuksen alkamisvuodella " koulutuksen_alkamisvuosi))
            (fn [_]
              (.findHakuByYear rajapinnat-api year)))))
      (GET "/hakukohde-for-haku/:haku-oid" [haku-oid palauta-null-arvot ticket]
        :summary "Hakukohteet haku OID:lla"
        :query-params [ticket :- String]
        :responses {200 {:schema [Hakukohde]}}
        (log/info (str "Got incoming request to /hakukohde-for-haku/" haku-oid))
        (comment
          (access-log-with-ticket-check-with-channel
            ticket
            (partial audit audit-logger (str "Hakukohteet haku OID:lla" haku-oid))
            (partial hakukohde-resource haku-oid palauta-null-arvot)))
        (access-log-with-ticket-check-as-channel
          ticket
          (partial audit audit-logger (str "Hakukohteet haku OID:lla " haku-oid))
          (fn [_]
            (.findHakukohteetForHaku rajapinnat-api haku-oid))))
      (GET "/vastaanotto-for-haku/:haku-oid" [haku-oid koulutuksen_alkamisvuosi koulutuksen_alkamiskausi ticket] ; hakuoid + kaudet
        :summary "Vastaanotot haku OID:lla"
        :query-params [ticket :- String
                       koulutuksen_alkamisvuosi :- String
                       koulutuksen_alkamiskausi :- String]
        :responses {200 {:schema [Vastaanotto]}}
        (log/info (str "Got incoming request to /vastaanotto-for-haku/" haku-oid))
        (access-log-with-ticket-check-as-channel
          ticket
          (partial audit audit-logger (str "Vastaanotot haku OID:lla" haku-oid))
          (if (= 35 (count haku-oid))
            (fn [_]
              (.findVastaanototForHaku rajapinnat-api haku-oid koulutuksen_alkamisvuosi koulutuksen_alkamiskausi))
            (partial vastaanotto-resource haku-oid koulutuksen_alkamisvuosi koulutuksen_alkamiskausi))))
      (POST "/vastaanotto-for-haku/:haku-oid" [haku-oid ticket]
        :summary "Vastaanotot haku OID:lla tietyille hakukohteille"
        :query-params [ticket :- String]
        :body [body (describe [s/Str] "hakukohteiden oidit JSON-taulukossa")]
        :responses {200 {:schema [Vastaanotto]}}
        (log/info (str "Got incoming request to /vastaanotto-for-haku/" haku-oid))
        (access-log-with-ticket-check-as-channel
          ticket
          (partial audit audit-logger (str "Vastaanotot haku OID:lla ja hakukohdeoideilla" haku-oid))
          (if (= 35 (count haku-oid))
            (fn [_]
              (.findVastaanototForHakukohteet rajapinnat-api haku-oid body))
            (partial vastaanotto-resource haku-oid))))
      ;fixme, this needs kouta-integraatio still
      (GET "/hakemus-for-haku/:haku-oid" [haku-oid koulutuksen_alkamisvuosi koulutuksen_alkamiskausi palauta-null-arvot ticket] ; hakuoid + kaudet
        :summary "Hakemukset haku OID:lla"
        :query-params [ticket :- String
                       koulutuksen_alkamisvuosi :- String
                       koulutuksen_alkamiskausi :- String]
        :responses {200 {:schema [Hakemus]}}
        (log/info (str "Got incoming request to /hakemus-for-haku/" haku-oid "?koulutuksen_alkamisvuosi=" koulutuksen_alkamisvuosi "&koulutuksen_alkamiskausi=" koulutuksen_alkamiskausi))
        (access-log-with-ticket-check-with-channel
          ticket
          (partial audit audit-logger (str "Vastaanotot haku OID:lla" haku-oid))
          (partial hakemus-resource haku-oid koulutuksen_alkamisvuosi koulutuksen_alkamiskausi palauta-null-arvot)))
      (GET "/valintaperusteet/hakukohde/:hakukohde-oid" [hakukohde-oid ticket]
        :summary "Hakukohde valintaperusteista"
        :query-params [ticket :- String]
        :responses {200 {:schema [Valintaperusteet]}}
        (log/info (str "Got incoming request to /valintaperusteet/hakukohde/" hakukohde-oid))
        (access-log-with-ticket-check-with-channel
          ticket
          (partial audit audit-logger (str "Vastaanotot hakukohde OID:lla" hakukohde-oid))
          (partial valintaperusteet-resource hakukohde-oid)))
      (POST "/valintaperusteet/hakukohde" [ticket]
        :summary "Hakukohteet valintaperusteista"
        :query-params [ticket :- String]
        :body [body (describe [s/Str] "hakukohteiden oidit JSON-taulukossa")]
        :responses {200 {:schema [Valintaperusteet]}}
        (log/info (str "Got incoming request to /valintaperusteet/hakukohde"))
        (access-log-with-ticket-check-with-channel
          ticket
          (partial audit audit-logger (str "Hakukohteet valintaperusteista"))
          (partial valintaperusteet-resource)))
      (GET "/valintapiste/haku/:haku-oid/hakukohde/:hakukohde-oid" [haku-oid hakukohde-oid ticket]
        :summary "Hakukohteen valintapisteet"
        :query-params [ticket :- String]
        :responses {200 {:schema [PistetietoWrapper]}}
        (log/info (str "Got incoming request to /valintapiste/haku/" haku-oid "/hakukohde-oid/" hakukohde-oid))
        (access-log-with-ticket-check-with-channel
          ticket
          (partial audit audit-logger (str "Valintapisteet haulle " haku-oid "hakukohteelle" hakukohde-oid))
          (partial fetch-valintapisteet-for-hakukohde haku-oid hakukohde-oid)))
      (POST "/valintapiste/pisteet-with-hakemusoids" [hakemus-oids ticket]
        :summary "Hakemusten pistetiedot. Hakemusten maksimimäärä on 32767 kpl."
        :query-params [ticket :- String]
        :body [body (describe [s/Str] "hakemusten oidit JSON-taulukossa")]
        :responses {200 {:schema [PistetietoWrapper]}}
        (log/info (str "Got incoming request to /valintapiste/pisteet-with-hakemusoids" body))
        (access-log-with-ticket-check-with-channel
          ticket
          (partial audit audit-logger (str "Valintapisteet hakemuksille"))
          (partial fetch-valintapisteet-for-hakemus-oids))))
    (context (str (-> @config :server :base-url) "") []
      (GET "/buildversion.txt" []
        :summary "Build fingerprint"
        (let [build-props (edn/read-string (slurp (clojure.java.io/resource "buildversion.edn")))]
          (access-log (ok build-props)))))
    (ANY "/*" []
      :summary "Not found page"
      (access-log (not-found "Page not found")))))

(defn- initialise-uncaught-exception-handler []
  (Thread/setDefaultUncaughtExceptionHandler
    (reify Thread$UncaughtExceptionHandler
      (uncaughtException [_ thread ex]
        (log/error ex "Uncaught exception on" (.getName thread))))))

(defn start-server [args]
  (initialise-uncaught-exception-handler)
  (init-config args)
  (let [port (-> @config :server :port)]
    (log/info "Starting server in port" port "host" (resolve-url :host-virkailija))
    (let [audit-logger (create-audit-logger)
          rajapinnat-api (UlkoisetRajapinnatApi. @url-properties @config)
          server (run-server (api-opintopolku-routes audit-logger rajapinnat-api) {:port port})
          close-handle (fn [] (-> (meta server)
                                  :server
                                  (.stop 100)))]
      (do
        (shutdown-hook #(close-handle))
        close-handle))))

(defn -main [& args]
  (start-server args))

