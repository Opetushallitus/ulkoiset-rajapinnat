package ulkoiset_rajapinnat.config

import io.netty.handler.codec.http.cookie.Cookie
import io.netty.handler.codec.http.cookie.DefaultCookie
import org.asynchttpclient.RequestBuilder
import java.util.List

object Headers {
    const val CALLER_ID = "1.2.246.562.10.00000000001.ulkoiset-rajapinnat"
    const val CSRF = "1.2.246.562.10.00000000001.ulkoiset-rajapinnat"
    fun requestBuilderWithHeaders(): RequestBuilder {
        return RequestBuilder()
            .setHeader("CSRF", CSRF)
            .setHeader("Caller-Id", CALLER_ID)
            .setHeader("clientSubSystemCode", CALLER_ID)
            .setCookies(List.of<Cookie>(DefaultCookie("CSRF", CALLER_ID)))
    }
}
