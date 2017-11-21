(ns ulkoiset-rajapinnat.odw
  (:require [ulkoiset-rajapinnat.utils.rest :refer [parse-json-request status body-and-close body to-json get-as-promise parse-json-body exception-response post-as-promise]]
            [ulkoiset-rajapinnat.utils.cas :refer [fetch-jsessionid]]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [clojure.tools.logging :as log]
            [manifold.deferred :refer [let-flow catch chain on-realized zip loop]]))

(defn handle-response [url response]
  (log/info (str url " " (response :status)))
  ;(log/info (response :body))
  (case (response :status)
    200 (parse-json-body response)
    404 nil
    (throw (RuntimeException. (str "Calling " url " failed: status=" (response :status) ", msg=" (response :body))))))

(defn post-with-url [session-id url body]
  (log/info url)
  ;(log/info body)
  (let [promise (post-as-promise url {:headers {"Cookie" (str "JSESSIONID=" session-id )}} body)]
    (log/info (str url "(JSESSIONID=" session-id ")"))
    (chain promise #(handle-response url %))))


(def hakukohteet-api "%s/valintaperusteet-service/resources/hakukohde/hakukohteet")
(defn hakukohteet-url [host] (format hakukohteet-api host))
(defn get-hakukohteet [host session-id hakukohde-oids] (post-with-url session-id (hakukohteet-url host) hakukohde-oids))
(def valinnanvaiheet-api "%s/valintaperusteet-service/resources/hakukohde/valinnanvaiheet")
(defn valinnanvaiheet-url [host] (format valinnanvaiheet-api host))
(defn get-valinnanvaiheet [host session-id hakukohde-oids] (post-with-url session-id (valinnanvaiheet-url host) hakukohde-oids))
(def valintatapajonot-api "%s/valintaperusteet-service/resources/valinnanvaihe/valintatapajonot")
(defn valintatapajonot-url [host] (format valintatapajonot-api host))
(defn get-valintatapajonot [host session-id valinnanvaihe-oids] (post-with-url session-id (valintatapajonot-url host) valinnanvaihe-oids))

(defn find-first-matching [match-key match-value collection]
  (first (filter #(= match-value (get % match-key)) collection)))

(defn merge-if-not-nil [collection merge-key merge-collection]
  (if (nil? merge-collection) collection (merge collection {merge-key merge-collection})))

(defn result [all-hakukohteet all-valinnanvaiheet all-valintatapajonot]
  (defn collect-hakukohteen-valinnanvaiheet [hakukohde-oid]
    (def hakukohteen-valinnanvaiheet (get (find-first-matching "hakukohdeOid" hakukohde-oid all-valinnanvaiheet) "valinnanvaiheet"))
    (map (fn [valinnanvaihe]
      (def valinnanvaihe-oid (get valinnanvaihe "oid"))
      (def valinnanvaiheen-valintatapajonot (get (find-first-matching "valinnanvaiheOid" valinnanvaihe-oid all-valintatapajonot) "valintatapajonot"))
      (merge-if-not-nil valinnanvaihe "valintatapajonot" valinnanvaiheen-valintatapajonot)
    ) hakukohteen-valinnanvaiheet))

  (map (fn [hakukohde]
    (def hakukohde-oid (get hakukohde "oid"))
    (def hakukohteen-valinnanvaiheet (collect-hakukohteen-valinnanvaiheet hakukohde-oid))
    (merge-if-not-nil hakukohde "valinnanvaiheet" hakukohteen-valinnanvaiheet)
  ) (filter #(not (nil? %)) all-hakukohteet)))

(defn odw-resource [config request channel]
  (let [host (config :host-virkailija)
        username (config :ulkoiset-rajapinnat-cas-username)
        password (config :ulkoiset-rajapinnat-cas-password)]
    (-> (let-flow [session-id (fetch-jsessionid host "/valintaperusteet-service" username password)
                   hakukohde-oidit (parse-json-request request)
                   hakukohteet (get-hakukohteet host session-id hakukohde-oidit)
                   valinnanvaiheet (get-valinnanvaiheet host session-id hakukohde-oidit)
                   valinnanvaihe-oidit (map #(get % "oid") valinnanvaiheet)
                   valintatapajonot (get-valintatapajonot host session-id valinnanvaihe-oidit)]
                  (let [json (to-json (result hakukohteet valinnanvaiheet valintatapajonot))]
                    (-> channel
                        (status 200)
                        (body-and-close json))))
        (catch Exception (exception-response channel)))
    (schedule-task (* 1000 60 60) (close channel))))