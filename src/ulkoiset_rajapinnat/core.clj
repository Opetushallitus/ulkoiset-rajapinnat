(ns ulkoiset-rajapinnat.core
  (:use [compojure.handler :only [site]]
        [org.httpkit.timer :refer :all]
        [manifold.deferred :only [let-flow catch]]
        [compojure.api.sweet :refer :all]
        [ring.util.http-response :refer :all]
        [ulkoiset-rajapinnat.utils.mongo :refer [create-mongo-client]]
        [ulkoiset-rajapinnat.utils.runtime :refer [shutdown-hook]]
        [ulkoiset-rajapinnat.haku :refer [haku-resource]]
        [ulkoiset-rajapinnat.hakukohde :refer [hakukohde-resource]]
        [ulkoiset-rajapinnat.hakemus :refer [hakemus-resource]]
        [ulkoiset-rajapinnat.config :refer :all]
        org.httpkit.server
        )
  (:require [clj-log4j2.core :as log])
  (:gen-class))

(defn api-opintopolku-routes [config hakuapp-mongo-client]
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Ulkoiset-rajapinnat"
                    :description "Ulkoiset-rajapinnat"}
             }}}
     (context (str (-> config :server :base-url) "/api") []
              :tags ["api"]
              (GET "/healthcheck" []
                   :summary "Health check API"
                   (ok "OK"))
              (GET "/haku/:vuosi" [vuosi]
                :summary "Haut"
                (partial haku-resource config vuosi))
              (GET "/hakukohde/haku/:haku-oid" [haku-oid kausi]
                :summary "Hakukohteet"
                (partial hakukohde-resource config haku-oid))
              (GET "/oppija/:haku-oid" [haku-oid kausi]
                :summary "Oppijat"
                (partial haku-resource config haku-oid))
              (GET "/vastaanotto/:haku-oid" [haku-oid kausi] ; hakuoid + kaudet
                :summary "Vastaanotot"
                (partial haku-resource config haku-oid))
              (GET "/hakemus-for-haku/:haku-oid" [haku-oid kausi] ; hakuoid + kaudet
                 :summary "Hakemukset haku OID:lla"
                 (partial hakemus-resource config hakuapp-mongo-client haku-oid)))))

(defn start-server [args]
  (let [config (read-configuration-file-first-from-varargs-then-from-env-vars args)
        port (-> config :server :port)
        hakuapp-mongo-uri (-> config :hakuapp-mongo :uri)
        hakuapp-mongo-client (create-mongo-client hakuapp-mongo-uri)]
    (shutdown-hook #(.close hakuapp-mongo-client))
    (log/info "Starting server in port {}" port)
    (run-server (api-opintopolku-routes config hakuapp-mongo-client) {:port port})))

(defn -main [& args]
  (start-server args))

