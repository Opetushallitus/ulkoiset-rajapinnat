package ulkoiset_rajapinnat.kouta.dto

data class HakuFull(val oid: String,
                    val nimi: Map<String, String>,
                    val tila: String,
                    val hakutapaKoodiUri: String,  // hakutapa_01#1
                    val hakukohteenLiittamisenTakaraja: String,
                    val ajastettuJulkaisu: String,
                    val kohdejoukkoKoodiUri: String, // haunkohdejoukko_12#1
                    val hakulomaketyyppi: String, // ataru
                    val hakulomakeAtaruId: String,
                    val hakulomakeKuvaus: Map<String, String>,
                    val hakulomakeLinkki: Map<String, String>,
                    val organisaatioOid: String,
                    val hakuajat: List<Map<String, String>>,
                    val kielivalinta: List<String>) {
}