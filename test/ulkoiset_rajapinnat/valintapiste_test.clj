(ns ulkoiset-rajapinnat.valintapiste-test
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [clojure.core.async :refer [go <!!]]
            [ulkoiset-rajapinnat.utils.access :refer [check-ticket-is-valid-and-user-has-required-roles write-access-log]]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json post-json-as-channel]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel fetch-service-ticket-channel]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [picomock.core :as pico]
            [ulkoiset-rajapinnat.test_utils :refer [mock-channel channel-response mock-write-access-log assert-access-log-write]]
            [clojure.string :as str]))

(use-fixtures :once fixture)


(def pistetiedot-json (resource "test/resources/vastaanotto/pistetiedot.json"))

(defn mock-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (if (str/starts-with? url "http://fake.virkailija.opintopolku.fi/valintapiste-service/api/haku/1.2.3.1111/hakukohde/1.2.3.444?sessionId=-&uid=1.2.246.562.24.1234567890&inetAddress=127.0.0.1&userAgent=")
    (response 200 pistetiedot-json)
    (response 404 "[]")))

(deftest valintapisteet-api-test

  (testing "valintapisteet hakukohteelle -> not found"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    http/get (fn [url options transform] (mock-http url options transform))
                    fetch-jsessionid-channel (fn [& _] (mock-channel "FAKEJSESSIONID"))
                    fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))
                    write-access-log access-log-mock]
        (let [apicall (api-call "/api/valintapiste/haku/1.2.3.nonexistent/hakukohde/1.2.3.nonexistent")
              response (try
                         ((client/get apicall)
                         (is (= false)))
                         (catch Exception e
                           (is (= true))))]
          (assert-access-log-write access-log-mock 404 nil)))))

  (testing "valintapisteet hakukohteelle -> found"
    (let [access-log-mock (pico/mock mock-write-access-log)]
      (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                    http/get (fn [url options transform] (mock-http url options transform))
                    fetch-jsessionid-channel (fn [& _] (mock-channel "FAKEJSESSIONID"))
                    fetch-service-ticket-channel (fn [x y] (mock-channel "FAKESERVICETICKET"))
                    write-access-log access-log-mock]
        (let [apicall (api-call "/api/valintapiste/haku/1.2.3.1111/hakukohde/1.2.3.444")
              response (try
                         (client/get apicall)
                         (catch Exception e
                           (log/error e (format "Problem calling url " apicall))
                           (throw e)))
              status (-> response :status)
              body (-> (parse-json-body response))
              expected (parse-string (resource "test/resources/vastaanotto/pistetiedot.json"))
              difference (diff expected body)]
          (is (= status 200))
          (is (= [nil nil expected] difference) difference)
          (assert-access-log-write access-log-mock 200 nil))))))
