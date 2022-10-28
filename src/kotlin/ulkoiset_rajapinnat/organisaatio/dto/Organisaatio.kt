package ulkoiset_rajapinnat.organisaatio.dto

import org.apache.commons.lang3.StringUtils

data class Organisaatio(
    val status: String,
    val oid: String,
    val piilotettu: Boolean,
    val yhteishaunKoulukoodi: String,
    val kieletUris: List<String>,
    val oppilaitosKoodi: String?,
    val ytunnus: String?,
    val kotipaikkaUri: String?,
    val parentOid: String,
    val parentOidPath: String,
    val toimipistekoodi: String,
    val kayntiosoite: Map<String, String>,
    val lyhytNimi: Map<String, String>,
    val postiosoite: Map<String, String>,
    val alkuPvm: String,
    val nimi: Map<String, String>,
    val nimet: List<Map<String, Any>>,
    val opetuspisteenJarjNro: String,
    val lisatiedot: List<*>,
    val kuvaus2: Map<*, *>,
    val muutKotipaikatUris: List<*>,
    val muutOppilaitosTyyppiUris: List<*>,
    val tyypit: List<String>,
    val vuosiluokat: List<*>,
    val ryhmatyypit: List<*>,
    val kayttoryhmat: List<*>,
    val yhteystietoArvos: List<*>,
    val yhteystiedot: List<Map<String, String>>
) {
    fun parentOids(): Set<String> {
        return parentOidPath.split("\\|".toRegex()).filter(StringUtils::isNotEmpty).toSet()
    }

}
