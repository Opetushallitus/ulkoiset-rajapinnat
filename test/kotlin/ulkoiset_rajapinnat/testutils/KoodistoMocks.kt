package ulkoiset_rajapinnat.testutils

import io.github.infeez.kotlinmockserver.dsl.http.mock
import io.github.infeez.kotlinmockserver.dsl.http.mocks
import ulkoiset_rajapinnat.testutils.ResourcesUtil.resource
import java.io.FileInputStream
import java.io.InputStream

object KoodistoMocks {
    val kausi = mock("/koodisto-service/rest/codeelement/codes/kausi/1") {
        body(resource("koodisto/kausi1.json"))
    }
    val hakutapa = mock("/koodisto-service/rest/codeelement/codes/hakutapa/1") {
        body(resource("koodisto/hakutapa1.json"))
    }
    val hakutyyppi = mock("/koodisto-service/rest/codeelement/codes/hakutyyppi/1") {
        body(resource("koodisto/hakutyyppi1.json"))
    }
    val haunkohdejoukko = mock("/koodisto-service/rest/codeelement/codes/haunkohdejoukko/1") {
        body(resource("koodisto/haunkohdejoukko1.json"))
    }
    val haunkohdejoukontarkenne = mock("/koodisto-service/rest/codeelement/codes/haunkohdejoukontarkenne/1") {
        body(resource("koodisto/haunkohdejoukontarkenne1.json"))
    }
    val kieli = mock("/koodisto-service/rest/codeelement/codes/kieli/1") {
        body(resource("koodisto/kieli1.json"))
    }
    val koulutustyyppi = mock("/koodisto-service/rest/codeelement/codes/koulutustyyppi/1") {
        body(resource("koodisto/koulutustyyppi1.json"))
    }
    val opetuskieli = mock("/koodisto-service/rest/codeelement/codes/oppilaitoksenopetuskieli/2") {
        body(resource("koodisto/oppilaitoksenopetuskieli2.json"))
    }

    val koodistot = mocks {
        +kausi
        +hakutapa
        +hakutyyppi
        +haunkohdejoukko
        +haunkohdejoukontarkenne
        +kieli
        +koulutustyyppi
        +opetuskieli
    }
}