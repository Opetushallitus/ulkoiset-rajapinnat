package ulkoiset_rajapinnat.ataru.dto

//[
//  {
//    "asuinmaa": "string",
//    "pohjakoulutus_kk_ulk_country": "string",
//    "hakukohde_oids": [
//      "string"
//    ],
//    "pohjakoulutus_kk": [
//      {
//        "pohjakoulutuskklomake": "string",
//        "suoritusvuosi": 0
//      }
//    ],
//    "hakemus_oid": "string",
//    "henkilo_oid": "string",
//    "hakemus_tila": "string",
//    "haku_oid": "string",
//    "kotikunta": "string",
//    "hakutoiveet": [
//      {
//        "hakukohde_oid": "string",
//        "sija": 0
//      }
//    ]
//  }
//]

data class HakemuksenHakutoive(
    val hakukohde_oid: String,
    val sija: Int,
    val harkinnanvarainenValinta: String?
)

data class PohjakoulutusKk(
    val pohjakoulutuskklomake: String?,
    val suoritusvuosi: Int?
)

data class Ataruhakemus(
    val asuinmaa: String,
    val pohjakoulutus_kk_ulk_country: String,
    val hakukohde_oids: List<String>,
    val pohjakoulutus_kk: List<PohjakoulutusKk>,
    val hakemus_oid: String,
    val henkilo_oid: String,
    val hakemus_tila: String?,
    val kotikunta: String?,
    val hakutoiveet: List<HakemuksenHakutoive>,
    val haku_oid: String
)
