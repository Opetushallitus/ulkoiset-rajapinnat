(ns ulkoiset-rajapinnat.utils.cas
  (:require [manifold.deferred :refer [let-flow catch chain]]
            [clojure.string :as str]
            [clj-log4j2.core :as log]
            [ulkoiset-rajapinnat.rest :refer [get-as-promise post-form-as-promise status body body-and-close exception-response parse-json-body to-json]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [jsoup.soup :refer :all]))

(def tgt-api "%s/cas/v1/tickets")

(defn read-action-attribute-from-cas-response [response]
  (let [html (response :body)
        action-attribute (attr "action" ($ (parse html) "form"))]
    (first action-attribute)))

(defn post-tgt-request
  [host username password]
  (chain (post-form-as-promise (format tgt-api host)
                        {:username username
                         :password password})
         read-action-attribute-from-cas-response))

(defn post-st-request
  [service host]
  (chain (post-form-as-promise host {:service service}) #(% :body)))

(defn fetch-service-ticket
  [host service username password]
  (let [absolute-service (str host service "/j_spring_cas_security_check")
        st-promise (chain (post-tgt-request host username password)
                          (partial post-st-request absolute-service))]
    ;(let-flow [st st-promise] (prn st))
    st-promise))
