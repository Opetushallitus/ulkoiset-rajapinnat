package ulkoiset_rajapinnat.kouta.dto

data class Hakukohde(val oid: String,
                     val toteutusOid: String,
                     val hakuOid: String,
                     val valintaperusteId: String,
                     val nimi: Map<String, String>,
                     val tila: String,
                     val jarjestyspaikkaOid: String,
                     val organisaatioOid: String)

data class PaateltyAlkamiskausi(
    val alkamiskausityyppi: String?,
    val source: String?,
    val kausiUri: String?,
    val vuosi: Int?
)

data class HakukohdeKoodi(val koodiUri: String) {}

data class HakukohdeInternal(
    val oid: String,
    val toteutusOid: String,
    val hakuOid: String,
    val tila: String,
    val aloituspaikat: Int?,
    val ensikertalaisenAloituspaikat: Int?,
    val nimi: Map<String, String>,
    val kaytetaanHaunAlkamiskautta: Boolean,
    val hakulomaketyyppi: String,
    val hakulomakeAtaruId: String,
    val hakulomakeKuvaus: Map<String, String>,
    val hakulomakeLinkki: Map<String, String>,
    val kaytetaanHaunHakulomaketta: Boolean,
    val painotetutArvosanat: Any,
    val pohjakoulutusvaatimusKoodiUrit: List<String>,
    val muuPohjakoulutusvaatimus: Any,
    val toinenAsteOnkoKaksoistutkinto: Boolean,
    val kaytetaanHaunAikataulua: Boolean,
    val valintaperusteId: String,
    val valintaperusteValintakokeet: Any,
    val yhdenPaikanSaanto: Map<String, Any>,
    val salliikoHakukohdeHarkinnanvaraisuudenKysymisen: Boolean,
    val voikoHakukohteessaOllaHarkinnanvaraisestiHakeneita: Boolean,
    val liitteetOnkoSamaToimitusaika: Boolean,
    val liitteetOnkoSamaToimitusosoite: Boolean,
    val liitteet: List<Map<String, Any>>,
    val valintakokeet: Any,
    val hakuajat: Any,
    val muokkaaja: String,
    val tarjoaja: String,
    val organisaatioOid: String,
    val organisaatioNimi: Map<String, String>,
    val kielivalinta: List<String>,
    val modified: String,
    val oikeusHakukohteeseen: Boolean,
    val externalId: String,
    val uudenOpiskelijanUrl: Any,
    val paateltyAlkamiskausi: PaateltyAlkamiskausi,
    val jarjestyspaikkaOid: String,
    val hakukohde: HakukohdeKoodi?,
)