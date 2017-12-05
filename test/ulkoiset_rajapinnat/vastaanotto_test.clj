(ns ulkoiset-rajapinnat.vastaanotto-test
  (:require [manifold.deferred :as d]
            [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.utils.rest :refer [get-as-promise parse-json-body to-json]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid]]
            [ulkoiset-rajapinnat.fixture :refer :all]))

(use-fixtures :once fixture)

(def vastaanotot-json (slurp "test/resources/haku.json"))

(defn mock-endpoints [url]
  (log/info url)
  (case url
    "http://fake.virkailija.opintopolku.fi/valinta-tulos-service/haku/1.2.246.562.29.25191045126" (d/future {:status 200 :body vastaanotot-json })
    (d/future {:status 404 :body "[]"})))

(deftest odw-api-test
  (testing "Fetch vastaanotot"
    (with-redefs [get-as-promise (fn [url] (mock-endpoints url))]
      (let [response (client/get (api-call "/api/vastaanotto-for-haku/1.2.246.562.29.25191045126"))
            status (-> response :status)
            body (-> (parse-json-body response))]
        (is (= status 200))
        (print (to-json body))
        ;(def expected (parse-string (slurp "test/resources/result.json")))
        ;(def difference (diff expected body))
        ;(is (= [nil nil expected] difference) difference )
        ))))

(run-tests)
