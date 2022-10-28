package ulkoiset_rajapinnat.testutils

import io.github.infeez.kotlinmockserver.dsl.http.mock
import io.github.infeez.kotlinmockserver.dsl.http.mocks
import io.github.infeez.kotlinmockserver.util.RequestMethod

object CasMocks {
    val casTGT = mock(RequestMethod.POST, "/cas/v1/tickets") {
        code(201)
        headers(Pair("Location", "http://localhost:9999/cas/v1/tickets/TGT"))
        body("")
    }
    val casServiceTicket = mock(RequestMethod.POST,"/cas/v1/tickets/TGT") {
        code(200)
        headers(Pair("Cookie", "session=session"))
        body("")
    }
    val cas = mocks {
        +casTGT
        +casServiceTicket
    }
}