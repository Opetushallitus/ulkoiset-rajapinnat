(ns ulkoiset-rajapinnat.core
  (:use [compojure.handler :only [site]]
        [org.httpkit.timer :refer :all]
        [manifold.deferred :only [let-flow catch]]
        [compojure.api.sweet :refer :all]
        [ring.util.http-response :refer :all]
        [ulkoiset-rajapinnat.tarjonta :as tarjonta]
        org.httpkit.server
        ))

(defroutes api-opintopolku-routes
           (context "/api" []
                    :tags ["api"]
                    (GET "/healthcheck" []
                         ;:return s/Str
                         :summary "Hakujen OID:t"
                         (ok "OK"))
                    (GET "/tarjonta" []
                         ;:return s/Str
                         :summary "Hakujen OID:t"
                         tarjonta/tarjonta-resource)))

(defn -main [& args]
  (run-server (site #'api-opintopolku-routes) {:port 8080}))

