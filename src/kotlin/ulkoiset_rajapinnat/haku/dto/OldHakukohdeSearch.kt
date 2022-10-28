package ulkoiset_rajapinnat.haku.dto

data class OldSearchTulos(val oid: String, val nimi: Map<String, String>, val tulokset: List<OldHakukohdeSearch>)
data class HakukohdeSearchResultTulokset(val tulokset: List<OldSearchTulos>)
data class HakukohdeSearchResult(val result: HakukohdeSearchResultTulokset)

data class OldHakukohdeSearch(
    val oid: String,
    val nimi: Map<String, String>,
    val kausi: Map<String, String>,
    val vuosi: Int,
    val tila: String,
    val koulutusasteTyyppi: String,
    val toteutustyyppiEnum: String,
    val pohjakoulutusvaatimus: Map<String, String>,
    val koulutusmoduuliTyyppi: String,
    val opetuskielet: List<String>,
    val hakutapa: Map<String, String>,
    val hakuaikaRyhma: String,
    val aloituspaikat: Int,
    val valintojenAloituspaikat: Int,
    val hakuOid: String,
    val koodistoNimi: String,
    val ryhmaliitokset: List<Map<String, String>>,
    val ensikertalaistenAloituspaikat: Int,
    val koulutuslaji: Map<String, String>
    )