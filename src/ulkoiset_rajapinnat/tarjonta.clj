(ns ulkoiset-rajapinnat.tarjonta
  (:use org.httpkit.server))

(defn tarjonta-resource [request]
  (with-channel request channel
                (send! channel {:status 200
                                :headers {"Content-Type" "application/json; charset=utf-8"}
                                :body "Tarjonta!"} true)))