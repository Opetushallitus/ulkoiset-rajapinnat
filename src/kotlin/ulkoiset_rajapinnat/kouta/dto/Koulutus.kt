package ulkoiset_rajapinnat.kouta.dto

data class KoulutusInternalMetadata(
    val tyyppi: String,
    val kuvaus: Map<String, String>,
    val lisatiedot: List<Any>,
    val koulutusalaKoodiUrit: List<String>,
    val tutkintonimikeKoodiUrit: List<String>,
    val opintojenLaajuusKodiUri: String // opintojenlaajuus_210#1
)

data class KoulutusInternal(
    val oid: String,
    val johtaaTutkintoon: Boolean,
    val koulutustyyppi: String, // amk
    val koulutusKoodiUrit: List<String>, // koulutus_671101#12
    val tila: String,
    val tarjoajat: List<String>,
    val nimi: Map<String, String>,
    val metadata: KoulutusInternalMetadata,
    val julkinen: Boolean,
    val sorakuvausId: String,
    val muokkaaja: String,
    val organisaatioOid: String,
    val kielivalinta: List<String>,
    val modified: String
)