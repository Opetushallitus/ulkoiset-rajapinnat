(ns ulkoiset-rajapinnat.odw-test
  (:require [manifold.deferred :as d]
            [clojure.test :refer :all]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clj-log4j2.core :as log]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.utils.rest :refer [post-as-promise parse-json-body]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid]]
            [ulkoiset-rajapinnat.fixture :refer :all]))

(use-fixtures :once fixture)

(def hakukohteet-json (slurp "test/resources/hakukohteet.json"))
(def valinnanvaiheet-json (slurp "test/resources/valinnanvaiheet.json"))
(def valintatapajonot-json (slurp "test/resources/valintatapajonot.json"))
(def hakijaryhmat-json (slurp "test/resources/hakijaryhmat.json"))

(defn mock-endpoints [url options]
  (case url
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/hakukohteet" (d/future {:status 200 :body hakukohteet-json })
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/valinnanvaiheet" (d/future {:status 200 :body valinnanvaiheet-json })
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/valinnanvaihe/valintatapajonot" (d/future {:status 200 :body valintatapajonot-json})
    "http://fake.virkailija.opintopolku.fi/valintaperusteet-service/resources/hakukohde/hakijaryhmat" (d/future {:status 200 :body hakijaryhmat-json})
    (d/future {:status 404 :body "[]"})))

(deftest odw-api-test
  (testing "Odw -> hakukohde not found"
    (with-redefs [post-as-promise (fn [a b c] (d/future {:status 404 :body "[]"}))
                  fetch-jsessionid (fn [a b c d] (str "FAKEJSESSIONID"))]
      (let [response (client/post (api-call "/api/odw/hakukohde") {:body "[\"1.2.3.444\"]" :content-type :json})
            status (-> response :status)
            body (-> response :body)]
        (is (= status 200))
        (is (= body "[]")))))
  (testing "Odw -> hakukohteet found"
    (with-redefs [post-as-promise (fn [a b c] (mock-endpoints a (merge b {:body c})))
                  fetch-jsessionid (fn [a b c d] (str "FAKEJSESSIONID"))]
      (let [response (client/post (api-call "/api/odw/hakukohde") {:body "[\"1.2.246.562.20.16152550832\", \"1.2.246.562.20.96011436637\", \"1.2.246.562.20.76494006901\", \"1.2.246.562.20.18496942519\"]" :content-type :json})
            status (-> response :status)
            body (-> (parse-json-body response))]
        (is (= status 200))
        (def expected (parse-string (slurp "test/resources/result.json")))
        (def difference (diff expected body))
        (is (= [nil nil expected] difference) difference )))))

(run-tests)