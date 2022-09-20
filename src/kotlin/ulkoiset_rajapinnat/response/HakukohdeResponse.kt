package ulkoiset_rajapinnat.response

data class HakukohdeResponse(
    val hakukohteenOid: String,
    val organisaatiot: List<OrganisaatioResponse>,
    val hakukohteenNimi: Map<String, String>,
    val koulutuksenOpetuskieli: List<String>,
    val koulutuksenKoulutustyyppi: String?,
    val koulutuksenAlkamisvuosi: Int?,
    val koulutuksenAlkamiskausi: String?,
    val hakukohteenKoulutuskoodit: List<String>,
    val hakukohteenKoulutukseenSisaltyvatKoulutuskoodit: List<String>,
    val hakukohteenKoodi: String?,
    val pohjakoulutusvaatimus: String?,
    val hakijalleIlmoitetutAloituspaikat: Int?,
    val valintojenAloituspaikat: Int?,
    val ensikertalaistenAloituspaikat: Int?) {
}