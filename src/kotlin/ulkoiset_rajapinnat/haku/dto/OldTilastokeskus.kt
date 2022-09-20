package ulkoiset_rajapinnat.haku.dto
data class Laajuusarvo(
    val oid: String,
    val koulutustyyppi: String?,
    val koulutuskoodi: String,
    val opintojenLaajuusarvo: String
)
data class OldTilastokeskus(
    val koulutuksenAlkamiskausiUri: String,
    val koulutuksenAlkamisvuosi: Int,
    val hakukohdeOid: String,
    val tarjoajaOid: String,
    val oppilaitosKoodi: String,
    val koulutusLaajuusarvos: List<Laajuusarvo>
)