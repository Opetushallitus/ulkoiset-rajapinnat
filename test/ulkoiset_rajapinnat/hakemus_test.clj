(ns ulkoiset-rajapinnat.hakemus-test
  (:require [clojure.test :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [<! promise-chan >! go put! close!]]
            [ulkoiset-rajapinnat.utils.access :refer [check-ticket-is-valid-and-user-has-required-roles write-access-log]]
            [ulkoiset-rajapinnat.oppija :refer [fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel]]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [ulkoiset-rajapinnat.utils.rest :refer [parse-json-body to-json]]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [ulkoiset-rajapinnat.onr :refer :all]
            [ulkoiset-rajapinnat.utils.haku_app :refer :all]
            [ulkoiset-rajapinnat.utils.ataru :refer :all]
            [ulkoiset-rajapinnat.utils.koodisto :refer :all]
            [ulkoiset-rajapinnat.utils.tarjonta :refer :all]
            [ulkoiset-rajapinnat.utils.cas :refer :all]
            [ulkoiset-rajapinnat.fixture :refer :all]
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

(def hakemus-2aste-json (resource "test/resources/hakemus/hakemus-2aste.json"))
(def hakemus-kk-json (resource "test/resources/hakemus/hakemus-kk.json"))

(deftest hakemus-test
  (with-redefs [haku-for-haku-oid-channel (mock-channel-fn {"oid", "1.2.3.4.5"})
                hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan (mock-channel-fn ["1.2.3.4"])
                check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                fetch-jsessionid-channel (mock-channel-fn "FAKE-SESSIONID")
                fetch-henkilot-channel (mock-channel-fn [])
                koodisto-as-channel (mock-channel-fn {})]
    (testing "Fetch hakemukset for haku with no hakemuksia!"
      (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-in-batches (mock-mapped [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s%231"))
              status (-> response :status)
              body (response :body)]
          (is (= status 200))
          (is (= body "[]")))))

    (testing "Fetch hakemukset for haku with 'ataru' hakemuksia!"
      (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [{"oid" "1.2.3.4.5.6"}])
                    fetch-hakemukset-from-haku-app-in-batches (mock-mapped [])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s%231")
                                   {:as :json})
              status (-> response :status)
              body (response :body)]
          (is (= status 200))
          (is (= (count body) 1)))))

    (testing "Fetch hakemukset for haku with 'haku-app' hakemuksia!"
      (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-in-batches (mock-mapped [{"oid" "1.2.3.4"}])]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s%231")
                                   {:as :json})
              status (-> response :status)
              body (response :body)]
          (is (= status 200))
          (is (= (count body) 1)))))

    (testing "When haku-app gives 2aste hakemus, the result will have the 2aste pohjakoulutus field!"
      (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-in-batches (fn [x y z mapper] (go (mapper (parse-string hakemus-2aste-json))))]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s%231")
                                   {:as :json})
              status (-> response :status)
              hakemus (first (response :body))]
          (is (= status 200))
          (is (some? hakemus))
          (is (= "2" (hakemus :pohjakoulutus_2aste)))
          (is (nil? (hakemus :pohjakoulutus_kk))))))

    (testing "When haku-app gives KK hakemus, the result will have the KK pohjakoulutus field!"
      (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [])
                    fetch-hakemukset-from-haku-app-in-batches (fn [x y z mapper] (go (mapper (parse-string hakemus-kk-json))))
                    koodisto-as-channel (mock-channel-fn {"koodi1" "pohjakoulutus_amt", "koodi2" "dummy_value", "koodi3" "pohjakoulutus_kk"})]
        (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.94986312133?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s%231")
                                   {:as :json})
              status (-> response :status)
              hakemus (first (response :body))]
          (is (= status 200))
          (is (some? hakemus))
          (is (= #{"pohjakoulutus_amt" "pohjakoulutus_kk"} (set (hakemus :pohjakoulutus_kk))))
          (is (nil? (hakemus :pohjakoulutus_2aste))))))))

(def haku-json (resource "test/resources/hakemus/haku.json"))
(def haku-ataru-deep-json (resource "test/resources/hakemus/haku-ataru-deep.json"))
(def hakukohteet-json (resource "test/resources/hakemus/hakukohteet.json"))
(def henkilot-json (resource "test/resources/hakemus/henkilot.json"))
(def oppijat-json (resource "test/resources/hakemus/oppijat.json"))
(def organisaatio-json (resource "test/resources/hakemus/organisaatio.json"))
(def koodisto-maakoodi-json (resource "test/resources/hakemus/koodisto-maakoodi.json"))

(defn mock-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (case url
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/codeelement/codes/pohjakoulutuskklomake/0" (response 200 "[]")
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.999999" (response 200 haku-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus" (response 200 hakukohteet-json)
    "http://fake.virkailija.opintopolku.fi/oppijanumerorekisteri-service/henkilo/henkilotByHenkiloOidList" (response 200 henkilot-json)
    "http://fake.virkailija.opintopolku.fi/organisaatio-service/rest/organisaatio/v3/findbyoids" (response 200 organisaatio-json)
    (response 404 "[]")))

