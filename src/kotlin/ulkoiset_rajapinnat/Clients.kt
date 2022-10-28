package ulkoiset_rajapinnat

import ulkoiset_rajapinnat.haku.HakuClient
import ulkoiset_rajapinnat.koodisto.KoodistoClient
import ulkoiset_rajapinnat.kouta.KoutaInternalClient
import ulkoiset_rajapinnat.ohjausparametrit.OhjausparametritClient
import ulkoiset_rajapinnat.organisaatio.OrganisaatioClient

data class Clients(
    val hakuClient: HakuClient,
    val koodistoClient: KoodistoClient,
    val organisaatioClient: OrganisaatioClient,
    val koutaInternalClient: KoutaInternalClient,
    val ohjausparametritClient: OhjausparametritClient
)
