package ulkoiset_rajapinnat.kouta.dto

data class ToteutusInternal(
    val oid: String,
    val koulutusOid: String,
    val tila: String,
    val tarjoajat: List<String>,
    val nimi: Map<String, String>,
    val metadata: Map<String, Any>,
    val muokkaaja: String,
    val organisaatioOid: String,
    val kielivalinta: List<String>,
    val modified: String
)