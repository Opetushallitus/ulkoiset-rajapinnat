package ulkoiset_rajapinnat.valinta_tulos_service.dto

data class Hakijaryhma(
    val oid: String?,
    val nimi: String?,
    val kiintio: Int?,
    val hakijaryhmatyyppikoodiUri: String?,
    val valintatapajonoOid: String?,
    val hyvaksyttyHakijaryhmasta: Boolean?
)

data class HakutoiveenValintatapajono(
    val valintatapajonoPrioriteetti: Int,
    val valintatapajonoOid: String,
    val valintatapajonoNimi: String,
    val eiVarasijatayttoa: Boolean,
    val jonosija: Int,
    val paasyJaSoveltuvuusKokeenTulos: String?,
    val varasijanNumero: Int?,
    val tila: String,
    val tilanKuvaukset: Map<String, String>,
    val ilmoittautumisTila: String,
    val hyvaksyttyHarkinnanvaraisesti: Boolean,
    val tasasijaJonosija: Int,
    val pisteet: Double?,
    val alinHyvaksyttyPistemaara: Double?,
    val hakeneet: Int?,
    val hyvaksytty: Int?,
    val varalla: Int?,
    val varasijat: Int?,
    val varasijaTayttoPaivat: Int?,
    val varasijojaKaytetaanAlkaen: String?,
    val varasijojaTaytetaanAsti: String?,
    val tayttojono: String?,
    val julkaistavissa: Boolean,
    val ehdollisestiHyvaksyttavissa: Boolean,
    val ehdollisenHyvaksymisenEhtoKoodi: String?,
    val ehdollisenHyvaksymisenEhtoFI: String?,
    val ehdollisenHyvaksymisenEhtoSV: String?,
    val ehdollisenHyvaksymisenEhtoEN: String?,
    val hyvaksyttyVarasijalta: Boolean,
    val valintatuloksenViimeisinMuutos: String?,
    val hakemuksenTilanViimeisinMuutos: Long,
)

data class HakutoiveenTulos(
    val hakutoive: Int,
    val hakukohdeOid: String,
    val tarjoajaOid: String?,
    val vastaanottotieto: String,
    val hakutoiveenValintatapajonot: List<HakutoiveenValintatapajono>,
    val kaikkiJonotSijoiteltu: Boolean?,
    val ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet: Double?,
    val hakijaryhmat: List<Hakijaryhma>
)

data class HakemuksenValinnanTulos(
    val hakijaOid: String,
    val hakemusOid: String,
    val etunimi: String?,
    val sukunimi: String?,
    val hakutoiveet: List<HakutoiveenTulos>
)
