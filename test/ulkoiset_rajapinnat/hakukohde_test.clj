(ns ulkoiset-rajapinnat.hakukohde-test
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [org.httpkit.client :as http]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.test_utils :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [ulkoiset-rajapinnat.vastaanotto :refer [oppijat-batch-size valintapisteet-batch-size trim-streaming-response]])
  (:import (java.io ByteArrayInputStream)))

(use-fixtures :once fixture)

(def koulutustyyppi-json (slurp "test/resources/hakukohde/koulutustyyppi.json"))
(def hakukohde-json (slurp "test/resources/hakukohde/hakukohde.json"))
(def hakukohde-tulos-json (slurp "test/resources/hakukohde/hakukohde-tulos.json"))
(def koulutus-json (slurp "test/resources/hakukohde/koulutus.json"))
(def kieli-json (slurp "test/resources/hakukohde/kieli.json"))
(def organisaatio-json (slurp "test/resources/hakukohde/organisaatio.json"))

(defn mock-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (case url
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.25191045126/hakukohdeTulos?hakukohdeTilas=JULKAISTU&count=-1" (response 200 hakukohde-tulos-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/koulutus/search?hakuOid=1.2.246.562.29.25191045126" (response 200 koulutus-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/koulutus/oids" (response 200 (to-json {"result" []}))
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/hakukohde/search?hakuOid=1.2.246.562.29.25191045126&tila=JULKAISTU" (response 200 hakukohde-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/kieli/1" (response 200 kieli-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/koulutustyyppi/1" (response 200 koulutustyyppi-json)
    "http://fake.virkailija.opintopolku.fi/organisaatio-service/rest/organisaatio/v3/findbyoids" (response 200 organisaatio-json)
    (response 404 "[]")))

(deftest hakukohde-api-test
  (testing "hakukohde not found"
    (with-redefs [http/get (fn [url options transform] (channel-response transform url 404 ""))
                  http/post (fn [url options transform] (channel-response transform url 404 ""))
                  fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))]
      (try
        (let [response (client/post (api-call "/api/hakukohde-for-haku/1.2.246.562.29.25191045126") {:body "[\"1.2.3.444\"]" :content-type :json})]
          (is (= false true)))
        (catch Exception e
          (is (= 404 ((ex-data e) :status)))))))
  (testing "Fetch hakukohde"
    (with-redefs [oppijat-batch-size 2
                  valintapisteet-batch-size 2
                  http/get (fn [url options transform] (mock-http url options transform))
                  http/post (fn [url options transform] (mock-http url options transform))
                  fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))]
      (let [response (client/get (api-call "/api/hakukohde-for-haku/1.2.246.562.29.25191045126"))
            status (-> response :status)
            body (-> (parse-json-body response))]
        (is (= status 200))
        (log/info (to-json body true))
        (def expected (parse-string (slurp "test/resources/hakukohde/result.json")))
        (def difference (diff expected body))
        (is (= [nil nil expected] difference) difference)))))






(run-tests)