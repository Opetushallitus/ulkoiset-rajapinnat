package ulkoiset_rajapinnat.haku.dto

data class OldKoulutusSearchTulos(val oid: String, val nimi: Map<String, String>, val tulokset: List<OldKoulutusSearch>)
data class KoulutusSearchResultTulokset(val tulokset: List<OldKoulutusSearchTulos>)
data class KoulutusSearchResult(val result: KoulutusSearchResultTulokset)

data class OldKoulutusSearch(
    val oid: String,
    val nimi: Map<String, String>,
    val kausi: Map<String, String>,
    val kausiUri: String,
    val vuosi: Int,
    val tila: String,
    val koulutustyyppiUri: String,
    val koulutusasteTyyppi: String,
    val toteutustyyppiEnum: String,
    val koulutuskoodi: String,
    val tarjoajat: List<String>,
    val koulutusmoduuliTyyppi: String,
    val komoOid: String,
    val opetuskielet: List<String>,
    val koulutuslaji: Map<String, String>
) {
}