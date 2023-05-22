package ulkoiset_rajapinnat.oppijanumerorekisteri.dto

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
