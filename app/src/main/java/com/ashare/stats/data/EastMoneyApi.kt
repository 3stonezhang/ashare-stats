package com.ashare.stats.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 东方财富公开接口：
 * - 涨跌分布 getTopicZDFenBu（按 1% 分桶）
 * - 涨停池 / 跌停池总数
 *
 * 分桶约定：key = k 表示涨跌幅落在 [k%, k+1%)；
 * 11 / -11 表示极端区间（约 ≥10% / ≤-10%）。
 * 「涨/跌幅 ≥ 5%」取 |k| >= 5，「涨/跌幅 < 5%」取 1..4 / -4..-1。
 */
class EastMoneyApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchMarketStats(): MarketStats = withContext(Dispatchers.IO) {
        coroutineScope {
            val fenbuDeferred = async { fetchFenbu() }
            val limitUpDeferred = async { fetchPoolTotal(isLimitUp = true) }
            val limitDownDeferred = async { fetchPoolTotal(isLimitUp = false) }

            val fenbu = fenbuDeferred.await()
            val limitUp = limitUpDeferred.await()
            val limitDown = limitDownDeferred.await()

            MarketStats(
                riseCount = fenbu.riseCount,
                fallCount = fenbu.fallCount,
                riseGt5Count = fenbu.riseGt5Count,
                riseLt5Count = fenbu.riseLt5Count,
                fallGt5Count = fenbu.fallGt5Count,
                fallLt5Count = fenbu.fallLt5Count,
                limitUpCount = limitUp,
                limitDownCount = limitDown,
                flatCount = fenbu.flatCount,
                tradeDate = fenbu.tradeDate,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun fetchFenbu(): FenbuAggregate {
        val url =
            "https://push2ex.eastmoney.com/getTopicZDFenBu" +
                "?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt"
        val body = httpGet(url)
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject
            ?: error("涨跌分布接口无 data 字段")

        val qdate = data["qdate"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val fenbuArray = data["fenbu"] as? JsonArray
            ?: error("涨跌分布接口无 fenbu 字段")

        val buckets = mutableMapOf<Int, Int>()
        for (item in fenbuArray) {
            val obj = item as? JsonObject ?: continue
            for ((key, value) in obj) {
                val k = key.toIntOrNull() ?: continue
                val count = (value as? JsonPrimitive)?.intOrNull ?: continue
                buckets[k] = count
            }
        }

        var rise = 0
        var fall = 0
        var riseGt5 = 0
        var riseLt5 = 0
        var fallGt5 = 0
        var fallLt5 = 0
        val flat = buckets[0] ?: 0

        // key=k 表示涨跌幅落在 [k%, k+1%)。
        // 「大于/等于 5%」取 k>=5；「小于 5%」取 1..4（下跌侧对称）。
        for ((key, count) in buckets) {
            when {
                key > 0 -> {
                    rise += count
                    if (key >= 5) riseGt5 += count else riseLt5 += count
                }
                key < 0 -> {
                    fall += count
                    if (key <= -5) fallGt5 += count else fallLt5 += count
                }
            }
        }

        return FenbuAggregate(
            riseCount = rise,
            fallCount = fall,
            riseGt5Count = riseGt5,
            riseLt5Count = riseLt5,
            fallGt5Count = fallGt5,
            fallLt5Count = fallLt5,
            flatCount = flat,
            tradeDate = formatTradeDate(qdate),
        )
    }

    private fun fetchPoolTotal(isLimitUp: Boolean): Int {
        val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val path = if (isLimitUp) "getTopicZTPool" else "getTopicDTPool"
        val sort = if (isLimitUp) "fbt:asc" else "fund:asc"
        val url =
            "https://push2ex.eastmoney.com/$path" +
                "?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt" +
                "&Pageindex=0&pagesize=1&sort=$sort&date=$today"
        val body = httpGet(url)
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"]?.jsonObject ?: return 0
        return data["tc"]?.jsonPrimitive?.intOrNull ?: 0
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .header("Referer", "https://quote.eastmoney.com/")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: $url")
            }
            return response.body?.string().orEmpty().ifBlank {
                error("空响应: $url")
            }
        }
    }

    private fun formatTradeDate(qdate: String): String {
        if (qdate.length != 8) return qdate
        return "${qdate.substring(0, 4)}-${qdate.substring(4, 6)}-${qdate.substring(6, 8)}"
    }

    private data class FenbuAggregate(
        val riseCount: Int,
        val fallCount: Int,
        val riseGt5Count: Int,
        val riseLt5Count: Int,
        val fallGt5Count: Int,
        val fallLt5Count: Int,
        val flatCount: Int,
        val tradeDate: String,
    )
}
