package ulkoiset_rajapinnat.response

data class Hakutoive(
    val hyvaksyttyEnsikertalaistenHakijaryhmasta: Boolean,
    val alinHyvaksyttyPistemaara: Double?,
    val osallistuiKielikokeeseen: Boolean?,
    val valintatapajono: String?,
    val osallistuiPaasykokeeseen: Boolean?,
    val ilmoittautumisenTila: String?,
    val vastaanotonTila: String?,
    val hakijanLopullinenJonosija: Int?,
    val hakukohdeOid: String?,
    val yhteispisteet: Double?,
    val hakijanJonosijanTarkenne: Int?,
    val valinnanTila: String?,
    val valinnanTilanLisatieto: String?,
    val hyvaksyttyHarkinnanvaraisesti: Boolean?
)

data class VastaanottoResponse(
    val henkilo_oid: String,
    val hakutoiveet: List<Hakutoive>
)