(deftest lahtokoulu-test
  (testing "Fetch lähtökoulu from Sure 2. aste"
    (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                  fetch-hakemukset-from-ataru (mock-mapped [])
                  fetch-hakemukset-from-haku-app-in-batches (fn [x y z mapper] (go (mapper (parse-string hakemus-2aste-json))))
                  fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel (fn [x y z h] (mock-channel (parse-string oppijat-json)))
                  fetch-service-ticket-channel (mock-channel-fn "FAKEST")
                  http/get (fn [url options transform] (mock-http url options transform))
                  http/post (fn [url options transform] (mock-http url options transform))
                  fetch-jsessionid-channel (mock-channel-fn "FAKEJSESSIONID")]
      (let [expected (parse-string (resource "test/resources/hakemus/result-2aste.json"))
            response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.999999?koulutuksen_alkamisvuosi=2018&koulutuksen_alkamiskausi=kausi_s"))
            status   (-> response :status)
            body     (-> (parse-json-body response))]
        (is (= status 200))
        (is (= expected body))))))

(defn- mock-ataru-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (def ataru-json (resource "test/resources/hakemus/ataru.json"))
  (def ataru-json-without-asuinmaa-and-kotikunta (resource "test/resources/hakemus/ataru-without-asuinmaa-and-kotikunta.json"))
  (case url
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.999999" (response 200 haku-ataru-deep-json)
    "http://fake.virkailija.opintopolku.fi/lomake-editori/api/external/tilastokeskus?hakuOid=1.2.246.562.29.999999" (response 200 ataru-json)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/json/relaatio/rinnasteinen/maatjavaltiot2_246" (response 200 koodisto-maakoodi-json)
    (response 404 "[]")))

(deftest ataru-deep-test
  (testing "Fetch from Ataru, using also the ataru-adapter"
    (with-redefs [hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan (mock-channel-fn ["1.2.3.4"])
                  check-ticket-is-valid-and-user-has-required-roles          (fn [& _] (go fake-user))
                  fetch-jsessionid-channel                                   (mock-channel-fn "FAKE-SESSIONID")
                  fetch-service-ticket-channel                               (mock-channel-fn "FAKEST")
                  fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel    (fn [x y z h] (mock-channel (parse-string oppijat-json)))
                  fetch-henkilot-channel                                     (mock-channel-fn [])
                  koodisto-as-channel                                        (mock-channel-fn {})
                  fetch-hakemukset-from-haku-app-in-batches                  (mock-mapped [])
                  http/get                                                   (fn [url options transform] (mock-ataru-http url options transform))
                  http/post                                                  (fn [url options transform] (mock-ataru-http url options transform))]
      (let [expected (parse-string (resource "test/resources/hakemus/result-ataru.json"))
            response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.999999?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s"))
            status   (-> response :status)
            body     (-> (parse-json-body response))]
        (is (= status 200))
        (is (= expected body))))))

