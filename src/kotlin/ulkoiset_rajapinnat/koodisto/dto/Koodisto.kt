package ulkoiset_rajapinnat.koodisto.dto

data class Metadata(val nimi: String,
                    val lyhytNimi: String,
                    val kieli: String)

data class CodeElement(val codeElementUri: String,
                       val codeElementVersion: Int,
                       val passive: Boolean){
}

data class Koodisto(val koodiUri: String,
                    val metadata: List<Metadata>,
                    val version: Int,
                    val koodiArvo: String,
                    val withinCodeElements: List<CodeElement>?,
                    val levelsWithCodeElements: List<CodeElement>?) {
}