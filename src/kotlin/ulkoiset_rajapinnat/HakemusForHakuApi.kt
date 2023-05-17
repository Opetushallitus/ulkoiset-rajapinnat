package ulkoiset_rajapinnat

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import ulkoiset_rajapinnat.ataru.dto.Ataruhakemus
import ulkoiset_rajapinnat.kouta.dto.HakuInternal
import ulkoiset_rajapinnat.oppijanumerorekisteri.dto.OnrHenkilo
import ulkoiset_rajapinnat.response.HakemusResponse
import ulkoiset_rajapinnat.suoritusrekisteri.dto.Oppija
import java.util.concurrent.CompletableFuture

class HakemusForHakuApi(clients: Clients) : HakemusForHaku {
    private val koutaInternalClient = clients.koutaInternalClient
    private val vtsClient = clients.valintaTulosServiceClient
    private val valintaperusteetClient = clients.valintaperusteetClient
    private val valintapisteClient = clients.valintapisteClient
    private val ataruClient = clients.ataruClient
    private val sureClient = clients.suoritusrekisteriClient
    private val onrClient = clients.oppijanumerorekisteriClient

    private val logger = LoggerFactory.getLogger("HakemusForHakuApi")

    private fun createHakemusResponse(hakemus: Ataruhakemus, oppija: Oppija?, onrHenkilo: OnrHenkilo?, ): HakemusResponse {
        if (onrHenkilo == null) {
            logger.warn("Hakemuksen ${hakemus.hakemus_oid} henkilö ${hakemus.henkilo_oid} puuttuu!")
        }
        if (oppija == null) {
            logger.warn("Hakemuksen ${hakemus.hakemus_oid} oppija henkilölle ${hakemus.henkilo_oid} puuttuu!")
        }

        return HakemusResponse(
            yksiloity = onrHenkilo?.yksiloity,
            henkilotunnus = onrHenkilo?.hetu,
            syntymaAika = onrHenkilo?.syntymaaika,
            etunimet = onrHenkilo?.etunimet,
            sukunimi = onrHenkilo?.sukunimi,
            sukupuoliKoodi = onrHenkilo?.sukupuoli,
            aidinkieli = onrHenkilo?.aidinkieli?.kieliKoodi, //fixme, missä muodossa tää halutaan ulos?
            hakijanKansalaisuudet = onrHenkilo?.kansalaisuus?.map { it.kansalaisuusKoodi} ?: emptyList(),
            hakemusOid = hakemus.hakemus_oid,
            henkiloOid = hakemus.henkilo_oid,
            hakuOid = hakemus.haku_oid,
            hakemusTila = hakemus.hakemus_tila,
            ensikertalaisuus = oppija?.ensikertalainen,
            hakutoiveet = hakemus.hakutoiveet,
            hakijanAsuinmaa = hakemus.asuinmaa,
            hakijanKotikunta = hakemus.kotikunta,
            pohjakoulutus_kk = hakemus.pohjakoulutus_kk.map { it.pohjakoulutuskklomake }.filterNotNull(),
            ulkomaillaSuoritetunToisenAsteenTutkinnonSuoritusmaa = hakemus.pohjakoulutus_kk_ulk_country)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun findHakemuksetForHaku(
        hakuOid: String,
        vuosi: String,
        kausi: String
    ): CompletableFuture<List<HakemusResponse>> {
        return GlobalScope.future {
            logger.info("Haetaan hakemukset haulle $hakuOid, vuosi $vuosi and kausi $kausi")
            val haku: HakuInternal = koutaInternalClient.findByHakuOid(hakuOid).await()
            val isHakuWithEnsikertalaisuus = haku.isKkHaku() && (haku.isErillishaku() || haku.isYhteishaku())

            val hakemukset = ataruClient.fetchHaunHakemukset(hakuOid).await()
            val personOidsFromHakemukset = hakemukset.map { it.henkilo_oid }

            val oppijat = if (isHakuWithEnsikertalaisuus) {
                sureClient.fetchOppijatForPersonOidsInBatches(hakuOid, personOidsFromHakemukset, true)
            } else {
                logger.info("Ei haeta sure-tietoja haulle $hakuOid, koska se ei ole kk-haku ja joko yhteis- tai erillishaku.")
                CompletableFuture.completedFuture(emptyList())
            }
            val henkilot = onrClient.fetchHenkilotInBatches(personOidsFromHakemukset.toSet())
            val oppijatByHenkiloOid = oppijat.thenApply { result -> result.map { it.oppijanumero to it }.toMap() }.await()
            val henkilotByHenkiloOid = henkilot.thenApply { result -> result.map { it.oidHenkilo to it }.toMap() }.await()

            logger.info("Tiedot haettu haulle $hakuOid, muodostetaan tulokset")
            hakemukset.map { hakemus -> createHakemusResponse(hakemus, oppijatByHenkiloOid[hakemus.henkilo_oid], henkilotByHenkiloOid[hakemus.henkilo_oid]) }
        }
    }
}