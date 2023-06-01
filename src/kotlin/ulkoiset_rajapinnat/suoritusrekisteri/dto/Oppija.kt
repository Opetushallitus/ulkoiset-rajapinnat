package ulkoiset_rajapinnat.suoritusrekisteri.dto

data class Opiskeluoikeus(
    val alkuPaiva: String?,
    val loppuPaiva: String?,
    val henkiloOid: String,
    val id: String?,
    val myontaja: String?,
    val komo: String?
)

data class Luokkatieto(
    val alkuPaiva: String?,
    val loppuPaiva: String?,
    val oppilaitosOid: String?,
    val luokka: String?,
    val henkiloOid: String?,
    val id: String?,
    val luokkataso: String?
)

data class Suoritus(
    val tila: String?,
    val yksilollistaminen: String?,
    val henkiloOid: String?,
    val vahvistettu: Boolean?,
    val suorituskieli: String?,
    val id: String?,
    val myontaja: String?,
    val lahdeArvot: Map<String, String>?,
    val komo: String?,
    val valmistuminen: String?
)
data class Arvosana(
    val lisatieto: String?,
    val valinnaninen: Boolean?,
    val jarjestys: Int?,
    val id: String?,
    val lahdeArvot: Map<String, String>?,
    val arvio: Any?, //fixme
    val suoritus: String?,
    val myonnetty: String?,
    val aine: String?
)
data class SuoritusJaArvosanat(
    val suoritus: Suoritus,
    val arvosanat: List<Arvosana>
)
data class Oppija(
    val oppijanumero: String,
    val opiskelu: List<Luokkatieto>,
    val opiskeluoikeudet: List<Opiskeluoikeus>,
    val ensikertalainen: Boolean?,
    val suoritukset: List<SuoritusJaArvosanat>
)

//{
//    "henkiloOid": "1.2.246.562.24.61961728774"
//  },
//  {
//    "henkiloOid": "1.2.246.562.24.45637322624",
//    "menettamisenPeruste": {
//      "peruste": "OpiskeluoikeusAlkanut",
//      "paivamaara": "2021-08-30T00:00:00.000+03:00"
//    }
//  },

data class MenettamisenPeruste(
    val peruste: String?,
    val paivamaara: String?
)
data class Ensikertalaisuus(
    val henkiloOid: String,
    val menettamisenPeruste: MenettamisenPeruste?
)