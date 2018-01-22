(ns ulkoiset-rajapinnat.vastaanotto-test
  (:require [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clj-log4j2.core :as log]
            [full.async :refer :all]
            [clj-http.client :as client]
            [org.httpkit.client :as http]
            [clojure.core.async :refer [alt! map merge promise-chan >! go go-loop put! close! timeout <! <!!] :rename {merge async-merge map async-map}]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid-channel]]
            [ulkoiset-rajapinnat.fixture :refer :all]
            [ulkoiset-rajapinnat.vastaanotto :refer [oppijat-batch-size valintapisteet-batch-size trim-streaming-response]])
  (:import (java.io ByteArrayInputStream)))

(use-fixtures :once fixture)

(def vastaanotot-json (slurp "test/resources/vastaanotto/streaming.json"))
(def pistetiedot-json (slurp "test/resources/vastaanotto/pistetiedot.json"))
(def avaimet-json (slurp "test/resources/vastaanotto/avaimet.json"))
(def oppijat-json (slurp "test/resources/vastaanotto/oppijat.json"))

(defn oppijat-chunk [oppijanumerot]
  (to-json (filter (fn [x] (some #(= (get x "oppijanumero") %) (parse-string oppijanumerot))) (parse-string oppijat-json))))
(defn valintapisteet-chunk [hakemus-oidit]
  (to-json (filter (fn [x] (some #(= (get x "hakemusOID") %) (parse-string hakemus-oidit))) (parse-string pistetiedot-json))))

  (defn to-input-stream [string]
    (new ByteArrayInputStream (.getBytes string)))

  (defn channel-response [transform url status data]
    (transform {:opts {:url url} :status status :body (to-input-stream data)}))

  (defn mock-http [url options transform]
    (log/info (str "Mocking url " url))
    (def response (partial channel-response transform url))
    (case url
      "http://fake.virkailija.opintopolku.fi/valinta-tulos-service/haku/streaming/1.2.246.562.29.25191045126/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true" (response 200 vastaanotot-json)
      "http://fake.internal.aws.opintopolku.fi/valintapiste-service/api/pisteet-with-hakemusoids?sessionId=sID&uid=1.2.246.1.1.1&inetAddress=127.0.0.1&userAgent=uAgent" (response 200 (valintapisteet-chunk (options :body)))
      "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/avaimet" (response 200 avaimet-json)
      "http://fake.virkailija.opintopolku.fi/suoritusrekisteri/rest/v1/oppijat/?ensikertalaisuudet=false&haku=1.2.246.562.29.25191045126" (response 200 (oppijat-chunk (options :body)))
      (response 404 "[]")))

  (defn mock-channel [result]
    (let [p (promise-chan)]
      (put! p result)
      (close! p)
      p))

  (deftest vastaanotto-api-test
    (testing "Fetch vastaanotot"
      (with-redefs [oppijat-batch-size 2
                    valintapisteet-batch-size 2
                    http/get (fn [url options transform] (mock-http url options transform))
                    http/post (fn [url options transform] (mock-http url options transform))
                    fetch-jsessionid-channel (fn [a b c d] (mock-channel "FAKEJSESSIONID"))]
        (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126"))
              status (-> response :status)
              body (-> (parse-json-body response))]
          (is (= status 200))
          (log/info (to-json body true))
          (def expected (parse-string (slurp "test/resources/vastaanotto/result.json")))
          (def difference (diff expected body))
          (is (= [nil nil expected] difference) difference))))
    (testing "Trim streaming response"
      (let [parsed (trim-streaming-response (parse-string vastaanotot-json))]
        ;(log/info (to-json parsed true))
        (def expected (parse-string (slurp "test/resources/vastaanotto/parsed.json")))
        (def difference (diff expected parsed))
        (is (= [nil nil expected] difference) difference))))

  (run-tests)
