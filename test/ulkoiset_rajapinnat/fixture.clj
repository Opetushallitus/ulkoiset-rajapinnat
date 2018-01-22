(ns ulkoiset-rajapinnat.fixture
  (:require [clojure.test :refer :all]
            [ulkoiset-rajapinnat.freeport :refer :all]
            [ulkoiset-rajapinnat.generate-edn :refer :all]
            [ulkoiset-rajapinnat.utils.config :refer :all]
            [ulkoiset-rajapinnat.freeport :refer :all]
            [ulkoiset-rajapinnat.core :refer :all]
            ))

(def test-configs {:host-virkailija "http://fake.virkailija.opintopolku.fi"
                   :valintapiste-host-virkailija "http://fake.internal.aws.opintopolku.fi"
                   :vastaanotto-host-virkailija "http://fake.virkailija.opintopolku.fi"
                   :server {:port (get-free-port)
                            :base-url "/ulkoiset-rajapinnat"}})
(defn api-call [path] (str "http://localhost:" (-> test-configs :server :port) (-> test-configs :server :base-url) path "?ticket=faketicket"))

(defn test-edn []
  (let [tmp-test-edn-file (data-to-tmp-edn-file test-configs)]
    (str (name ulkoiset-rajapinnat-property-key) "=" tmp-test-edn-file)))

(defn fixture [f]
  (let [server-close-handle (start-server [(test-edn)])]
    (f)
    (server-close-handle)))