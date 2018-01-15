(ns ulkoiset-rajapinnat.vastaanotto-test
  (:require [manifold.deferred :as d]
            [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise parse-json-body to-json post-json-as-promise get-json-with-cas to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [ulkoiset-rajapinnat.vastaanotto :refer [oppijat-batch-size valintapisteet-batch-size]])
  (:import (java.io ByteArrayInputStream)))

(use-fixtures :once fixture)

(def vastaanotot-json (slurp "test/resources/vastaanotto/streaming.json"))
(def pistetiedot-json (slurp "test/resources/vastaanotto/pistetiedot.json"))
(def avaimet-json (slurp "test/resources/vastaanotto/avaimet.json"))
(def oppijat-json (slurp "test/resources/vastaanotto/oppijat.json"))

(defn oppijat-chunk [oppijanumerot]
  (to-json (filter (fn [x] (some #(= (get x "oppijanumero") %) oppijanumerot)) (parse-string oppijat-json))))
(defn valintapisteet-chunk [hakemus-oidit]
  (to-json (filter (fn [x] (some #(= (get x "hakemusOID") %) hakemus-oidit)) (parse-string pistetiedot-json))))

(defn to-input-stream [string]
  (new ByteArrayInputStream (.getBytes string)))

(defn mock-endpoints [url data options]
  (log/info url)
  (case url
    "http://fake.virkailija.opintopolku.fi/valinta-tulos-service/haku/streaming/1.2.246.562.29.25191045126/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true" (d/future {:status 200 :body  (to-input-stream vastaanotot-json)})
    "http://fake.internal.aws.opintopolku.fi/valintapiste-service/api/pisteet-with-hakemusoids?sessionId=sID&uid=1.2.246.1.1.1&inetAddress=127.0.0.1&userAgent=uAgent" (d/future {:status 200 :body (valintapisteet-chunk data) })
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/avaimet" (d/future {:status 200 :body avaimet-json})
    "http://fake.virkailija.opintopolku.fi/suoritusrekisteri/rest/v1/oppijat/?ensikertalaisuudet=false&haku=1.2.246.562.29.25191045126" (d/future {:status 200 :body (oppijat-chunk data)})
    (d/future {:status 404 :body "[]"})))

(defn mock-get-as-promise
  ([url] (mock-endpoints url {} {}))
  ([url options] (mock-endpoints url {} options)))

(deftest vastaanotto-api-test
  (testing "Fetch vastaanotot"
    (with-redefs [oppijat-batch-size 2
                  valintapisteet-batch-size 2
                  post-json-as-promise (fn [url data options] (mock-endpoints url data options))
                  fetch-jsessionid (fn [a b c d] (str "FAKEJSESSIONID"))
                  get-as-promise (partial mock-get-as-promise)]
      (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126"))
            status (-> response :status)
            body (-> (parse-json-body response))]
        (is (= status 200))
        (print (to-json body))
        (def expected (parse-string (slurp "test/resources/vastaanotto/result.json")))
        (def difference (diff expected body))
        (is (= [nil nil expected] difference) difference )
        ))))

(run-tests)
