(ns ulkoiset-rajapinnat.haku-test
  (:require [clojure.test :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [<! promise-chan >! go put! close!]]
            [ulkoiset-rajapinnat.utils.access :refer [check-ticket-is-valid-and-user-has-required-roles write-access-log]]
            [clj-log4j2.core :as log]
            [org.httpkit.client :as http]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.haku :refer [fetch-haku]]
            [ulkoiset-rajapinnat.utils.koodisto :refer :all]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json]]
            [ulkoiset-rajapinnat.fixture :refer [fixture fake-user api-call resource]]
            [clojure.data :refer [diff]]
            [picomock.core :as pico]
            [ulkoiset-rajapinnat.test_utils :refer [mock-channel channel-response mock-write-access-log assert-access-log-write]]))


(use-fixtures :once fixture)


(defn mock-mapped [response]
  (fn [& varargs]
    (go [[] response (fn [& abc] response)])))

(defn mock-channel-fn [response]
  (fn [& varargs]
    (go response)))

(def koodisto-hakutapa-json (resource "test/resources/haku/koodisto-hakutapa.json"))
(def koodisto-hakutyyppi-json (resource "test/resources/haku/koodisto-hakutyyppi.json"))
(def koodisto-haunkohdejoukko-json (resource "test/resources/haku/koodisto-haunkohdejoukko.json"))
(def koodisto-haunkohdejoukontarkenne-json (resource "test/resources/haku/koodisto-haunkohdejoukontarkenne.json"))
(def koodisto-kausi-json (resource "test/resources/haku/koodisto-kausi.json"))
(def koodisto-kieli-json (resource "test/resources/haku/koodisto-kieli.json"))
(def tarjonta-haut-json (resource "test/resources/haku/tarjonta-haut.json"))

(defn mock-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (case url
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/hakutapa/1" (response 200 koodisto-hakutapa-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/hakutyyppi/1" (response 200 koodisto-hakutyyppi-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/haunkohdejoukko/1" (response 200 koodisto-haunkohdejoukko-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/haunkohdejoukontarkenne/1" (response 200 koodisto-haunkohdejoukontarkenne-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/kausi/1" (response 200 koodisto-kausi-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/kieli/1" (response 200 koodisto-kieli-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/find?TILA=JULKAISTU&HAKUVUOSI=2017" (response 200 tarjonta-haut-json)
    (response 404 "[]")))

(deftest haku-test
  (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                fetch-jsessionid-channel (mock-channel-fn "FAKE-SESSIONID")]

    (testing "Fetch haku-for-year with invalid year returns 400"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [write-access-log access-log-mock]
          (try
            (let [response (client/get (api-call "/api/haku-for-year/-2017"))]
              (is (= false true) "should not reach this line"))
            (catch Exception e
              (log/error "XYZ exception e: " e)
              (assert-access-log-write access-log-mock 400 "Invalid vuosi parameter -2017")
              (let [data (ex-data e)]
                (log/error "XYZ exception data: " data)
                (is (= 400 (data :status))))
              (is (re-find #"Invalid vuosi parameter -2017" ((ex-data e) :body))))))))

    (testing "Fetch haku-for-year internal server error returns 500"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [write-access-log access-log-mock]
          (try
            (let [response (client/get (api-call "/api/haku-for-year/2017"))]
              (is (= false true) "should not reach this line"))
            (catch Exception e
              (assert-access-log-write access-log-mock 500 "Unexpected error from url http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/kieli/1")
              (is (= 500 ((ex-data e) :status)))
              (is (re-find #"Unexpected error" ((ex-data e) :body))))))))

    (testing "Fetch haku-for-year returns no results"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [fetch-haku (mock-channel-fn [])
                      http/get (fn [url options transform] (mock-http url options transform))
                      write-access-log access-log-mock]
          (try
            (let [response (client/get (api-call "/api/haku-for-year/2017"))
                  status (-> response :status)
                  body (-> (parse-json-body response))]
              (assert-access-log-write access-log-mock 200 nil)
              (is (= status 200))
              (log/info (to-json body true))
              (def expected [])
              (def difference (diff expected body))
              (is (= [nil nil expected] difference) difference))))))

    (testing "Fetch haku-for-year returns correct results"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [write-access-log access-log-mock
                      http/get (fn [url options transform] (mock-http url options transform))]
          (try
            (let [response (client/get (api-call "/api/haku-for-year/2017"))
                  status (-> response :status)
                  body (-> (parse-json-body response))]
              (assert-access-log-write access-log-mock 200 nil)
              (is (= status 200))
              (log/info (to-json body true))
              (def expected (parse-string (resource "test/resources/haku/result.json")))
              (def difference (diff expected body))
              (is (= [nil nil expected] difference) difference))))))))
