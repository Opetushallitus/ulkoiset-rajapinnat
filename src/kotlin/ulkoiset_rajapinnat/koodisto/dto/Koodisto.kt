package ulkoiset_rajapinnat.koodisto.dto

data class Metadata(val nimi: String,
                    val lyhytNimi: String,
                    val kieli: String)

data class Koodisto(val koodiUri: String,
                    val metadata: List<Metadata>,
                    val version: Int,
                    val koodiArvo: String) {
}