package ulkoiset_rajapinnat

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import ulkoiset_rajapinnat.haku.dto.OldHakukohdeTulos
import ulkoiset_rajapinnat.koodisto.dto.Koodisto
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
                    koulutuksenKoulutustyyppi = koulutustyyppi().arvo(koulutus?.koulutustyyppiUri),
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

    private suspend fun koutaKoulutuksenOpetuskieli(toteutus: ToteutusInternal?, opetusKieli: CompletableFuture<Map<String, Koodisto>>): List<String> {
        val opetus = toteutus?.metadata?.getOrDefault("opetus", emptyMap<String, Any>())
        return if (opetus is Map<*, *>) {
            (opetus.getOrDefault("opetuskieliKoodiUrit", emptyList<String>()) as List<*>)
            .map { it as String?}
                    .mapNotNull(opetusKieli()::arvo)
        } else {
            emptyList()
        }
    }

    private suspend fun findHakukohteetForKoutaHaku(hakuOid: String): List<HakukohdeResponse> {
        val kieli = koodistoClient.fetchKoodisto("kieli")
        val kausi = koodistoClient.fetchKoodisto("kausi")
        val koulutustyyppi = koodistoClient.fetchKoodisto("koulutustyyppi")
        val opetusKieli = koodistoClient.fetchKoodisto("oppilaitoksenopetuskieli", 2)
        val koutaHaku = koutaInternalClient.findByHakuOid(hakuOid)
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
                val haku = koutaHaku()

                HakukohdeResponse(
                    hakukohteenOid = hk.oid,
                    organisaatiot = listOf(organisaatio).filterNotNull(),
                    hakukohteenNimi = hk.nimi.excludeBlankValues,
                    koulutuksenOpetuskieli = koutaKoulutuksenOpetuskieli(toteutus, opetusKieli),
                    koulutuksenKoulutustyyppi = koulutus?.koulutustyyppi,
                    hakukohteenKoulutuskoodit = listOf(koulutus?.koulutusKoodiUrit) //haetaan relaation kautta
                        .filterNotNull().flatten().map { it.stripVersion.stripType },
                    koulutuksenAlkamisvuosi = hk.paateltyAlkamiskausi.vuosi,
                    koulutuksenAlkamiskausi = kausi().arvo(hk.paateltyAlkamiskausi.kausiUri),
                    hakukohteenKoulutukseenSisaltyvatKoulutuskoodit = emptyList(),
                    hakukohteenKoodi = null, //hakukohteen koodi
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