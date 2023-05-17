package ulkoiset_rajapinnat

import clojure.lang.PersistentArrayMap
import com.google.javascript.refactoring.Matchers.constructor
import fi.vm.sade.properties.OphProperties
import ulkoiset_rajapinnat.ataru.AtaruClient
import ulkoiset_rajapinnat.config.PersistentArrayMapWrapper
import ulkoiset_rajapinnat.haku.HakuClient
import ulkoiset_rajapinnat.koodisto.KoodistoClient
import ulkoiset_rajapinnat.kouta.KoutaInternalClient
import ulkoiset_rajapinnat.kouta.dto.*
import ulkoiset_rajapinnat.ohjausparametrit.OhjausparametritClient
import ulkoiset_rajapinnat.oppijanumerorekisteri.OppijanumerorekisteriClient
import ulkoiset_rajapinnat.organisaatio.OrganisaatioClient
import ulkoiset_rajapinnat.response.HakemusResponse
import ulkoiset_rajapinnat.response.HakuResponse
import ulkoiset_rajapinnat.response.HakukohdeResponse
import ulkoiset_rajapinnat.response.VastaanottoResponse
import ulkoiset_rajapinnat.suoritusrekisteri.SuoritusrekisteriClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.*
import ulkoiset_rajapinnat.util.*
import ulkoiset_rajapinnat.valinta_tulos_service.ValintaTulosServiceClient
import ulkoiset_rajapinnat.valintaperusteet.ValintaperusteetClient
import ulkoiset_rajapinnat.valintapisteet.ValintapisteClient

interface HakukohteetForHaku {
    fun findHakukohteetForHaku(hakuOid: String): CompletableFuture<List<HakukohdeResponse>>
}

interface HakuByYear {
    fun findHakuByYear(year: Int): CompletableFuture<List<HakuResponse>>
}

interface VastaanottoForHaku {
    fun findVastaanototForHaku(hakuOid: String, vuosi: String, kausi: String): CompletableFuture<List<VastaanottoResponse>>
    fun findVastaanototForHakukohteet(hakuOid: String, hakukohdeOids: List<String>): CompletableFuture<List<VastaanottoResponse>>
}

interface HakemusForHaku {
    fun findHakemuksetForHaku(hakuOid: String, vuosi: String, kausi: String): CompletableFuture<List<HakemusResponse>>
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
        ohjausparametritClient = OhjausparametritClient(username, password, properties),
        valintaTulosServiceClient = ValintaTulosServiceClient(username, password, properties),
        valintaperusteetClient = ValintaperusteetClient(username, password, properties),
        valintapisteClient = ValintapisteClient(username, password, properties),
        suoritusrekisteriClient = SuoritusrekisteriClient(username, password, properties),
        ataruClient = AtaruClient(username, password, properties),
        oppijanumerorekisteriClient = OppijanumerorekisteriClient(username, password, properties)
    ),
    hakukohteetForHaku: HakukohteetForHakuApi = HakukohteetForHakuApi(clients),
    hakuByYear: HakuByYear = HakuByYearApi(clients),
    vastaanottoForHaku: VastaanottoForHaku = VastaanottoForHakuApi(clients),
    hakemusForHaku: HakemusForHaku = HakemusForHakuApi(clients)
) : HakukohteetForHaku by hakukohteetForHaku,
    HakuByYear by hakuByYear,
    VastaanottoForHaku by vastaanottoForHaku ,
    HakemusForHaku by hakemusForHaku {
    constructor(
        p: OphProperties,
        c: PersistentArrayMap
    ) : this(
        p,
        username = PersistentArrayMapWrapper(c).read("ulkoiset-rajapinnat-cas-username") as String,
        password = PersistentArrayMapWrapper(c).read("ulkoiset-rajapinnat-cas-password") as String
    )
}




