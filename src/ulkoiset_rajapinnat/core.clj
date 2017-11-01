(ns ulkoiset-rajapinnat.core
  (:use [compojure.handler :only [site]]
        [org.httpkit.timer :refer :all]
        [manifold.deferred :only [let-flow catch]]
        [compojure.api.sweet :refer :all]
        [ring.util.http-response :refer :all]
        [ulkoiset-rajapinnat.tarjonta :as tarjonta]
        [ulkoiset-rajapinnat.config :refer :all]
        org.httpkit.server
        )
  (:require [clj-log4j2.core :as log])
  (:gen-class))

(defn api-opintopolku-routes [config]
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
                   :summary "Hakujen OID:t"
                   (ok "OK"))
              (GET "/tarjonta/:vuosi" [vuosi]
                   :summary "Hakujen OID:t"
                   (partial tarjonta/tarjonta-resource config vuosi)))))

(defn start-server [args]
  (let [config (read-configuration-file-first-from-varargs-then-from-env-vars args)
        port (-> config :server :port)]
    (log/info "Starting server in port {}" port)
    (run-server (api-opintopolku-routes config) {:port port})))

(defn -main [& args]
  (start-server args))

