package ulkoiset_rajapinnat

import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import ulkoiset_rajapinnat.kouta.dto.HakuInternal
import ulkoiset_rajapinnat.response.HakuResponse
import ulkoiset_rajapinnat.util.*
import java.util.concurrent.CompletableFuture

class HakuByYearApi(clients: Clients) : HakuByYear {
    private val koutaInternalClient = clients.koutaInternalClient
    private val hakuClient = clients.hakuClient
    private val koodistoClient = clients.koodistoClient
    private val ohjausparametritClient = clients.ohjausparametritClient

    @OptIn(DelicateCoroutinesApi::class)
    override fun findHakuByYear(year: Int): CompletableFuture<List<HakuResponse>> {
        return GlobalScope.future {
            val koutaByVuosi = koutaInternalClient.findByHakuvuosi(year)
            val hakuByVuosi = hakuClient.findHakuByHakuvuosi(year)
            val kieli = koodistoClient.fetchKoodisto("kieli")
            val kausi = koodistoClient.fetchKoodisto("kausi")
            val hakutyyppi = koodistoClient.fetchKoodisto("hakutyyppi")
            val hakutapa = koodistoClient.fetchKoodisto("hakutapa")
            val haunkohdejoukko = koodistoClient.fetchKoodisto("haunkohdejoukko")
            val haunkohdejoukontarkenne = koodistoClient.fetchKoodisto("haunkohdejoukontarkenne")
            val ohjausparametrit = koutaByVuosi.thenCompose {
                ohjausparametritClient.fetchOhjausparametrit(it.map { it.oid }.toSet())
            }
            val hakuResult = hakuByVuosi().result.map { haku ->
                HakuResponse(
                    hakuOid = haku.oid,
                    hakuNimi = haku.nimi.mapKeys { (k, _) -> kieli().arvo(k)!! }
                        .excludeBlankValues,
                    haunHakukohteidenOidit = haku.hakukohdeOids,
                    hakuvuosi = haku.hakukausiVuosi,
                    hakukausi = kausi().arvo(haku.hakukausiUri),
                    koulutuksenAlkamisvuosi = haku.koulutuksenAlkamisvuosi,
                    koulutuksenAlkamiskausi = kausi().arvo(haku.koulutuksenAlkamiskausiUri),
                    hakutyyppiKoodi = hakutyyppi().arvo(haku.hakutyyppiUri),
                    hakutapaKoodi = hakutapa().arvo(haku.hakutapaUri),
                    hakukohteidenPriorisointi = haku.usePriority,
                    haunKohdejoukko = haunkohdejoukko().arvo(haku.kohdejoukkoUri),
                    haunKohdejoukonTarkenne = haunkohdejoukontarkenne().arvo(haku.kohdejoukonTarkenne)
                )
            }
            val koutaResult = koutaByVuosi.await().map { haku: HakuInternal ->
                HakuResponse(
                    hakuOid = haku.oid,
                    hakuNimi = haku.nimi.mapKeys { (k, _) -> kieli().arvo("kieli_$k")!! }
                        .excludeBlankValues,
                    haunHakukohteidenOidit = haku.hakukohdeOids,
                    hakuvuosi = haku.hakuvuosi,
                    hakukausi = kausi().arvo(haku.hakukausi),
                    koulutuksenAlkamisvuosi = haku.alkamisvuosi,
                    koulutuksenAlkamiskausi = kausi().arvo(haku.alkamiskausiKoodiUri),
                    hakutapaKoodi = hakutapa().arvo(haku.hakutapaKoodiUri),
                    hakukohteidenPriorisointi = ohjausparametrit()[haku.oid]?.jarjestetytHakutoiveet,
                    hakutyyppiKoodi = null,
                    haunKohdejoukko = haunkohdejoukko().arvo(haku.kohdejoukkoKoodiUri),
                    haunKohdejoukonTarkenne = haunkohdejoukontarkenne().arvo(haku.kohdejoukonTarkenneKoodiUri)
                )
            }
            hakuResult + koutaResult
        }
    }

}

