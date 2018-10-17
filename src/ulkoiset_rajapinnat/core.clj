(ns ulkoiset-rajapinnat.core
  (:require [compojure.handler :only [site]]
        [org.httpkit.timer :refer :all]
        [compojure.api.sweet :refer :all]
        [compojure.api.exception :as ex]
        [ring.util.http-response :refer [ok not-found]]
        [schema.core :as s]
        [compojure.api.middleware :refer [no-response-coercion]]
        [ulkoiset-rajapinnat.utils.audit :refer [audit create-audit-logger]]
        [ulkoiset-rajapinnat.utils.access :refer [access-log access-log-with-ticket-check-with-channel handle-invalid-request]]
        [ulkoiset-rajapinnat.utils.runtime :refer [shutdown-hook]]
        [ulkoiset-rajapinnat.haku :refer [Haku haku-resource]]
        [ulkoiset-rajapinnat.hakukohde :refer [Hakukohde hakukohde-resource]]
        [ulkoiset-rajapinnat.hakemus :refer [Hakemus hakemus-resource]]
        [ulkoiset-rajapinnat.vastaanotto :refer [Vastaanotto vastaanotto-resource]]
        [ulkoiset-rajapinnat.valintaperusteet :refer [Valintaperusteet valintaperusteet-resource]]
        [ulkoiset-rajapinnat.utils.config :refer [config init-config]]
        [org.httpkit.server :refer :all]
        [clojure.tools.logging :as log])
  (:gen-class))

(defn api-opintopolku-routes [audit-logger]
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
      (GET "/haku-for-year/:vuosi" [vuosi ticket]
        :summary "Haut vuodella"
        :query-params [ticket :- String]
        :responses {200 {:schema [Haku]}}
        (log/info (str "Got incoming request to /haku-for-year/" vuosi))
        (access-log-with-ticket-check-with-channel
          ticket
          (partial audit audit-logger (str "Haut vuodella " vuosi))
          (partial haku-resource vuosi)))
      (GET "/hakukohde-for-haku/:haku-oid" [haku-oid palauta-null-arvot ticket]
        :summary "Hakukohteet haku OID:lla"
        :query-params [ticket :- String]
        :responses {200 {:schema [Hakukohde]}}
        (log/info (str "Got incoming request to /hakukohde-for-haku/" haku-oid))
        (access-log-with-ticket-check-with-channel
          ticket
          (partial audit audit-logger (str "Hakukohteet haku OID:lla" haku-oid))
          (partial hakukohde-resource haku-oid palauta-null-arvot)))
      (GET "/vastaanotto-for-haku/:haku-oid" [haku-oid koulutuksen_alkamisvuosi koulutuksen_alkamiskausi ticket] ; hakuoid + kaudet
        :summary "Vastaanotot haku OID:lla"
        :query-params [ticket :- String
                       koulutuksen_alkamisvuosi :- String
                       koulutuksen_alkamiskausi :- String]
        :responses {200 {:schema [Vastaanotto]}}
        (log/info (str "Got incoming request to /vastaanotto-for-haku/" haku-oid))
        (access-log-with-ticket-check-with-channel
           ticket
          (partial audit audit-logger (str "Vastaanotot haku OID:lla" haku-oid))
          (partial vastaanotto-resource haku-oid koulutuksen_alkamisvuosi koulutuksen_alkamiskausi)))
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
          (partial valintaperusteet-resource  hakukohde-oid)))
      (POST "/valintaperusteet/hakukohde" [ticket]
        :summary "Hakukohteet valintaperusteista"
        :query-params [ticket :- String]
        :body [body (describe [s/Str] "hakukohteiden oidit JSON-taulukossa")]
        :responses {200 {:schema [Valintaperusteet]}}
        (log/info (str "Got incoming request to /valintaperusteet/hakukohde"))
        (access-log-with-ticket-check-with-channel
           ticket
          (partial audit audit-logger (str "Hakukohteet valintaperusteista"))
          (partial valintaperusteet-resource))))
    (ANY "/*" []
      :summary "Not found page"
      (access-log (not-found "Page not found")))))

(defn start-server [args]
  (init-config args)
  (let [port (-> @config :server :port)]

    (log/info "Starting server in port {}" port)
    (let [audit-logger (create-audit-logger)
          server (run-server (api-opintopolku-routes audit-logger) {:port port})
          close-handle (fn [] (-> (meta server)
                                    :server
                                    (.stop 100)))]
      (do
        (shutdown-hook #(close-handle))
        close-handle))))

(defn -main [& args]
  (start-server args))

