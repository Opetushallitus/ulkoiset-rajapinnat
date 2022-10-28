(ns ulkoiset-rajapinnat.utils.tarjonta
  (:require [clojure.string :as str]
            [full.async :refer :all]
            [clojure.core.async :refer [<! promise-chan >! go put! close!]]
            [clojure.tools.logging :as log]
            [ulkoiset-rajapinnat.utils.url-helper :refer [resolve-url]]
            [ulkoiset-rajapinnat.utils.snippets :refer [is-valid-year]]
            [ulkoiset-rajapinnat.utils.rest :refer :all]
            [ulkoiset-rajapinnat.utils.async_safe :refer :all]))

(defn haku-for-haku-oid-channel [haku-oid]
  (let [url (resolve-url :tarjonta-service.haku haku-oid)]
    (get-as-channel url { :as :stream } (fn [response] (if-let [haku ((parse-json-body-stream response) "result")]
                                                         haku
                                                         {})))))

(def hakukohde-batch-size 500)

(defn fetch-hakukohteet-channel [hakukohde-oids]
  (log/info "Fetching hakukohde data from tarjonta tilastokeskus endpoint for " (if (nil? hakukohde-oids) 0 (count hakukohde-oids)) " hakukohde!")
  (go-try (let [url (resolve-url :tarjonta-service.tilastokeskus)
                partitions (partition hakukohde-batch-size hakukohde-batch-size nil hakukohde-oids)
                post (fn [x] (post-json-as-channel url x parse-json-body-stream))
                hakukohteet (<? (async-map-safe vector (map #(post %) partitions) []))]
               (apply merge hakukohteet))))

(defn is-haku-with-ensikertalaisuus [haku]
  (if haku
    (let [korkeakoulu? (if-let [kuri (haku "kohdejoukkoUri")] (str/starts-with? kuri "haunkohdejoukko_12#") false)
          kohdejoukontarkenne? (some? (haku "kohdejoukonTarkenne"))
          yhteis-tai-erillishaku? (if-let [hakutapa (haku "hakutapaUri")] (or (str/starts-with? hakutapa "hakutapa_01#") (str/starts-with? hakutapa "hakutapa_02#")) false)]
      (and korkeakoulu? yhteis-tai-erillishaku? (not kohdejoukontarkenne?)))
    (throw (RuntimeException. "Can't check nil haku if it's jatkuva haku!"))))

(defn is-toinen-aste [haku]
  (if haku
    (if-let [kuri (haku "kohdejoukkoUri")]
      (or (str/starts-with? kuri "haunkohdejoukko_11#") (str/starts-with? kuri "haunkohdejoukko_17#") (str/starts-with? kuri "haunkohdejoukko_20#"))
      false)
    (throw (RuntimeException. "Can't check nil haku if it's toisen asteen haku!"))))

(defn is-yhteishaku [haku]
  (if haku
    (if-let [hakutapa (haku "hakutapaUri")]
      (str/starts-with? hakutapa "hakutapa_01#")
      false)
    (throw (RuntimeException. "Can't check nil haku if it's yhteishaku!"))))

(def kausi-uri-prefix-kevat "kausi_k")
(def kausi-uri-prefix-syksy "kausi_s")

(defn kevat? [kausi] (let [kausi-l (str/lower-case kausi)] (or (str/starts-with? kausi-l kausi-uri-prefix-kevat) (= kausi-l "k"))))
(defn syksy? [kausi] (let [kausi-l (str/lower-case kausi)] (or (str/starts-with? kausi-l kausi-uri-prefix-syksy) (= kausi-l "s"))))

(defn to-kausi-uri-prefix [kausi]
  (if (kevat? kausi)
    kausi-uri-prefix-kevat
    (if (syksy? kausi)
      kausi-uri-prefix-syksy
      (throw (IllegalArgumentException. (str "Unknown kausi param: " kausi))))))

(defn- has-koulutuksen-alkamiskausi [haku vuosi kausi]
  (and (= (str (get haku "koulutuksenAlkamisVuosi")) vuosi)
       (str/starts-with? (get haku "koulutuksenAlkamiskausiUri") (to-kausi-uri-prefix kausi))))

(defn- hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan-yhteishaulle [yhteishaku vuosi kausi]
  (log/info "Haku" (get yhteishaku "oid") "(" (get-in yhteishaku ["nimi" "kieli_fi"])
            ") on yhteishaku, joten sen alkamiskausikohtaisten hakukohteiden listaus on helppoa.")
  (if (has-koulutuksen-alkamiskausi yhteishaku vuosi kausi)
    (get yhteishaku "hakukohdeOids")
    []))

(defn- has-koulutuksen-alkamiskausi? [alkamisvuosi alkamiskausi hakukohde]
  (let [kausi-uri-prefix (to-kausi-uri-prefix alkamiskausi)]
    (if-let [alkamiskausiUri (get hakukohde "koulutuksenAlkamiskausiUri")]
      (if-let [alkamisVuosi (get hakukohde "koulutuksenAlkamisVuosi")]
        (and (= (str alkamisVuosi) alkamisvuosi) (str/starts-with? alkamiskausiUri kausi-uri-prefix))
        false)
      false)))

(defn hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan
  ([haku-oid vuosi kausi] (hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan haku-oid vuosi kausi nil))
  ([haku-oid vuosi kausi valmis-haku]
   (when (not (is-valid-year vuosi))
     (throw (IllegalArgumentException. (str "Invalid vuosi: " vuosi))))
   (go-try (let [haku (if (nil? valmis-haku) (<? (haku-for-haku-oid-channel haku-oid)) valmis-haku)
                 hakukohde-oids (get haku "hakukohdeOids")]
             (if (is-yhteishaku haku)
               (hakukohde-oidit-koulutuksen-alkamiskauden-ja-vuoden-mukaan-yhteishaulle haku vuosi kausi)
               (let [hakukohteet (<? (fetch-hakukohteet-channel hakukohde-oids))
                     koulutuksen-alkamiskausi? (partial has-koulutuksen-alkamiskausi? vuosi kausi)]
                 (map #(get % "hakukohdeOid") (filter koulutuksen-alkamiskausi? hakukohteet))))))))
