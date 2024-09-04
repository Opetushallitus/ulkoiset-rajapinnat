package ulkoiset_rajapinnat

import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import ulkoiset_rajapinnat.ataru.dto.Ataruhakemus
import ulkoiset_rajapinnat.koodisto.dto.CodeElement
import ulkoiset_rajapinnat.kouta.dto.HakuInternal
import ulkoiset_rajapinnat.kouta.dto.HakukohdeInternal
import ulkoiset_rajapinnat.oppijanumerorekisteri.dto.OnrHenkilo
import ulkoiset_rajapinnat.response.HakemusResponse
import ulkoiset_rajapinnat.suoritusrekisteri.dto.Ensikertalaisuus
import ulkoiset_rajapinnat.util.arvo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class HakemusForHakuApi(clients: Clients) : HakemusForHaku {
    private val koutaInternalClient = clients.koutaInternalClient
    private val koodistoClient = clients.koodistoClient
    private val ataruClient = clients.ataruClient
    private val sureClient = clients.suoritusrekisteriClient
    private val onrClient = clients.oppijanumerorekisteriClient

    private val logger = LoggerFactory.getLogger("HakemusForHakuApi")

    private fun createHakemusResponse(hakemus: Ataruhakemus, ensikertalaisuus: Ensikertalaisuus?, onrHenkilo: OnrHenkilo?, maatJaValtiot: Map<String, String>, isHakuWithEnsikertalaisuus: Boolean): HakemusResponse {
        if (onrHenkilo == null) {
            logger.warn("Hakemuksen ${hakemus.hakemus_oid} henkilö ${hakemus.henkilo_oid} puuttuu!")
        }
        if (isHakuWithEnsikertalaisuus && ensikertalaisuus == null) {
            logger.warn("Hakemuksen ${hakemus.hakemus_oid} ensikertalaisuustieto henkilölle ${hakemus.henkilo_oid} puuttuu! Henkilö $onrHenkilo")
        }
        return HakemusResponse(
            yksiloity = onrHenkilo?.yksiloity,
            henkilotunnus = onrHenkilo?.hetu,
            syntymaAika = onrHenkilo?.syntymaaika,
            etunimet = onrHenkilo?.etunimet,
            sukunimi = onrHenkilo?.sukunimi,
            sukupuoliKoodi = onrHenkilo?.sukupuoli,
            aidinkieli = onrHenkilo?.aidinkieli?.kieliKoodi, //fixme, missä muodossa tää halutaan ulos?
            hakijanKansalaisuudet = onrHenkilo?.kansalaisuus?.map { maatJaValtiot[it.kansalaisuusKoodi] ?: "XXX" } ?: emptyList(),
            hakemusOid = hakemus.hakemus_oid,
            henkiloOid = hakemus.henkilo_oid,
            hakuOid = hakemus.haku_oid,
            hakemusTila = hakemus.hakemus_tila,
            ensikertalaisuus = if (ensikertalaisuus != null) ensikertalaisuus?.menettamisenPeruste == null else null,
            hakutoiveet = hakemus.hakutoiveet,
            hakijanAsuinmaa = hakemus.asuinmaa,
            hakijanKotikunta = hakemus.kotikunta,
            pohjakoulutus_kk = hakemus.pohjakoulutus_kk.map { it.pohjakoulutuskklomake }.filterNotNull(),
            ulkomaillaSuoritetunToisenAsteenTutkinnonSuoritusmaa = hakemus.pohjakoulutus_kk_ulk_country,
            koulusivistyskieli = hakemus.koulusivistyskieli,
            pohjakoulutus2aste = hakemus.pohjakoulutus_2aste,
            lahtokoulunOrganisaatioOid = hakemus.lahtokoulunOrganisaatioOid)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun findHakemuksetForHaku(
        hakuOid: String
    ): CompletableFuture<List<HakemusResponse>> {
        return GlobalScope.future {
            logger.info("Haetaan hakemukset kouta-haulle $hakuOid")
            val haku: HakuInternal = koutaInternalClient.findByHakuOid(hakuOid).await()
            val isHakuWithEnsikertalaisuus = haku.isKkHaku() //Fixme, tämä päättely voisi varmaan olla parempikin
            val ensikertalaisuudet = if (isHakuWithEnsikertalaisuus) {
                sureClient.fetchHaunEnsikertalaisuudet(hakuOid)
            } else {
                logger.info("Ei haeta sure-tietoja haulle $hakuOid, koska se ei ole kk-haku.")
                CompletableFuture.completedFuture(emptyList())
            }
            val is2AsteenYhteishaku = (haku.isYhteishaku() && haku.is2Aste())
            val hakemukset = fetchAtaru(hakuOid, is2AsteenYhteishaku)
            val personOidsFromHakemukset = hakemukset.map { it.henkilo_oid }

            val masterHenkilotByHakemusHenkiloOid = onrClient.fetchMasterHenkilotInBatches(personOidsFromHakemukset.toSet()).await()
            val ensikertalaisuusByHenkiloOid = ensikertalaisuudet.thenApply { result -> result.map { it.henkiloOid to it }.toMap() }.await()

            val maatJaValtiot1 = koodistoClient.fetchKoodisto("maatjavaltiot1", 2, true).await()
            val maatJaValtiot2 = koodistoClient.fetchKoodisto("maatjavaltiot2", 2, true).await()
            val mv2_value_to_mv1_value = maatJaValtiot2.values.map {
                val rinnasteinen: CodeElement? = it.levelsWithCodeElements?.find { rinnasteinenKoodi -> rinnasteinenKoodi.codeElementUri.startsWith("maatjavaltiot1_") }
                val rinnasteisenArvo = maatJaValtiot1.arvo(rinnasteinen?.codeElementUri)
                it.koodiArvo to (rinnasteisenArvo ?: "XXX")
            }.toMap()
            logger.info("Mv2 to mv1 mappings $mv2_value_to_mv1_value")
            logger.info("Tiedot haettu haulle $hakuOid, muodostetaan tulokset")
            hakemukset.map { hakemus ->
                val masterHenkilo = masterHenkilotByHakemusHenkiloOid[hakemus.henkilo_oid]
                createHakemusResponse(hakemus, ensikertalaisuusByHenkiloOid[masterHenkilo?.oidHenkilo ?: ""], masterHenkilo, mv2_value_to_mv1_value, isHakuWithEnsikertalaisuus) }
        }
    }

    private suspend fun fetchAtaru(hakuOid: String, is2AsteenYhteishaku: Boolean): List<Ataruhakemus> {
        // tehdään hidas operaatio hakukohde kerrallaan koska cas clientissa tulee 30min kohdalla timeout
        if(is2AsteenYhteishaku) {
            val hakukohteetForHaku = koutaInternalClient.findHakukohteetByHakuOid(hakuOid).await()
            val hakemukset = mutableListOf<Ataruhakemus>()
            for (hakukohde: HakukohdeInternal in hakukohteetForHaku) {
                hakemukset.addAll(ataruClient.fetchHaunHakemuksetHakukohteella(hakuOid, hakukohde.oid).await())
            }
            return hakemukset
        }
        return ataruClient.fetchHaunHakemukset(hakuOid).await()
    }

    private val tulosCache = Caffeine.newBuilder()
        .expireAfterWrite(2L, TimeUnit.HOURS)
        .buildAsync { hakuOid: String, executor: Executor -> findHakemuksetForHaku(hakuOid) }

    override fun findHakemuksetForHakuCached(
        hakuOid: String
     ): CompletableFuture<List<HakemusResponse>> {
        return tulosCache.get(hakuOid)
    }
}