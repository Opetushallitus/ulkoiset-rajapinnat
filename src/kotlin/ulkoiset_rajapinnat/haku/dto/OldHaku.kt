package ulkoiset_rajapinnat.haku.dto

data class HakuResultByAlkamisvuosi(val result: List<OldHaku>)

data class HakuResult(val result: OldHaku)

data class OldHaku(
    val oid: String,
    val hakukausiUri: String?,
    val hakukausiVuosi: Int,
    val hakutapaUri: String?,
    val hakulomakeUri: String?,
    val hakutyyppiUri: String?,
    val kohdejoukkoUri: String?,
    val kohdejoukonTarkenne: String?,
    val koulutuksenAlkamisvuosi: Int,
    val koulutuksenAlkamiskausiUri: String?,
    val tila: String,
    val ylioppilastutkintoAntaaHakukelpoisuuden: Boolean,
    val hakukohdeOidsYlioppilastutkintoAntaaHakukelpoisuuden: List<String>,
    val sijoittelu: Boolean,
    val jarjestelmanHakulomake: Boolean,
    val hakuaikas: List<Map<String, Any>>,
    val hakukohdeOids: List<String>,
    val nimi: Map<String, String>,
    val maxHakukohdes: Int,
    val canSubmitMultipleApplications: Boolean,
    val organisaatioOids: List<String>,
    val tarjoajaOids: List<String>,
    val usePriority: Boolean?,
    val sisaltyvatHaut: List<String>,
    val maksumuuriKaytossa: Boolean,
    val tunnistusKaytossa: Boolean,
    val yhdenPaikanSaanto: Map<String, Any>,
    val autosyncTarjonta: Boolean,
    val korkeakouluHaku: Boolean
) {
}