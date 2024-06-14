package ulkoiset_rajapinnat.response

import ulkoiset_rajapinnat.ataru.dto.HakemuksenHakutoive
data class HakemusResponse(
    val yksiloity: Boolean?,
    val henkilotunnus: String?,
    val syntymaAika: String?,
    val etunimet: String?,
    val sukunimi: String?,
    val sukupuoliKoodi: String?,
    val aidinkieli: String?,
    val hakijanKansalaisuudet: List<String>,
    val hakemusOid: String,
    val henkiloOid: String,
    val hakuOid: String,
    val hakemusTila: String?,
    val ensikertalaisuus: Boolean?,
    val hakutoiveet: List<HakemuksenHakutoive>,
    val hakijanAsuinmaa: String?,
    val hakijanKotikunta: String?,
    val pohjakoulutus_kk: List<String>,
    val ulkomaillaSuoritetunToisenAsteenTutkinnonSuoritusmaa: String?,
    val koulusivistyskieli: String?,
    val pohjakoulutus2aste: String?,
    val lahtokoulunOrganisaatioOid: String?

)
