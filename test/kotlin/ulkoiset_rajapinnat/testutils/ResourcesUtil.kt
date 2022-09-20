package ulkoiset_rajapinnat.testutils

import java.io.FileInputStream
import java.io.InputStream

object ResourcesUtil {
    fun resource(path: String): InputStream {
        val resourceAsStream = FileInputStream("test/resources/$path")

        return resourceAsStream
    }
}