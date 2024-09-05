package ulkoiset_rajapinnat.kouta.dto

data class KoulutuksenAlkamiskausi(
    val koulutuksenAlkamiskausi: Koodi,
    val koulutuksenAlkamisvuosi: Int
)

data class Haku(
    val oid: String,
    val nimi: Map<String, String>,
    val organisaatio: Map<String, Any>,
    val tila: String,
    val koulutuksenAlkamiskausi: KoulutuksenAlkamiskausi,
    val hakutapa: Koodi
) {

}

data class Hakuaika(val alkaa: String, val paattyy: String?)
data class Valintakokeet(
    val id: String,
    val tyyppi: String,
    val vahimmaispisteet: Int?,
    val tilaisuudet: List<Map<String, String>>
)

data class HakuInternal(
    val oid: String,
    val tila: String,
    val nimi: Map<String, String>,
    val hakukohdeOids: List<String>,
    val hakutapaKoodiUri: String, // "hakutapa_03#1",
    val hakukohteenLiittamisenTakaraja: String, // "2019-08-23T09:55",
    val hakukohteenMuokkaamiseenTakaraja: String, //"2019-08-23T09:55",
    val ajastettuJulkaisu: String, //"2019-08-23T09:55",
    val alkamiskausiKoodiUri: String?, // "kausi_k#1",
    val alkamisvuosi: Int, //2020
    val hakuvuosi: Int?,
    val hakukausi: String?,
    val kohdejoukkoKoodiUri: String, //"haunkohdejoukko_17#1",
    val kohdejoukonTarkenneKoodiUri: String, //"haunkohdejoukontarkenne_1#1",
    val hakulomaketyyppi: String, // ataru
    val hakulomakeAtaruId: String, // "ea596a9c-5940-497e-b5b7-aded3a2352a7",
    val hakulomakeKuvaus: Map<String, String>,
    val hakulomakeLinkki: Map<String, String>,
    val hakuajat: List<Hakuaika>,
    val valintakokeet: List<Valintakokeet>,
    val metadata: Map<String, Any>,
    val kielivalinta: List<String>,
    val muokkaaja: String,
    val organisaatioOid: String,
    val externalId: String
) {
    fun isKkHaku(): Boolean {
        return kohdejoukkoKoodiUri.split("#").first().equals("haunkohdejoukko_12")
    }
    fun isYhteishaku(): Boolean {
        return hakutapaKoodiUri.split("#").first().equals("hakutapa_01")
    }

    fun is2Aste(): Boolean {
        return kohdejoukkoKoodiUri.split("#").first().equals("haunkohdejoukko_11")
    }

    fun isErillishaku(): Boolean {
        return hakutapaKoodiUri.split("#").first().equals("hakutapa_02")
    }
}
