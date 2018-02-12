(ns ulkoiset-rajapinnat.utils.tarjonta
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clojure.core.async :refer [<! promise-chan >! go put! close! map] :rename {map async-map}]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.rest :refer :all]))

(defn- filter-only-haku [haku-oid]
  (fn [response]
    (let [data ((parse-json-body-stream response) "result")
          data-with-haku-and-hakukohde (map #(select-keys % ["oid" "hakukohdeOids"]) data)]
      (mapcat #(% "hakukohdeOids") (filter #(= haku-oid (% "oid")) data-with-haku-and-hakukohde)))))

(defn hakukohde-oids-for-kausi-and-vuosi-channel [haku-oid kausi vuosi]
  (if (not (every? some? [kausi vuosi haku-oid]))
    (throw (RuntimeException. "Haku-oid, kausi and vuosi are mandatory parameters!")))
  (let [url (resolve-url :tarjonta-service.haku-find-by-hakuvuosi-and-hakukausi vuosi kausi)]
    (get-as-channel url { :as :stream } (filter-only-haku haku-oid))))

(defn haku-for-haku-oid-channel [haku-oid]
  (let [url (resolve-url :tarjonta-service.haku haku-oid)]
    (get-as-channel url { :as :stream } (fn [response] (if-let [some-haku ((parse-json-body-stream response) "result")]
                                            some-haku
                                            (throw (RuntimeException. (format "Haku %s not found!" haku-oid))))))))

(def tilastokeskus-batch-size 5000)

(defn fetch-tilastoskeskus-hakukohde-channel [hakukohde-oids]
  (go-try (let [url (resolve-url :tarjonta-service.tilastokeskus)
                partitions (partition tilastokeskus-batch-size tilastokeskus-batch-size nil hakukohde-oids)
                post (fn [x] (post-json-as-channel url x parse-json-body-stream))
                hakukohteet (<? (async-map vector (map #(post %) partitions)))]
    (apply merge hakukohteet))))

(defn is-haku-with-ensikertalaisuus [haku]
  (if-let [some-haku haku]
    (let [korkeakoulu? (if-let [kuri (haku "kohdejoukkoUri")] (str/starts-with? kuri "haunkohdejoukko_12#") false)
          kohdejoukontarkenne? (some? (haku "kohdejoukonTarkenne"))
          yhteis-tai-erillishaku? (if-let [hakutapa (haku "hakutapaUri")] (or (str/starts-with? hakutapa "hakutapa_01#") (str/starts-with? hakutapa "hakutapa_02#")) false)
          jatkuva-haku? (if-let [hakutapa (haku "hakutapaUri")] (str/starts-with? hakutapa "hakutapa_03#") false)]
      (and korkeakoulu? yhteis-tai-erillishaku? (not kohdejoukontarkenne?)))
    (throw (RuntimeException. "Can't check nil haku if it's jatkuva haku!"))))

(defn is-jatkuva-haku [haku]
  (if-let [some-haku haku]
    (if-let [hakutapa (haku "hakutapaUri")]
      (str/starts-with? hakutapa "hakutapa_03#")
      false)
    (throw (RuntimeException. "Can't check nil haku if it's jatkuva haku!"))))

(defn jatkuvan-haun-hakukohde-oids-for-hakukausi [haku-oid vuosi kausi]
  (go-try (let [haku (<? (haku-for-haku-oid-channel haku-oid))
                jatkuva (is-jatkuva-haku haku)
                hakukohde-oids (if jatkuva (<? (hakukohde-oids-for-kausi-and-vuosi-channel haku-oid kausi vuosi)) [])]
            (if (and jatkuva (empty? hakukohde-oids))
              (throw (RuntimeException. (format "No hakukohde-oids found for 'jatkuva haku' %s with vuosi %s and kausi %s!"
                                                haku-oid vuosi kausi)))
              hakukohde-oids))))