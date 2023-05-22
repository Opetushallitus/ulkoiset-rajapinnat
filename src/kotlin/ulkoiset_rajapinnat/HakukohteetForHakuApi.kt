package ulkoiset_rajapinnat

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import ulkoiset_rajapinnat.haku.dto.OldHakukohdeTulos
import ulkoiset_rajapinnat.koodisto.dto.Koodisto
import ulkoiset_rajapinnat.koodisto.dto.CodeElement
import ulkoiset_rajapinnat.kouta.dto.HakukohdeInternal
import ulkoiset_rajapinnat.kouta.dto.KoulutusInternal
import ulkoiset_rajapinnat.kouta.dto.ToteutusInternal
import ulkoiset_rajapinnat.organisaatio.dto.Organisaatio
import ulkoiset_rajapinnat.response.HakukohdeResponse
import ulkoiset_rajapinnat.response.OrganisaatioResponse
import ulkoiset_rajapinnat.util.*
import java.util.*
import java.util.Optional.*
import java.util.concurrent.CompletableFuture

class HakukohteetForHakuApi(clients: Clients): HakukohteetForHaku {
    private val koutaInternalClient = clients.koutaInternalClient
    private val hakuClient = clients.hakuClient
    private val organisaatioClient = clients.organisaatioClient
    private val koodistoClient = clients.koodistoClient
    private val logger = LoggerFactory.getLogger("HakukohteetForHakuApi")

    private fun enrichOrganisaatiot(organisaatiot: Map<String, Organisaatio>): Map<String, OrganisaatioResponse> {
        return organisaatiot.mapValues { (oid, org) ->
            val relevantOrgs: List<Organisaatio> = organisaatiot.getAll(org.parentOids() + oid)
            val ytunnus = relevantOrgs.map { it.ytunnus }.filterNotNull().firstOrNull()
            val oppilaitosKoodi = relevantOrgs.map { it.oppilaitosKoodi }.filterNotNull().firstOrNull()
            OrganisaatioResponse(
                organisaationOid = oid,
                koulutustoimijanYtunnus = ytunnus,
                oppilaitosKoodi = oppilaitosKoodi,
                organisaationKuntakoodi = org.kotipaikkaUri?.stripType,
                organisaationNimi = org.nimi
            )
        }
    }

    private suspend fun findHakukohteetForOldHaku(hakuOid: String): List<HakukohdeResponse> {
        val hakukohdetulos = hakuClient.fetchHakuHakukohdeTulos(hakuOid)
        val tilastokeskus = hakukohdetulos.thenCompose { ht ->
            hakuClient.fetchTilastokeskus(ht.map { it.hakukohdeOid }.toSet())
                .thenApply { t -> t.map { it.hakukohdeOid to it }.toMap() }
        }
        val organisaatiot = hakukohdetulos.thenCompose { ht ->
            organisaatioClient
                .fetchOrganisaatiotAndParentOrganisaatiot(ht.flatMap { it.organisaatioOids }.toSet())
                .thenApply { orgs -> enrichOrganisaatiot(orgs.map { it.oid to it }.toMap()) }
        }
        val kausi = koodistoClient.fetchKoodisto("kausi")
        val kieli = koodistoClient.fetchKoodisto("kieli")
        val koulutustyyppi = koodistoClient.fetchKoodisto("koulutustyyppi")
        val hakukohdesearch =
            hakuClient.fetchHakukohdeSearchByHakuOid(hakuOid)
            .thenApply { h -> h.flatMap { it.tulokset }.map { it.oid to it }.toMap() }
        val koulutussearch =
            hakuClient.fetchKoulutusSearchByHakuOid(hakuOid)
                .thenApply { k -> k.flatMap { it.tulokset }.map { it.oid to it }.toMap() }

        return hakukohdetulos()
            .map { hk: OldHakukohdeTulos ->
                val oid = hk.hakukohdeOid
                val search = hakukohdesearch().get(hk.hakukohdeOid)
                val koulutukset = koulutussearch().getAll(hk.koulutusOids.toSet())
                val koulutus = koulutukset.firstOrNull()
                HakukohdeResponse(
                    hakukohteenOid = oid,
                    organisaatiot = hk.organisaatioOids.mapNotNull { organisaatiot()[it] },
                    hakukohteenNimi = hk.hakukohdeNimi.excludeBlankValues,
                    koulutuksenOpetuskieli = hk.opetuskielet.mapNotNull(kieli()::arvo),
                    koulutuksenKoulutustyyppi = listOf(koulutustyyppi().arvo(koulutus?.koulutustyyppiUri)).filterNotNull(),
                    hakukohteenKoulutuskoodit = koulutukset.map { it.koulutuskoodi.stripVersion.stripType },
                    koulutuksenAlkamisvuosi = koulutus?.vuosi,
                    koulutuksenAlkamiskausi = kausi().arvo(koulutus?.kausiUri),
                    hakukohteenKoulutukseenSisaltyvatKoulutuskoodit =
                    (tilastokeskus()[oid]?.koulutusLaajuusarvos ?: listOf()).map { it.koulutuskoodi }
                        .toSet().toList(),
                    hakukohteenKoodi = search?.koodistoNimi?.stripVersion?.stripType?.stripNull,
                    pohjakoulutusvaatimus = search?.pohjakoulutusvaatimus?.get("fi"),
                    hakijalleIlmoitetutAloituspaikat = search?.aloituspaikat,
                    valintojenAloituspaikat = search?.valintojenAloituspaikat,
                    ensikertalaistenAloituspaikat = search?.ensikertalaistenAloituspaikat
                )
            }
    }

