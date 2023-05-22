package ulkoiset_rajapinnat

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import ulkoiset_rajapinnat.kouta.dto.HakukohdeInternal
import ulkoiset_rajapinnat.response.Hakutoive
import ulkoiset_rajapinnat.response.VastaanottoResponse
import ulkoiset_rajapinnat.valinta_tulos_service.dto.HakemuksenValinnanTulos
import ulkoiset_rajapinnat.valintapisteet.dto.HakemuksenValintapisteet
import java.util.concurrent.CompletableFuture

class VastaanottoForHakuApi(clients: Clients) : VastaanottoForHaku {
    private val koutaInternalClient = clients.koutaInternalClient
    private val vtsClient = clients.valintaTulosServiceClient
    private val valintaperusteetClient = clients.valintaperusteetClient
    private val valintapisteClient = clients.valintapisteClient

    private val logger = LoggerFactory.getLogger("VastaanottoForHakuApi")

    private fun createHakemuksenTulos(
        hakemuksenTulos: HakemuksenValinnanTulos,
        halututHakukohteet: Set<String>,
        valintakokeetByHakukohde: Map<String, List<String>>,
        kielikokeetByHakukohde: Map<String, List<String>>,
        pisteetByHakemusOid: Map<String, HakemuksenValintapisteet>
    ): VastaanottoResponse {
        val hakutoiveidenTulokset =
            hakemuksenTulos.hakutoiveet.filter { ht -> halututHakukohteet.contains(ht.hakukohdeOid) }
                .map { ht ->
                    val merkitsevaJono = ht.hakutoiveenValintatapajonot.first()
                    val hyvaksyttyEnsikertalaistenHakijaryhmasta =
                        ht.hakijaryhmat
                            .find { it.valintatapajonoOid.equals(merkitsevaJono.valintatapajonoOid)
                                    && it.hakijaryhmatyyppikoodiUri.equals("hakijaryhmantyypit_ensikertalaiset") }
                            ?.hyvaksyttyHakijaryhmasta ?: false
                    val valintakokeet = valintakokeetByHakukohde[ht.hakukohdeOid] ?: emptyList()
                    val kielikokeet = kielikokeetByHakukohde[ht.hakukohdeOid] ?: emptyList()
                    val pistetiedot = pisteetByHakemusOid[hakemuksenTulos.hakemusOid]
                    val osallistuiPaasykokeeseen =
                        valintakokeet.any { valintakoeTunniste -> pistetiedot?.pisteet?.any { it.osallistuiKokeeseen(valintakoeTunniste) } ?: false }
                    val osallistuiKielikokeeseen =
                        kielikokeet.any { valintakoeTunniste -> pistetiedot?.pisteet?.any { it.osallistuiKokeeseen(valintakoeTunniste) } ?: false }
                    Hakutoive(
                        hyvaksyttyEnsikertalaistenHakijaryhmasta = hyvaksyttyEnsikertalaistenHakijaryhmasta,
                        alinHyvaksyttyPistemaara = merkitsevaJono.alinHyvaksyttyPistemaara,
                        osallistuiKielikokeeseen = osallistuiKielikokeeseen,
                        valintatapajono = merkitsevaJono.valintatapajonoOid,
                        osallistuiPaasykokeeseen = osallistuiPaasykokeeseen,
                        ilmoittautumisenTila = merkitsevaJono.ilmoittautumisTila,
                        vastaanotonTila = ht.vastaanottotieto,
                        hakijanLopullinenJonosija = merkitsevaJono.jonosija,
                        hakukohdeOid = ht.hakukohdeOid,
                        yhteispisteet = merkitsevaJono.pisteet,
                        hakijanJonosijanTarkenne = merkitsevaJono.tasasijaJonosija,
                        valinnanTila = merkitsevaJono.tila,
                        valinnanTilanLisatieto = merkitsevaJono.tilanKuvaukset.getOrDefault("FI", ""),
                        hyvaksyttyHarkinnanvaraisesti = merkitsevaJono.hyvaksyttyHarkinnanvaraisesti
                    )
                }
        return VastaanottoResponse(hakemuksenTulos.hakijaOid, hakutoiveidenTulokset)
    }

    private suspend fun muodostaTulokset(hakuOid: String, hakemustenTulokset: CompletableFuture<List<HakemuksenValinnanTulos>>, hakukohdeOids: List<String>): CompletableFuture<List<VastaanottoResponse>> {
        logger.info("Muodostetaan vastaanotot haun $hakuOid hakukohteille $hakukohdeOids")
        val valintaperusteetByHakukohde = valintaperusteetClient.fetchValintakokeet(hakukohdeOids.toSet())
            .thenApply { result -> result.map { it.hakukohdeOid to it.valintaperusteDTO }.toMap() }
        val valintakoeTunnisteetByHakukohde = valintaperusteetByHakukohde.await()
            .map { v -> v.key to v.value.filter { valintaperuste -> valintaperuste.isValintakoe() }.map { it.tunniste } }.toMap()
        val kielikoeTunnisteetByHakukohde = valintaperusteetByHakukohde.await()
            .map { v -> v.key to v.value.filter { valintaperuste -> valintaperuste.isKielikoe() }.map { it.tunniste} }.toMap()

        val hakemusOids = hakemustenTulokset.await().map { it.hakemusOid }
        val pisteet = valintapisteClient.fetchValintapisteetForHakemusOidsInBatches(hakemusOids)
        val pisteetByHakemusOid = pisteet.thenApply { result -> result.map { it.hakemusOID to it }.toMap() }.await()

        return hakemustenTulokset.thenApply { tulokset ->
            tulokset.map { hakemuksenTulos ->
                createHakemuksenTulos(
                    hakemuksenTulos,
                    hakukohdeOids.toSet(),
                    valintakoeTunnisteetByHakukohde,
                    kielikoeTunnisteetByHakukohde,
                    pisteetByHakemusOid
                )
            }.filter { response -> response.hakutoiveet.isNotEmpty() }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun findVastaanototForHaku(
        hakuOid: String,
        vuosi: String,
        kausi: String
    ): CompletableFuture<List<VastaanottoResponse>> {
        return GlobalScope.future {
            logger.info("Haetaan vastaanotot haulle $hakuOid, vuosi $vuosi and kausi $kausi")
            val hakukohteet: List<HakukohdeInternal>? = koutaInternalClient.findHakukohteetByHakuOid(hakuOid).await()
            val hakukohteetKaudellaOids =
                hakukohteet?.filter { hk -> hk.onAlkamiskaudella(kausi, vuosi) }?.map { it.oid }?.toList() ?: emptyList()
            logger.info("Haulle $hakuOid löytyi ${hakukohteet?.size} hakukohdetta, joista ${hakukohteetKaudellaOids.size} on vuoden $vuosi kaudella $kausi")
            val valinnanTulokset = vtsClient.fetchHaunHakemustenTuloksetCached(hakuOid)
            muodostaTulokset(hakuOid, valinnanTulokset, hakukohteetKaudellaOids).await()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun findVastaanototForHakukohteet(hakuOid: String, hakukohdeOids: List<String>): CompletableFuture<List<VastaanottoResponse>>  {
        return GlobalScope.future {
            logger.info("Haetaan vastaanotot haun $hakuOid hakukohteille $hakukohdeOids")
            val hakemustenValinnantulokset = vtsClient.fetchHakemustenTuloksetHakukohteille(hakuOid, hakukohdeOids)
            muodostaTulokset(hakuOid, hakemustenValinnantulokset, hakukohdeOids).await()
        }
    }
}