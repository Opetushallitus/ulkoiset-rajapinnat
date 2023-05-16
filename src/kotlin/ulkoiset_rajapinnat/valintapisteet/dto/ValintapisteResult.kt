package ulkoiset_rajapinnat.valintapisteet.dto

data class Valintapiste(
    val tunniste: String,
    val arvo: Any,
    val osallistuminen: String,
    val tallettaja: String
) {
    fun osallistuiValintakokeeseen(kokeenTunniste: String): Boolean {
        return tunniste == kokeenTunniste && osallistuminen == "OSALLISTUI"
    }
}

data class HakemuksenValintapisteet(
    val hakemusOID: String,
    val sukunimi: String,
    val etunimi: String,
    val oppijaOID: String,
    val pisteet: List<Valintapiste>
)
