package ulkoiset_rajapinnat

import clojure.lang.PersistentArrayMap
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.config.PersistentArrayMapWrapper
import ulkoiset_rajapinnat.haku.HakuClient
import ulkoiset_rajapinnat.koodisto.KoodistoClient
import ulkoiset_rajapinnat.kouta.KoutaInternalClient
import ulkoiset_rajapinnat.kouta.dto.*
import ulkoiset_rajapinnat.ohjausparametrit.OhjausparametritClient
import ulkoiset_rajapinnat.organisaatio.OrganisaatioClient
import ulkoiset_rajapinnat.response.HakuResponse
import ulkoiset_rajapinnat.response.HakukohdeResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.*
import ulkoiset_rajapinnat.util.*

interface HakukohteetForHaku {
    fun findHakukohteetForHaku(hakuOid: String): CompletableFuture<List<HakukohdeResponse>>
}

interface HakuByYear {
    fun findHakuByYear(year: Int): CompletableFuture<List<HakuResponse>>
}

class UlkoisetRajapinnatApi(
    properties: OphProperties,
    username: String,
    password: String,
    clients: Clients = Clients(
        hakuClient = HakuClient(properties),
        koodistoClient = KoodistoClient(properties),
        organisaatioClient = OrganisaatioClient(properties),
        koutaInternalClient = KoutaInternalClient(username, password, properties),
        ohjausparametritClient = OhjausparametritClient(username, password, properties)
    ),
    hakukohteetForHaku: HakukohteetForHakuApi = HakukohteetForHakuApi(clients),
    hakuByYear: HakuByYear = HakuByYearApi(clients)
) : HakukohteetForHaku by hakukohteetForHaku,
    HakuByYear by hakuByYear {

    constructor(
        p: OphProperties,
        c: PersistentArrayMap
    ) : this(
        p,
        username = PersistentArrayMapWrapper(c).read("ulkoiset-rajapinnat-cas-username") as String,
        password = PersistentArrayMapWrapper(c).read("ulkoiset-rajapinnat-cas-password") as String
    )
}




