(ns ulkoiset-rajapinnat.core
  (:require [compojure.handler :only [site]]
        [org.httpkit.timer :refer :all]
        [compojure.api.sweet :refer :all]
        [ring.util.http-response :refer :all]
        [schema.core :as s]
        [compojure.api.middleware :refer [no-response-coercion]]
        [ulkoiset-rajapinnat.utils.access :refer [access-log access-log-with-ticket-check-with-channel]]
        [ulkoiset-rajapinnat.utils.runtime :refer [shutdown-hook]]
        [ulkoiset-rajapinnat.haku :refer [Haku haku-resource]]
        [ulkoiset-rajapinnat.hakukohde :refer [Hakukohde hakukohde-resource]]
        [ulkoiset-rajapinnat.hakemus :refer [Hakemus hakemus-resource]]
        [ulkoiset-rajapinnat.vastaanotto :refer [Vastaanotto vastaanotto-resource]]
        [ulkoiset-rajapinnat.valintaperusteet :refer [Valintaperusteet valintaperusteet-resource]]
        [ulkoiset-rajapinnat.utils.config :refer :all]
        [org.httpkit.server :refer :all]
            [clojure.tools.logging :as log])
  (:gen-class))

(defn api-opintopolku-routes [config]
  (api
    {:coercion no-response-coercion
     :swagger
     {:ui   (-> config :server :base-url)
      :spec (str (-> config :server :base-url) "/swagger.json")
      :data {:info {:title       "Ulkoiset-rajapinnat"
                    :description "Ulkoiset-rajapinnat"}
             }}}
    (context (str (-> config :server :base-url) "/api") []
      :tags ["api"]
      (GET "/healthcheck" []
        :summary "Health check API"
        (access-log (ok "OK")))
      (GET "/haku-for-year/:vuosi" [vuosi ticket]
        :summary "Haut vuodella"
        :query-params [ticket :- String]
        :responses {200 {:schema [Haku]}}
        (access-log-with-ticket-check-with-channel
          config ticket
          (partial haku-resource config vuosi)))
      (GET "/hakukohde-for-haku/:haku-oid" [haku-oid kausi palauta-null-arvot ticket]
        :summary "Hakukohteet haku OID:lla"
        :query-params [ticket :- String]
        :responses {200 {:schema [Hakukohde]}}
        (access-log-with-ticket-check-with-channel
          config ticket
          (partial hakukohde-resource config haku-oid palauta-null-arvot)))
      (GET "/vastaanotto-for-haku/:haku-oid" [haku-oid kausi ticket] ; hakuoid + kaudet
        :summary "Vastaanotot haku OID:lla"
        :query-params [ticket :- String]
        :responses {200 {:schema [Vastaanotto]}}
        (access-log-with-ticket-check-with-channel
          config ticket
          (partial vastaanotto-resource config haku-oid)))
      (GET "/hakemus-for-haku/:haku-oid" [haku-oid vuosi kausi palauta-null-arvot ticket] ; hakuoid + kaudet
        :summary "Hakemukset haku OID:lla"
        :query-params [ticket :- String
                       vuosi :- String
                       kausi :- String]
        :responses {200 {:schema [Hakemus]}}
        (access-log-with-ticket-check-with-channel
          config ticket
          (partial hakemus-resource config haku-oid vuosi kausi palauta-null-arvot)))
      (GET "/valintaperusteet/hakukohde/:hakukohde-oid" [hakukohde-oid ticket]
        :summary "Hakukohde valintaperusteista"
        :query-params [ticket :- String]
        :responses {200 {:schema [Valintaperusteet]}}
        (access-log-with-ticket-check-with-channel
          config ticket
          (partial valintaperusteet-resource config hakukohde-oid)))
      (POST "/valintaperusteet/hakukohde" [ticket]
        :summary "Hakukohteet valintaperusteista"
        :query-params [ticket :- String]
        :responses {200 {:schema [Valintaperusteet]}}
        (access-log-with-ticket-check-with-channel
          config ticket
          (partial valintaperusteet-resource config))))
    (ANY "/*" []
      :summary "Not found page"
      (access-log (not-found "Page not found")))))

(defn start-server [args]
  (let [config (read-configuration-file-first-from-varargs-then-from-env-vars args)
        port (-> config :server :port)]

    (log/info "Starting server in port {}" port)
    (let [server (run-server (api-opintopolku-routes config) {:port port})
          close-handle (fn [] (-> (meta server)
                                    :server
                                    (.stop 100)))]
      (do
        (shutdown-hook #(close-handle))
        close-handle))))

(defn -main [& args]
  (start-server args))