    private suspend fun koutaKoulutuksenOpetuskieli(toteutus: ToteutusInternal?, kieli: CompletableFuture<Map<String, Koodisto>>): List<String> {
        val containsRelation = { ce: List<CodeElement>, opetuskieliKoodi: String -> ce.filter { !it.passive && it.codeElementUri.equals(opetuskieliKoodi) }
                .isNotEmpty() }
        val opetus = toteutus?.metadata?.getOrDefault("opetus", emptyMap<String, Any>())
        return if (opetus is Map<*, *>) {
            (opetus.getOrDefault("opetuskieliKoodiUrit", emptyList<String>()) as List<*>)
                .map { it as String? }
                .map { it?.stripVersion }
                .flatMap { opetuskieliKoodi: String? -> kieli().values.filter { opetuskieliKoodi != null && it.levelsWithCodeElements != null
                    && containsRelation(it.levelsWithCodeElements, opetuskieliKoodi)} }
                .map { it.koodiArvo }
                .distinct()
        } else {
            emptyList()
        }
    }

    private suspend fun findHakukohteetForKoutaHaku(hakuOid: String): List<HakukohdeResponse> {
        logger.info("Finding hakukohteet for haku $hakuOid")
        val kausi = koodistoClient.fetchKoodisto("kausi")
        val koulutustyyppi = koodistoClient.fetchKoodisto("koulutustyyppi", 2, true)
        val koulutusKoodisto = koodistoClient.fetchKoodisto("koulutus", 12)
        val kieli = koodistoClient.fetchKoodisto("kieli", 1,true)
        val koutaToteutukset = koutaInternalClient.findToteutuksetByHakuOid(hakuOid)
            .thenApply { it.map { it.oid to it }.toMap() }
        val koutaKoulutukset = koutaInternalClient.findKoulutuksetByHakuOid(hakuOid)
            .thenApply { it.map { it.oid to it }.toMap() }
        val koutaHakukohteet = koutaInternalClient.findHakukohteetByHakuOid(hakuOid)
        val organisaatiot = koutaHakukohteet.thenCompose {
            organisaatioClient.fetchOrganisaatiotAndParentOrganisaatiot(it.map { hk -> hk.tarjoaja }.toSet()) }
            .thenApply { orgs -> enrichOrganisaatiot(orgs.map { it.oid to it }.toMap()) }

        return koutaHakukohteet()
            .map { hk: HakukohdeInternal ->
                val organisaatio = organisaatiot()[hk.tarjoaja]
                val toteutus = koutaToteutukset().get(hk.toteutusOid)
                val koulutus: KoulutusInternal? = if(toteutus?.koulutusOid != null) koutaKoulutukset().get(toteutus.koulutusOid) else null
                val koulutusKoodit = (koulutus?.koulutusKoodiUrit ?: emptyList()).mapNotNull(koulutusKoodisto()::arvo)
                var koulutustyyppiHakukohteelta = koulutustyyppi().arvo(hk.koulutustyyppikoodi)
                if (koulutusKoodit.isEmpty()) {
                    logger.warn("Haun $hakuOid hakukohteelta ${hk.oid} puuttuu koulutusKoodit. Koulutus: $koulutus")
                }
                if (koulutustyyppiHakukohteelta == null) {
                    logger.error("Haun $hakuOid hakukohteelta ${hk.oid} puuttuu koulutustyyppi. Koulutuskoodit: $koulutusKoodit.")
                    throw RuntimeException("Puuttuva koulutustyyppi haun $hakuOid hakukohteella ${hk.oid}")
                }

                HakukohdeResponse(
                    hakukohteenOid = hk.oid,
                    organisaatiot = listOf(organisaatio).filterNotNull(),
                    hakukohteenNimi = hk.nimi.excludeBlankValues,
                    koulutuksenOpetuskieli = koutaKoulutuksenOpetuskieli(toteutus, kieli),
                    koulutuksenKoulutustyyppi = if (koulutustyyppiHakukohteelta != null) listOf(koulutustyyppiHakukohteelta) else emptyList(),
                    hakukohteenKoulutuskoodit = koulutusKoodit,
                    koulutuksenAlkamisvuosi = hk.paateltyAlkamiskausi.vuosi,
                    koulutuksenAlkamiskausi = kausi().arvo(hk.paateltyAlkamiskausi.kausiUri),
                    hakukohteenKoulutukseenSisaltyvatKoulutuskoodit = emptyList(),
                    hakukohteenKoodi = hk.hakukohde?.koodiUri,
                    pohjakoulutusvaatimus = hk.pohjakoulutusvaatimusKoodiUrit
                        .map { it.stripVersion.stripType }.firstOrNull(),
                    hakijalleIlmoitetutAloituspaikat = hk.aloituspaikat,
                    valintojenAloituspaikat = hk.aloituspaikat,
                    ensikertalaistenAloituspaikat = hk.ensikertalaisenAloituspaikat
                )
            }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun findHakukohteetForHaku(hakuOid: String): CompletableFuture<List<HakukohdeResponse>> {
        return GlobalScope.future {
            val hakukohdeResponse = if (Oids.isKoutaHaku(hakuOid)) {
                findHakukohteetForKoutaHaku(hakuOid)
            } else {
                findHakukohteetForOldHaku(hakuOid)
            }
            hakukohdeResponse
        }
    }
}