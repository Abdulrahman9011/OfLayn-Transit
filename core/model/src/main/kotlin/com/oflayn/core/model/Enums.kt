package com.oflayn.core.model

/**
 * Card categories as defined by the official "Seyahat Kartları Uygulama Yönetmeliği" (Madde 5).
 * The type is NEVER inferred from a card UID: it comes from official data or the user's own selection.
 */
enum class BursaCardType {
    // Tam / Abonman Tam
    FULL, FULL_SUBSCRIPTION,
    // İndirimli
    DISCOUNTED, STUDENT, STUDENT_SUBSCRIPTION, GRADUATING_STUDENT, TEACHER, AGE_60_PLUS,
    // Ücretsiz (Madde 5-D)
    AGE_65_PLUS, AGE_65_PLUS_2022, DISABLED, DISABLED_COMPANION, PRESS, STATE_ATHLETE,
    COAST_GUARD, HONORARY_INSPECTOR, PER_DIEM_STAFF, POLICE_GENDARMERIE, MUNICIPAL_POLICE,
    // Not covered by the regulation text we read; see official "Ücretsiz Kart Kullanım Listesi".
    VETERAN_FAMILY, OTHER_FREE,
    UNKNOWN
}

enum class VehicleType { BUS, BURSARAY, TRAM, MINIBUS, OTHER }

/** How fresh a piece of data is. LIVE must never be used when the observation time is unknown. */
enum class Freshness { LIVE, RECENT, CACHED, STALE, UNAVAILABLE }

enum class Verification { OFFICIAL, OFFICIAL_RESTRICTED, UNOFFICIAL, USER_ENTERED, ESTIMATED }
