package ulkoiset_rajapinnat.haku.dto

data class HakukohdeTulosResult(val tulokset: List<OldHakukohdeTulos>)

data class OldHakukohdeTulos(
    val hakukohdeOid: String,
    val tarjoajaOid: String,
    val tarjoajaNimi: Map<String, String>,
    val hakukohdeNimi: Map<String, String>,
    val hakukohdeTila: String,
    val hakuVuosi: Int,
    val koulutusVuosi: Int,
    val organisaatioOids: List<String>,
    val opetuskielet: List<String>,
    val koulutusOids: List<String>
    )
