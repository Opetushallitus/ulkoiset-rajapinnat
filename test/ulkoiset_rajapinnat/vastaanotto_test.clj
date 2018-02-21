(ns ulkoiset-rajapinnat.vastaanotto-test
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [clojure.core.async :refer [go]]
            [ulkoiset-rajapinnat.utils.access :refer [check-ticket-is-valid-and-user-has-required-roles]]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [ulkoiset-rajapinnat.vastaanotto :refer [oppijat-batch-size valintapisteet-batch-size trim-streaming-response]]
            [ulkoiset-rajapinnat.test_utils :refer :all]
            [clojure.string :as str])
  (:import (java.io ByteArrayInputStream)))

(use-fixtures :once fixture)

(def vastaanotot-json (resource "test/resources/vastaanotto/streaming.json"))
(def pistetiedot-json (resource "test/resources/vastaanotto/pistetiedot.json"))
(def avaimet-json (resource "test/resources/vastaanotto/avaimet.json"))
(def oppijat-json (resource "test/resources/vastaanotto/oppijat.json"))
(def haku-json (resource "test/resources/vastaanotto/haku.json"))
(def tilastokeskus-json (resource "test/resources/vastaanotto/tilastokeskus.json"))

(defn oppijat-chunk [oppijanumerot]
  (to-json (filter (fn [x] (some #(= (get x "oppijanumero") %) (parse-string oppijanumerot))) (parse-string oppijat-json))))
(defn valintapisteet-chunk [hakemus-oidit]
  (to-json (filter (fn [x] (some #(= (get x "hakemusOID") %) (parse-string hakemus-oidit))) (parse-string pistetiedot-json))))

(defn mock-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (if (str/starts-with? url "http://fake.internal.virkailija.opintopolku.fi/valintapiste-service/api/pisteet-with-hakemusoids?sessionId=-&uid=1.2.246.562.24.1234567890&inetAddress=127.0.0.1&userAgent=")
    (response 200 (valintapisteet-chunk (options :body)))
    (case url
      "http://fake.internal.virkailija.opintopolku.fi/valinta-tulos-service/haku/streaming/1.2.246.562.29.25191045126/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true" (response 200 vastaanotot-json)
      "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/avaimet" (response 200 avaimet-json)
      "http://fake.virkailija.opintopolku.fi/suoritusrekisteri/rest/v1/oppijat/?ensikertalaisuudet=false&haku=1.2.246.562.29.25191045126" (response 200 (oppijat-chunk (options :body)))
      "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126" (response 200 haku-json)
      "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus" (response 200 tilastokeskus-json)
      (response 404 "[]"))))

(defn mock-http-with-ise [url options transform]
  (if (= "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/avaimet" url)
    (channel-response transform url 500 "{\"error\":\"Internal server error\"}")
    (mock-http url options transform)))

(deftest vastaanotto-api-test
 (comment
   (testing "No vastaanotot found"
     (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                   oppijat-batch-size 2
                   valintapisteet-batch-size 2
                   http/get (fn [url options transform] (channel-response transform url 404 ""))
                   http/post (fn [url options transform] (channel-response transform url 404 ""))
                   fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))]
       (try
         (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?vuosi=2017&kausi=s"))]
           (is (= false true)))
         (catch Exception e
           (is (= 500 ((ex-data e) :status)))))))
   (testing "Fetch vastaanotot"
     (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                   oppijat-batch-size 2
                   valintapisteet-batch-size 2
                   http/get (fn [url options transform] (mock-http url options transform))
                   http/post (fn [url options transform] (mock-http url options transform))
                   fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))]
       (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?vuosi=2017&kausi=s"))
             status (-> response :status)
             body (-> (parse-json-body response))]
         (is (= status 200))
         (log/info (to-json body true))
         (def expected (parse-string (resource "test/resources/vastaanotto/result.json")))
         (def difference (diff expected body))
         (is (= [nil nil expected] difference) difference))))

   (testing "Trim streaming response"
     (let [parsed (trim-streaming-response ["1.2.246.562.20.72385087522", "1.2.246.562.20.16902536479", "1.2.246.562.20.760269451710", "1.2.246.562.20.16902536479"] (parse-string vastaanotot-json))]
       (def expected (parse-string (resource "test/resources/vastaanotto/parsed.json")))
       (def difference (diff expected parsed))
       (is (= [nil nil expected] difference) difference)))
   (testing "Trim streaming response - remove hakukohteet"
     (let [parsed (trim-streaming-response ["1.2.246.562.20.72385087522", "1.2.246.562.20.16902536479"] (parse-string vastaanotot-json))]
       (def expected (parse-string (resource "test/resources/vastaanotto/parsed2.json")))
       (def difference (diff expected parsed))
       (log/info (to-json parsed true))
       (is (= [nil nil expected] difference) difference)))
   (testing "Trim streaming response - no hakukohteet"
     (let [parsed (trim-streaming-response [] (parse-string vastaanotot-json))]
       (def expected [])
       (def difference (diff expected parsed))
       (is (= [nil nil expected] difference) difference))))
   (testing "Internal server error"
     (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                   oppijat-batch-size 2
                   valintapisteet-batch-size 2
                   http/get (fn [url options transform] (mock-http-with-ise url options transform))
                   http/post (fn [url options transform] (mock-http-with-ise url options transform))
                   fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))]
       (try
         (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?vuosi=2017&kausi=s"))]
           (is (= false true)))
         (catch Exception e
           (do
             (is (= 500 ((ex-data e) :status)))))))))