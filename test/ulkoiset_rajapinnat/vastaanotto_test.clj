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
            [ulkoiset-rajapinnat.utils.access :refer [check-ticket-is-valid-and-user-has-required-roles write-access-log]]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel fetch-service-ticket-channel]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [ulkoiset-rajapinnat.vastaanotto :refer [oppijat-batch-size valintapisteet-batch-size trim-streaming-response]]
            [picomock.core :as pico]
            [ulkoiset-rajapinnat.test_utils :refer [mock-channel channel-response mock-write-access-log assert-access-log-write mock-haku-not-found-http]]
            [clojure.string :as str])
  (:import (java.io ByteArrayInputStream)))

(use-fixtures :once fixture)

(def vastaanotot-json (resource "test/resources/vastaanotto/streaming.json"))
(def pistetiedot-json (resource "test/resources/vastaanotto/pistetiedot.json"))
(def avaimet-json (resource "test/resources/vastaanotto/avaimet.json"))
(def avaimet-empty-json (resource "test/resources/vastaanotto/avaimet-empty.json"))
(def oppijat-json (resource "test/resources/vastaanotto/oppijat.json"))
(def haku-json (resource "test/resources/vastaanotto/haku.json"))
(def tilastokeskus-json (resource "test/resources/vastaanotto/hakukohteet.json"))

(defn oppijat-chunk [oppijanumerot]
  (to-json (filter (fn [x] (some #(= (get x "oppijanumero") %) (parse-string oppijanumerot))) (parse-string oppijat-json))))
(defn valintapisteet-chunk [hakemus-oidit]
  (to-json (filter (fn [x] (some #(= (get x "hakemusOID") %) (parse-string hakemus-oidit))) (parse-string pistetiedot-json))))

(defn mock-http [url options transform & flags]
  (log/info (str "Mocking url " url " flags: " flags))
  (def response (partial channel-response transform url))
  (let [use-empty-avaimet (some #{:empty-avaimet} flags)]
    (if (str/starts-with? url "http://fake.virkailija.opintopolku.fi/valintapiste-service/api/pisteet-with-hakemusoids?sessionId=-&uid=1.2.246.562.24.1234567890&inetAddress=127.0.0.1&userAgent=")
      (response 200 (valintapisteet-chunk (options :body)))
      (case url
        "http://fake.internal.virkailija.opintopolku.fi/valinta-tulos-service/haku/streaming/1.2.246.562.29.25191045126/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true" (response 200 vastaanotot-json)
        "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/avaimet" (response 200 (if use-empty-avaimet avaimet-empty-json avaimet-json))
        "http://fake.internal.virkailija.opintopolku.fi/suoritusrekisteri/rest/v1/oppijat/?ensikertalaisuudet=false&haku=1.2.246.562.29.25191045126" (response 200 (oppijat-chunk (options :body)))
        "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126" (response 200 haku-json)
        "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus" (response 200 tilastokeskus-json)
        (response 404 "[]")))))

(deftest vastaanotto-api-test

  (testing "Haku does not exist, return status 404"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (mock-haku-not-found-http url transform))
                    fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))
                    fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))
                    write-access-log access-log-mock]
        (try
          (let [response (client/get (api-call "/api/vastaanotto-for-haku/INVALID_HAKU?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=s"))]
            (is (= false true)))
          (catch Exception e
            (assert-access-log-write access-log-mock 404 "Haku INVALID_HAKU not found")
            (is (= 404 ((ex-data e) :status))))))))

  (testing "No vastaanotot found"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (channel-response transform url 404 ""))
                    http/post (fn [url options transform] (channel-response transform url 404 ""))
                    fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))
                    fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))
                    write-access-log access-log-mock]
        (try
          (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=s"))]
            (is (= false true)))
          (catch Exception e
            (assert-access-log-write access-log-mock 500 "Unexpected response status 404 from url http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126")
            (is (= 500 ((ex-data e) :status))))))))

  (testing "Fetch vastaanotot"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (mock-http url options transform))
                    http/post (fn [url options transform] (mock-http url options transform))
                    fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))
                    fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))
                    write-access-log access-log-mock]
        (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?koulutuksen_alkamisvuosi=2018&koulutuksen_alkamiskausi=k"))
              status (-> response :status)
              body (-> (parse-json-body response))]
          (is (= status 200))
          (assert-access-log-write access-log-mock 200 nil)
          (log/info (to-json body true))
          (def expected (parse-string (resource "test/resources/vastaanotto/result.json")))
          (def difference (diff expected body))
          (is (= [nil nil expected] difference) difference)))))

  (testing "Fetch vastaanotot correctly also when avaimet returns empty list (don't return error)"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (mock-http url options transform :empty-avaimet))
                    http/post (fn [url options transform] (mock-http url options transform :empty-avaimet))
                    fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))
                    fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))
                    write-access-log access-log-mock]
        (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?koulutuksen_alkamisvuosi=2018&koulutuksen_alkamiskausi=k"))
              status (-> response :status)
              body (-> (parse-json-body response))]
          (is (= status 200))
          (assert-access-log-write access-log-mock 200 nil)
          (log/info (to-json body true))
          (def expected (parse-string (resource "test/resources/vastaanotto/result-when-empty-avaimet.json")))
          (def difference (diff expected body))
          (is (= [nil nil expected] difference) difference)))))

  (testing "Fetch vastaanotot correctly also when hakutoiveen valintatapajonot is empty"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    http/get (fn [url options transform] (mock-http url options transform))
                    http/post (fn [url options transform] (mock-http url options transform))
                    fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))
                    fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))
                    write-access-log access-log-mock
                    vastaanotot-json (resource "test/resources/vastaanotto/streaming-empty-valintatapajonot.json")]
        (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?koulutuksen_alkamisvuosi=2018&koulutuksen_alkamiskausi=k"))
              status (-> response :status)]
          (is (= status 200))
          (assert-access-log-write access-log-mock 200 nil))))))

(deftest vts-response-trim-test
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

(defn mock-http-with-ise [url options transform]
  (if (= "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/avaimet" url)
    (channel-response transform url 500 "{\"error\":\"Internal server error\"}")
    (mock-http url options transform)))

(defn mock-http-no-vastaanotot [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (if (= "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/avaimet" url)
    (channel-response transform url 500 "{\"error\":\"Internal server error\"}"))
  (case url
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126" (response 200 haku-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus" (response 200 tilastokeskus-json)
    (response 200 "[]")))

(deftest vastaanotto-error-test
  (testing "Internal server error"
    (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                  oppijat-batch-size 2
                  valintapisteet-batch-size 2
                  http/get (fn [url options transform] (mock-http-with-ise url options transform))
                  http/post (fn [url options transform] (mock-http-with-ise url options transform))
                  fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))
                  fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))]
      (try
        (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=s"))]
          (is (= false true)))
        (catch Exception e
          (do
            (is (= 500 ((ex-data e) :status))))))))
  (testing "No vastaanottoja"
    (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                  http/get (fn [url options transform] (mock-http-no-vastaanotot url options transform))
                  http/post (fn [url options transform] (mock-http-no-vastaanotot url options transform))
                  fetch-jsessionid-channel (fn [a] (mock-channel "FAKEJSESSIONID"))
                  fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))]
      (try (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126?koulutuksen_alkamisvuosi=2018&koulutuksen_alkamiskausi=k"))
                 status (-> response :status)
                 body (-> (parse-json-body response))]
             (is (= status 200))
             (log/info (to-json body true)))
           (catch Exception e
             (log/info (str "Exception " ((ex-data e) :status)))
             (is (= false true)))))))
