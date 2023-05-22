package ulkoiset_rajapinnat.valintaperusteet.dto

data class SyotettavanArvonTyyppi(
    val uri: String,
    val nimiFi: String?,
    val nimiSv: String?,
    val nimiEn: String?,
    val arvo: String?

)

data class ValintaperusteDTO(
    val tunniste: String,
    val kuvaus: String,
    val funktiotyyppi: String,
    val lahde: String,
    val onPakollinen: Boolean,
    val min: String,
    val max: String,
    val arvot: Any,
    val osallistuminenTunniste: String,
    val vaatiiOsallistumisen: Boolean,
    val syotettavissaKaikille: Boolean,
    val tilastoidaan: Boolean,
    val syötettavanArvonTyyppi: SyotettavanArvonTyyppi?
) {
    fun isValintakoe() = syötettavanArvonTyyppi?.uri.equals("syotettavanarvontyypit_valintakoe")
    fun isKielikoe() = syötettavanArvonTyyppi?.uri.equals("syotettavanarvontyypit_kielikoe")
}

data class HakukohteenValintaperusteResponse(
    val hakukohdeOid: String,
    val valintaperusteDTO: List<ValintaperusteDTO>
)