package ulkoiset_rajapinnat.response

data class OrganisaatioResponse(
    val organisaationOid: String,
    val koulutustoimijanYtunnus: String?,
    val oppilaitosKoodi: String?,
    val organisaationKuntakoodi: String?,
    val organisaationNimi: Map<String, String>) {
}