package ulkoiset_rajapinnat.ataru.dto

data class HakemuksenHakutoive(
    val hakukohde_oid: String,
    val sija: Int,
    val harkinnanvaraisuuden_syy: String?
)

data class PohjakoulutusKk(
    val pohjakoulutuskklomake: String?,
    val suoritusvuosi: Int?
)

data class Ataruhakemus(
    val asuinmaa: String,
    val pohjakoulutus_kk_ulk_country: String,
    val hakukohde_oids: List<String>,
    val pohjakoulutus_kk: List<PohjakoulutusKk>,
    val pohjakoulutus_2aste_suorituskieli: String?,
    val pohjakoulutus_2aste: String?,
    val pohjakoulutus_2aste_lahtokoulu_oid: String?,
    val hakemus_oid: String,
    val henkilo_oid: String,
    val hakemus_tila: String?,
    val kotikunta: String?,
    val hakutoiveet: List<HakemuksenHakutoive>,
    val haku_oid: String
)