(defn- mock-ataru-http-without-asuinmaa-and-kotikunta [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (def ataru-json-without-asuinmaa-and-kotikunta (resource "test/resources/hakemus/ataru-without-asuinmaa-and-kotikunta.json"))
  (case url
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.999999" (response 200 haku-ataru-deep-json)
    "http://fake.virkailija.opintopolku.fi/lomake-editori/api/external/tilastokeskus?hakuOid=1.2.246.562.29.999999" (response 200 ataru-json-without-asuinmaa-and-kotikunta)
    "http://fake.virkailija.opintopolku.fi/koodisto-service/rest/json/relaatio/rinnasteinen/maatjavaltiot2_246" (response 200 koodisto-maakoodi-json)
    (response 404 "[]")))

(deftest ataru-deep-test-without-asuinmaa-and-kotikunta
  (testing "Fetch from Ataru, using also the ataru-adapter. Missing asuinmaa and kotikunta"
    (with-redefs [hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan (mock-channel-fn ["1.2.3.4"])
                  check-ticket-is-valid-and-user-has-required-roles          (fn [& _] (go fake-user))
                  fetch-jsessionid-channel                                   (mock-channel-fn "FAKE-SESSIONID")
                  fetch-service-ticket-channel                               (mock-channel-fn "FAKEST")
                  fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel    (fn [x y z h] (mock-channel (parse-string oppijat-json)))
                  fetch-henkilot-channel                                     (mock-channel-fn [])
                  koodisto-as-channel                                        (mock-channel-fn {})
                  fetch-hakemukset-from-haku-app-in-batches                  (mock-mapped [])
                  http/get                                                   (fn [url options transform] (mock-ataru-http-without-asuinmaa-and-kotikunta url options transform))
                  http/post                                                  (fn [url options transform] (mock-ataru-http-without-asuinmaa-and-kotikunta url options transform))]
      (let [expected (parse-string (resource "test/resources/hakemus/result-ataru-without-asuinmaa-and-kotikunta.json"))
            response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.999999?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s"))
            status   (-> response :status)
            body     (-> (parse-json-body response))]
        (is (= status 200))
        (is (= expected body))))))

(defn mock-not-found-http [url options transform]
  (log/info (str "Mocking url " url))
  (def response (partial channel-response transform url))
  (def ataru-json (resource "test/resources/hakemus/ataru.json"))
  (case url
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/1.2.246.562.29.999999" (response 200 haku-ataru-deep-json)
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/v1/haku/ABC" (response 200 "{}")
    "http://fake.virkailija.opintopolku.fi/tarjonta-service/rest/hakukohde/tilastokeskus" (response 200 hakukohteet-json)
    (response 404 "[]")))

(deftest valid-and-invalid-requests-deep-test
  (with-redefs [check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                fetch-jsessionid-channel (mock-channel-fn "FAKE-SESSIONID")
                fetch-service-ticket-channel (mock-channel-fn "FAKEST")
                fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel (fn [x y z h] (mock-channel (parse-string oppijat-json)))
                fetch-henkilot-channel (mock-channel-fn [])
                koodisto-as-channel (mock-channel-fn {})
                fetch-hakemukset-from-haku-app-in-batches (mock-mapped [])
                http/get (fn [url options transform] (mock-not-found-http url options transform))
                http/post (fn [url options transform] (mock-not-found-http url options transform))]

    (testing "Haku not found, returns status 404"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [write-access-log access-log-mock]
          (try
            (let [response (client/get (api-call "/api/hakemus-for-haku/ABC?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s"))]
              (is (= false true) "should not reach this line"))
            (catch Exception e
              (assert-access-log-write access-log-mock 404 "Haku ABC not found")
              (is (= 404 ((ex-data e) :status)))
              (is (re-find #"ABC" ((ex-data e) :body))))))))

    (testing "Invalid kausi parameter, returns status 400"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [write-access-log access-log-mock]
          (try
            (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.999999?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=ABC"))]
              (is (= false true) "should not reach this line"))
            (catch Exception e
              (assert-access-log-write access-log-mock 400 "Unknown kausi param: ABC")
              (is (= 400 ((ex-data e) :status)))
              (is (re-find #"Unknown kausi param: ABC" ((ex-data e) :body))))))))

    (testing "if year is not a four digit positive number, returns status 400"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [write-access-log access-log-mock]
          (try
            (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.999999?koulutuksen_alkamisvuosi=-2017&koulutuksen_alkamiskausi=s"))]
              (is (= false true) "should not reach this line"))
            (catch Exception e
              (assert-access-log-write access-log-mock 400 "Invalid vuosi: -2017")
              (is (= 400 ((ex-data e) :status)))
              (is (re-find #"Invalid vuosi: -2017" ((ex-data e) :body))))))))

    (testing "Hakemukset not found in statistics, returns empty array"
      (let [access-log-mock (pico/mock mock-write-access-log)]
        (with-redefs [fetch-hakemukset-from-ataru (mock-mapped [])
                      write-access-log access-log-mock]
          (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.999999?koulutuksen_alkamisvuosi=2015&koulutuksen_alkamiskausi=s"))
                status (-> response :status)
                body (-> response :body)]
            (assert-access-log-write access-log-mock 200 nil)
            (is (= status 200))
            (is (= "[]" body))))))
    (testing "Hakukohde data is retrieved correctly with partitioning from tarjonta"
        (with-redefs [ulkoiset-rajapinnat.utils.tarjonta/hakukohde-batch-size 2
                      hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan (mock-channel-fn ["1.2.3.4"])
                      check-ticket-is-valid-and-user-has-required-roles (fn [& _] (go fake-user))
                      fetch-jsessionid-channel (mock-channel-fn "FAKE-SESSIONID")
                      fetch-service-ticket-channel (mock-channel-fn "FAKEST")
                      fetch-oppijat-for-hakemus-with-ensikertalaisuus-channel (fn [x y z h] (mock-channel (parse-string oppijat-json)))
                      fetch-henkilot-channel (mock-channel-fn [])
                      koodisto-as-channel (mock-channel-fn {})
                      fetch-hakemukset-from-haku-app-in-batches (mock-mapped [])
                      http/get (fn [url options transform] (mock-ataru-http url options transform))
                      http/post (fn [url options transform] (mock-ataru-http url options transform))]
          (let [response (client/get (api-call "/api/hakemus-for-haku/1.2.246.562.29.999999?koulutuksen_alkamisvuosi=2017&koulutuksen_alkamiskausi=kausi_s"))
                status (-> response :status)
                body (-> (parse-json-body response))]
            (is (= status 200))
            (log/info (to-json body true))
            (def expected (parse-string (resource "test/resources/hakemus/result-ataru.json")))
            (def difference (diff expected body))
            (is (= [nil nil expected] difference) difference))))))
