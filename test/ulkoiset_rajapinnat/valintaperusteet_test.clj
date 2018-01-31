(ns ulkoiset-rajapinnat.valintaperusteet-test
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json post-json-as-channel]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [ulkoiset-rajapinnat.test_utils :refer :all])
  (:import (java.io ByteArrayInputStream)))

(use-fixtures :once fixture)

(def hakukohteet-json (resource "test/resources/hakukohteet.json"))
(def valinnanvaiheet-json (resource "test/resources/valinnanvaiheet.json"))
(def valintatapajonot-json (resource "test/resources/valintatapajonot.json"))
(def hakijaryhmat-json (resource "test/resources/hakijaryhmat.json"))
(def valintaryhmat-json (resource "test/resources/valintaryhmat.json"))
(def avaimet-json (resource "test/resources/avaimet.json"))

(defn mock-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (case url
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/hakukohteet" (response 200 hakukohteet-json)
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/valinnanvaiheet" (response 200 valinnanvaiheet-json)
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/valinnanvaihe/valintatapajonot" (response 200 valintatapajonot-json)
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/hakijaryhmat" (response 200 hakijaryhmat-json)
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/valintaryhmat" (response 200 valintaryhmat-json)
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/avaimet" (response 200 avaimet-json)
    (response 404 "[]")))

(deftest valintaperusteet-api-test
  (testing "valintaperusteet -> hakukohde not found"
    (with-redefs [post-json-as-channel (fn [url data mapper j-session-id] (throw (RuntimeException. (str "GOT 404 FROM URL " url))) )
                  fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))]
      (try
        (let [response (client/post (api-call "/api/valintaperusteet/hakukohde") {:body "[\"1.2.3.444\"]" :content-type :json})]
            (is (= false true)))
        (catch Exception e
          (is (= 500 ((ex-data e) :status)))))))
  (testing "valintaperusteet -> hakukohteet found"
    (with-redefs [http/post (fn [url options transform] (mock-http url options transform))
                  fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))]
      (let [response (client/post (api-call "/api/valintaperusteet/hakukohde") {:body "[\"1.2.246.562.20.16152550832\", \"1.2.246.562.20.96011436637\", \"1.2.246.562.20.76494006901\", \"1.2.246.562.20.18496942519\"]" :content-type :json})
            status (-> response :status)
            body (-> (parse-json-body response))]
        (is (= status 200))
        (def expected (parse-string (resource "test/resources/valintaperusteet-result.json")))
        (def difference (diff expected body))
        (is (= [nil nil expected] difference) difference)))))

(run-tests)