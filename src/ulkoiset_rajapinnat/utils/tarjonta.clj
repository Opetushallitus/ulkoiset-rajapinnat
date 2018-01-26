(ns ulkoiset-rajapinnat.utils.tarjonta
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clojure.core.async :refer [<! promise-chan >! go put! close!]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.config :refer :all]
            [ulkoiset-rajapinnat.utils.rest :refer :all]))

(defn- haku-alkamiskaudella-api
  [host vuosi kausi]
  (format "%s/tarjonta-service/rest/v1/haku/find?HAKUVUOSI=%s&HAKUKAUSI=%s&TILA=NOT_POISTETTU&TARJOAJAOID=1.2.246.562.10.00000000001&addHakukohdes=true" host vuosi kausi))

(defn- haku-api [host haku-oid]
  (format "%s/tarjonta-service/rest/v1/haku/%s" host haku-oid))

(defn- filter-only-haku [haku-oid]
  (fn [response]
    (let [data ((parse-json-body response) "result")
          data-with-haku-and-hakukohde (map #(select-keys % ["oid" "hakukohdeOids"]) data)]
      (mapcat #(% "hakukohdeOids") (filter #(= haku-oid (% "oid")) data-with-haku-and-hakukohde)))))

(defn hakukohde-oids-for-kausi-and-vuosi-channel [config haku-oid kausi vuosi]
  (if (not (every? some? [kausi vuosi haku-oid]))
    (throw (RuntimeException. "Haku-oid, kausi and vuosi are mandatory parameters!")))
  (let [url (haku-alkamiskaudella-api (config :host-virkailija) vuosi kausi)]
    (get-as-channel url {} (filter-only-haku haku-oid))))

(defn haku-for-haku-oid-channel [config haku-oid]
  (let [url (haku-api (config :host-virkailija) haku-oid)]
    (get-as-channel url {} (fn [response] (if-let [some-haku ((parse-json-body response) "result")]
                                            some-haku
                                            (throw (RuntimeException. (format "Haku %s not found!" haku-oid))))))))

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