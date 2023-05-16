package ulkoiset_rajapinnat

import ulkoiset_rajapinnat.haku.HakuClient
import ulkoiset_rajapinnat.koodisto.KoodistoClient
import ulkoiset_rajapinnat.kouta.KoutaInternalClient
import ulkoiset_rajapinnat.ohjausparametrit.OhjausparametritClient
import ulkoiset_rajapinnat.organisaatio.OrganisaatioClient
import ulkoiset_rajapinnat.valinta_tulos_service.ValintaTulosServiceClient
import ulkoiset_rajapinnat.valintaperusteet.ValintaperusteetClient
import ulkoiset_rajapinnat.valintapisteet.ValintapisteClient

data class Clients(
    val hakuClient: HakuClient,
    val koodistoClient: KoodistoClient,
    val organisaatioClient: OrganisaatioClient,
    val koutaInternalClient: KoutaInternalClient,
    val ohjausparametritClient: OhjausparametritClient,
    val valintaTulosServiceClient: ValintaTulosServiceClient,
    val valintaperusteetClient: ValintaperusteetClient,
    val valintapisteClient: ValintapisteClient
)
