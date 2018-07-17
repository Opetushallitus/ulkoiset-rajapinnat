(ns ulkoiset-rajapinnat.valintaperusteet-test
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [clojure.core.async :refer [go]]
            [ulkoiset-rajapinnat.utils.access :refer [check-ticket-is-valid-and-user-has-required-roles write-access-log]]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json post-json-as-channel]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [picomock.core :as pico]
            [ulkoiset-rajapinnat.test_utils :refer [mock-channel channel-response mock-write-access-log assert-access-log-write]])
  (:import (java.io ByteArrayInputStream)))

(use-fixtures :once fixture)

(def hakukohteet-json (resource "test/resources/valintaperusteet/hakukohteet.json"))
(def valinnanvaiheet-json (resource "test/resources/valintaperusteet/valinnanvaiheet.json"))
(def valintatapajonot-json (resource "test/resources/valintaperusteet/valintatapajonot.json"))
(def hakijaryhmat-json (resource "test/resources/valintaperusteet/hakijaryhmat.json"))
(def valintaryhmat-json (resource "test/resources/valintaperusteet/valintaryhmat.json"))
(def avaimet-json (resource "test/resources/valintaperusteet/avaimet.json"))

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
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    post-json-as-channel (fn [url data mapper j-session-id] (throw (RuntimeException. (str "GOT 404 FROM URL " url))))
                    fetch-jsessionid-channel (fn [& _] (mock-channel "FAKEJSESSIONID"))
                    write-access-log access-log-mock]
        (try
          (let [response (client/post (api-call "/api/valintaperusteet/hakukohde") {:body "[\"1.2.3.444\"]" :content-type :json})]
            (is (= false true)))
          (catch Exception e
            (is (= 500 ((ex-data e) :status)))
            (assert-access-log-write access-log-mock 500 "GOT 404 FROM URL http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/hakukohteet"))))))

  (testing "valintaperusteet -> hakukohteet found"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    http/post (fn [url options transform] (mock-http url options transform))
                    fetch-jsessionid-channel (fn [& _] (mock-channel "FAKEJSESSIONID"))
                    write-access-log access-log-mock]
        (let [response (client/post (api-call "/api/valintaperusteet/hakukohde") {:body "[\"1.2.246.562.20.16152550832\", \"1.2.246.562.20.96011436637\", \"1.2.246.562.20.76494006901\", \"1.2.246.562.20.18496942519\"]" :content-type :json})
              status (-> response :status)
              body (-> (parse-json-body response))]
          (is (= status 200))
          (def expected (parse-string (resource "test/resources/valintaperusteet/result.json")))
          (def difference (diff expected body))
          (is (= [nil nil expected] difference) difference)
          (assert-access-log-write access-log-mock 200 nil))))))