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

(def kausi-uri-prefix-kevat "kausi_k")
(def kausi-uri-prefix-syksy "kausi_s")

(defn kevat? [kausi] (let [kausi-l (str/lower-case kausi)] (or (str/starts-with? kausi-l kausi-uri-prefix-kevat) (= kausi-l "k"))))
(defn syksy? [kausi] (let [kausi-l (str/lower-case kausi)] (or (str/starts-with? kausi-l kausi-uri-prefix-syksy) (= kausi-l "s"))))

(defn to-kausi-uri-prefix [kausi]
  (if (kevat? kausi)
    kausi-uri-prefix-kevat
    (if (syksy? kausi)
      kausi-uri-prefix-syksy
      (throw (RuntimeException. (str "Unknown kausi param: " kausi))))))

(defn hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan
  ([haku-oid vuosi kausi] (hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan haku-oid vuosi kausi nil))
  ([haku-oid vuosi kausi valmis-haku]
   (go-try (let [haku (if (nil? valmis-haku) (<? (haku-for-haku-oid-channel haku-oid)) valmis-haku)
                 hakukohde-oids (get haku "hakukohdeOids")
                 hakukohteiden-koulutusten-alkamiskaudet (<? (fetch-tilastoskeskus-hakukohde-channel hakukohde-oids))
                 kausi-uri-prefix (to-kausi-uri-prefix kausi)
                 koulutuksen-alkamiskausi? (fn [x] (if-let [alkamiskausiUri (get x "koulutuksenAlkamiskausiUri")]
                                                     (if-let [alkamisVuosi (get x "koulutuksenAlkamisVuosi")]
                                                       (and (= (str alkamisVuosi) vuosi) (str/starts-with? alkamiskausiUri kausi-uri-prefix))
                                                       false)
                                                     false))]
             (map #(get % "hakukohdeOid") (filter koulutuksen-alkamiskausi? hakukohteiden-koulutusten-alkamiskaudet))))))