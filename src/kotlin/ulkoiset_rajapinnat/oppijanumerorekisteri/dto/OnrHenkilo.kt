package ulkoiset_rajapinnat.oppijanumerorekisteri.dto

//[
//  {
//    "oidHenkilo": "1.2.246.562.24.76784922593",
//    "hetu": "090914A9134",
//    "kaikkiHetut": [],
//    "passivoitu": false,
//    "etunimet": "Samuel Testi",
//    "kutsumanimi": "Samuel",
//    "sukunimi": "Mäkynen-Testi",
//    "aidinkieli": {
//      "kieliKoodi": "fi",
//      "kieliTyyppi": "suomi"
//    },
//    "asiointiKieli": null,
//    "kansalaisuus": [
//      {
//        "kansalaisuusKoodi": "246"
//      }
//    ],
//    "kasittelijaOid": "1.2.246.562.24.66631583590",
//    "syntymaaika": "2014-09-09",
//    "sukupuoli": "2",
//    "kotikunta": null,
//    "oppijanumero": "1.2.246.562.24.76784922593",
//    "turvakielto": false,
//    "eiSuomalaistaHetua": false,
//    "yksiloity": false,
//    "yksiloityVTJ": true,
//    "yksilointiYritetty": true,
//    "duplicate": false,
//    "created": 1411326275578,
//    "modified": 1584058500126,
//    "vtjsynced": null,
//    "yhteystiedotRyhma": [],
//    "yksilointivirheet": [],
//    "passinumerot": [],
//    "kielisyys": [],
//    "henkiloTyyppi": "OPPIJA"
//  }
//]

//{:yksiloity              (get henkilo "yksiloity")
////       :henkilotunnus          (get henkilo "hetu")
////       :syntyma_aika           (get henkilo "syntymaaika")
////       :etunimet               (get henkilo "etunimet")
////       :sukunimi               (get henkilo "sukunimi")
////       :sukupuoli_koodi        (get henkilo "sukupuoli")
////       :aidinkieli             (get henkilo "aidinkieli")
////       :hakijan_kansalaisuudet (mapv fetch-maakoodi-from-koodisto-cache (map #(get % "kansalaisuusKoodi") kansalaisuusKoodit))})
////    {}))

data class Kansalaisuus(
    val kansalaisuusKoodi: String
)

data class Kieli(
    val kieliKoodi: String?,
    val kieliTyyppi: String?
)

data class OnrHenkilo(
    val oidHenkilo: String,
    val hetu: String?,
    val yksiloity: Boolean?,
    val syntymaaika: String?,
    val etunimet: String?,
    val sukunimi: String?,
    val sukupuoli: String?,
    val aidinkieli: Kieli?,
    val kansalaisuus: List<Kansalaisuus>
)
