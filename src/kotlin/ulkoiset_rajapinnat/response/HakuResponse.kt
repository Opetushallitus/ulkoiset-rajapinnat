package ulkoiset_rajapinnat.response

data class HakuResponse(
    val hakuOid: String,
    val hakuNimi: Map<String, String>,
    val haunHakukohteidenOidit: List<String>,
    val hakukohteidenPriorisointi: Boolean?,
    val hakuvuosi: Int?,
    val hakukausi: String?,
    val koulutuksenAlkamisvuosi: Int?,
    val koulutuksenAlkamiskausi: String?,
    val hakutyyppiKoodi: String?,
    val hakutapaKoodi: String?,
    val haunKohdejoukko: String?,
    val haunKohdejoukonTarkenne: String?)