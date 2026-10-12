package com.ashare.stats.data

data class MarketStats(
    val riseCount: Int = 0,
    val fallCount: Int = 0,
    val riseGt5Count: Int = 0,
    val riseLt5Count: Int = 0,
    val fallGt5Count: Int = 0,
    val fallLt5Count: Int = 0,
    val limitUpCount: Int = 0,
    val limitDownCount: Int = 0,
    val flatCount: Int = 0,
    val tradeDate: String = "",
    val updatedAtMillis: Long = 0L,
)
