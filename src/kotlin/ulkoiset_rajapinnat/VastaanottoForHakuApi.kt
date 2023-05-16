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
import ulkoiset_rajapinnat.valintaperusteet.dto.ValintaperusteDTO
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
        valintakokeetByHakukohde: Map<String, List<ValintaperusteDTO>>,
        kielikokeetByHakukohde: Map<String, List<ValintaperusteDTO>>,
        pisteetByHakemusOid: Map<String, HakemuksenValintapisteet>
    ): VastaanottoResponse {
        logger.info("Muodostetaan hakemuksen tulos hakemukselle ${hakemuksenTulos.hakemusOid}")
        val hakutoiveidenTulokset =
            hakemuksenTulos.hakutoiveet.filter { ht -> halututHakukohteet.contains(ht.hakukohdeOid) }
                .map { ht ->
                    val merkitsevaJono = ht.hakutoiveenValintatapajonot.first()
                    val hyvaksyttyEnsikertalaistenHakijaryhmasta =
                        ht.hakijaryhmat
                            .find { it.valintatapajonoOid.equals(merkitsevaJono.valintatapajonoOid)
                                    && it.hakijaryhmatyyppikoodiUri.equals("hakijaryhmantyypit_ensikertalaiset") }
                            ?.hyvaksyttyHakijaryhmasta ?: false
                    Hakutoive(
                        hyvaksyttyEnsikertalaistenHakijaryhmasta = hyvaksyttyEnsikertalaistenHakijaryhmasta,
                        alinHyvaksyttyPistemaara = merkitsevaJono.alinHyvaksyttyPistemaara,
                        osallistuiKielikokeeseen = false, //fixme
                        valintatapajono = merkitsevaJono.valintatapajonoOid,
                        osallistuiPaasykokeeseen = false, //fixme
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

    @OptIn(DelicateCoroutinesApi::class)
    override fun findVastaanototForHaku(
        hakuOid: String,
        vuosi: String,
        kausi: String
    ): CompletableFuture<List<VastaanottoResponse>> {
        return GlobalScope.future {
            logger.info("Finding vastaanotot for haku $hakuOid, vuosi $vuosi and kausi $kausi")
            val hakemustenValinnantulokset = vtsClient.fetchHaunHakemustenTuloksetCached(hakuOid)
            val hakukohteet: List<HakukohdeInternal>? = koutaInternalClient.findHakukohteetByHakuOid(hakuOid).await()
            val hakukohteetKaudellaOids =
                hakukohteet?.filter { hk -> hk.onAlkamiskaudella(kausi, vuosi) }?.map { it.oid }?.toSet() ?: emptySet()
            logger.info("Haulle $hakuOid löytyi ${hakukohteet?.size} hakukohdetta, joista ${hakukohteetKaudellaOids.size} on vuoden $vuosi kaudella $kausi")
            val valintaperusteetByHakukohde = valintaperusteetClient.fetchValintakokeet(hakukohteetKaudellaOids)
                .thenApply { result -> result.map { it.hakukohdeOid to it.valintaperusteDTO }.toMap() }
            //logger.info("Saatiin valintaperusteet: ${valintaperusteetByHakukohde.await()}")

            val valintakokeetByHakukohde = valintaperusteetByHakukohde.await().map { v -> v.key to v.value.filter { valintaperuste -> valintaperuste.isValintakoe() } }.toMap()
            val kielikoeetByHakukohde = valintaperusteetByHakukohde.await().map { v -> v.key to v.value.filter { valintaperuste -> valintaperuste.isKielikoe()} }.toMap()

            val hakemusOids = hakemustenValinnantulokset.await().map { it.hakemusOid }
            val pisteet = valintapisteClient.fetchValintapisteetForHakemusOidsInBatches(hakemusOids)
            val pisteetByHakemusOid = pisteet.thenApply { result -> result.map { it.hakemusOID to it }.toMap() }
            //logger.info("Saatiin pisteet: ${pisteetByHakemusOid.await()}")

            hakemustenValinnantulokset.await().map { hakemuksenTulos ->
                createHakemuksenTulos(hakemuksenTulos, hakukohteetKaudellaOids, valintakokeetByHakukohde, kielikoeetByHakukohde, pisteetByHakemusOid.await())
            }.filter { response -> response.hakutoiveet.isNotEmpty() }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun findVastaanototForHakukohteet(hakuOid: String, hakukohdeOids: List<String>): CompletableFuture<List<VastaanottoResponse>>  {
        return GlobalScope.future {
            logger.info("Haetaan vastaanotot haun $hakuOid hakukohteille $hakukohdeOids")
            val hakemustenValinnantulokset = vtsClient.fetchHakemustenTuloksetHakukohteille(hakuOid, hakukohdeOids)
            val valintaperusteet = valintaperusteetClient.fetchValintakokeet(hakukohdeOids.toSet())
            //logger.info("Saatiin valintaperusteet: ${valintaperusteet.await()}")
            hakemustenValinnantulokset.await().map { hakemuksenTulos ->
                createHakemuksenTulos(
                    hakemuksenTulos,
                    hakukohdeOids.toSet(),
                    emptyMap(),
                    emptyMap(),
                    emptyMap()
                )
            }.filter { response -> response.hakutoiveet.isNotEmpty() }
        }
    }
}