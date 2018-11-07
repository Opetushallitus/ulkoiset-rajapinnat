(ns ulkoiset-rajapinnat.hakukohde-test
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
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [ulkoiset-rajapinnat.vastaanotto :refer [oppijat-batch-size valintapisteet-batch-size trim-streaming-response]]
            [picomock.core :as pico]
            [ulkoiset-rajapinnat.test_utils :refer [mock-channel channel-response mock-write-access-log assert-access-log-write mock-haku-not-found-http]])
  (:import (java.io ByteArrayInputStream)))

(use-fixtures :once fixture)

(def koulutustyyppi-json (resource "test/resources/hakukohde/koulutustyyppi.json"))
(def hakukohde-json (resource "test/resources/hakukohde/hakukohde.json"))
(def hakukohde-empty-json (resource "test/resources/hakukohde/hakukohde-empty.json"))
(def hakukohde-tulos-json (resource "test/resources/hakukohde/hakukohde-tulos.json"))
(def hakukohde-tulos-empty-json (resource "test/resources/hakukohde/hakukohde-tulos-empty.json"))
(def koulutus-json (resource "test/resources/hakukohde/koulutus.json"))
(def koulutus-empty-json (resource "test/resources/hakukohde/koulutus-empty.json"))
(def kieli-json (resource "test/resources/hakukohde/kieli.json"))
(def organisaatio-json (resource "test/resources/hakukohde/organisaatio.json"))
(def tilastokeskus-json (resource "test/resources/hakukohde/tilastokeskus.json"))

(defn mock-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (case url
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126" (response 200 "{\"result\": \"dummy\"}")
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.9999009999" (response 200 "{\"result\": \"dummy\"}")
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126/hakukohdeTulos?hakukohdeTilas=JULKAISTU&count=-1" (response 200 hakukohde-tulos-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.9999009999/hakukohdeTulos?hakukohdeTilas=JULKAISTU&count=-1" (response 200 hakukohde-tulos-empty-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/koulutus/search?hakuOid=1.2.246.562.29.25191045126" (response 200 koulutus-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/koulutus/search?hakuOid=1.2.246.562.29.9999009999" (response 200 koulutus-empty-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus" (response 200 tilastokeskus-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/hakukohde/search?hakuOid=1.2.246.562.29.25191045126&tila=JULKAISTU" (response 200 hakukohde-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/hakukohde/search?hakuOid=1.2.246.562.29.9999009999&tila=JULKAISTU" (response 200 hakukohde-empty-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/kieli/1" (response 200 kieli-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/koulutustyyppi/1" (response 200 koulutustyyppi-json)
    "http://fake.virkailija.opintopolku.fi/organisaatio-service/rest/organisaatio/v3/findbyoids" (response 200 organisaatio-json)
    (response 404 "[]")))

(defn mock-http-stuck [url options transform]
  (log/info (str "Mocking url for tilastokeskus failed case " url))
  (def response (partial channel-response transform url))
  (case url
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126" (response 200 "{\"result\": \"dummy\"}")
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126/hakukohdeTulos?hakukohdeTilas=JULKAISTU&count=-1" (response 200 hakukohde-tulos-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/koulutus/search?hakuOid=1.2.246.562.29.25191045126" (response 200 koulutus-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus" (response 500 tilastokeskus-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/hakukohde/search?hakuOid=1.2.246.562.29.25191045126&tila=JULKAISTU" (response 200 hakukohde-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/kieli/1" (response 200 kieli-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/koulutustyyppi/1" (response 200 koulutustyyppi-json)
    "http://fake.virkailija.opintopolku.fi/organisaatio-service/rest/organisaatio/v3/findbyoids" (response 200 organisaatio-json)
    (response 404 "[]")))

(deftest hakukohde-api-test
  (testing "return status 200 and empty list if there are no hakukohde for existing haku, and do not invoke hakukohde/tilastokeskus"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (mock-http url options transform))
                    http/post (fn [url options transform] (mock-http url options transform))
                    fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))
                    write-access-log access-log-mock]
        (let [response (client/get (api-call "/api/hakukohde-for-haku/1.2.246.562.29.9999009999"))
              status (-> response :status)
              body (-> (parse-json-body response))]
          (assert-access-log-write access-log-mock 200 nil)
          (is (= status 200))
          (log/info (to-json body true))
          (def expected (parse-string (resource "test/resources/hakukohde/result-empty.json")))
          (def difference (diff expected body))
          (is (= [nil nil expected] difference) difference)))))

  (testing "return status 404 if haku is not found"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (mock-haku-not-found-http url transform))
                    fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))
                    write-access-log access-log-mock]
        (try
          (let [response (client/get (api-call "/api/hakukohde-for-haku/INVALID_HAKU"))]
            (is (= false true)))
          (catch Exception e
            (assert-access-log-write access-log-mock 404 "Haku INVALID_HAKU not found")
            (is (= 404 ((ex-data e) :status))))))))

  (testing "return status 500 if POST request to tilastokeskus fails with status 500 (don't get stuck)"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (mock-http-stuck url options transform))
                    http/post (fn [url options transform] (mock-http-stuck url options transform))
                    fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))
                    write-access-log access-log-mock]
        (try
          (let [response (client/get (api-call "/api/hakukohde-for-haku/1.2.246.562.29.25191045126"))]
            (is (= false true)))
          (catch Exception e
            (assert-access-log-write access-log-mock 500 "Unexpected response status 500 from url http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus")
            (is (= 500 ((ex-data e) :status))))))))


  (testing "Fetch hakukohde"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (mock-http url options transform))
                    http/post (fn [url options transform] (mock-http url options transform))
                    fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))
                    write-access-log access-log-mock]
        (let [response (client/get (api-call "/api/hakukohde-for-haku/1.2.246.562.29.25191045126"))
              status (-> response :status)
              body (-> (parse-json-body response))]
          (is (= status 200))
          (log/info (to-json body true))
          (def expected (parse-string (resource "test/resources/hakukohde/result.json")))
          (def difference (diff expected body))
          (is (= [nil nil expected] difference))
          (assert-access-log-write access-log-mock 200 nil)))))

  (testing "Fetch hakukohde needing to batch organisations"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                      oppijat-batch-size 2
                      valintapisteet-batch-size 2
                      http/get (fn [url options transform] (mock-http url options transform))
                      http/post (fn [url options transform] (mock-http url options transform))
                      fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))
                      write-access-log access-log-mock
                      ulkoiset-rajapinnat.organisaatio/organisaatio-batch-size 1]
          (let [response (client/get (api-call "/api/hakukohde-for-haku/1.2.246.562.29.25191045126"))
                status (-> response :status)
                body (-> (parse-json-body response))]
            (is (= status 200))
            (log/info (to-json body true))
            (def expected (parse-string (resource "test/resources/hakukohde/result.json")))
            (def difference (diff expected body))
            (is (= [nil nil expected] difference))
            (assert-access-log-write access-log-mock 200 nil))))))
