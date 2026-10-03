package com.oflayn.core.model

import java.time.Instant

data class GeoPoint(val lat: Double, val lon: Double)

data class Stop(val id: String, val name: String, val lat: Double, val lon: Double)

/** [stopIds] is the ordered stop sequence of one direction. */
data class Route(
    val id: String,
    val shortName: String,
    val longName: String?,
    val vehicleType: VehicleType,
    val stopIds: List<String>,
)

data class TransitAlert(val id: String, val title: String, val body: String?, val publishedAt: Instant?)

enum class TransactionType {
    BUS_FARE, BURSA_RAY_FARE, TRAM_FARE, TRANSFER, TOP_UP, ONLINE_TOP_UP, REFUND, ADJUSTMENT, SUBSCRIPTION, UNKNOWN
}

/** Only real or user-entered transactions are stored. [verified] is true only for official-source records. */
data class Transaction(
    val id: String,
    val cardId: String,
    val cardType: BursaCardType,
    val type: TransactionType,
    val amount: Kurus,
    val balanceAfter: Kurus?,
    val dateTime: Instant,
    val vehicleType: VehicleType?,
    val lineNumber: String?,
    val stationName: String?,
    val origin: String?,
    val destination: String?,
    val source: String,
    val verified: Boolean,
)
