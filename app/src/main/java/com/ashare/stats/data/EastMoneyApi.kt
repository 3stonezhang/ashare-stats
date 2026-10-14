package com.ashare.stats.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 市场统计：
 * - 涨跌/涨跌幅家数：新浪沪深 A 股列表（node=hs_a）
 *   排除北交所（bj / 4、8、92 开头）以及 ST / *ST 等风险警示股
 * - 涨停/跌停：东方财富涨停池/跌停池，同样排除北交所与 ST
 *
 * 阈值按实时涨跌幅浮点数精确统计：≥5% / (0,5%) / ≤-5% / (-5%,0)。
 */
class EastMoneyApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchMarketStats(): MarketStats = withContext(Dispatchers.IO) {
        coroutineScope {
            val boardDeferred = async { fetchHsABoardStats() }
            val limitUpDeferred = async { fetchLimitPoolCount(isLimitUp = true) }
            val limitDownDeferred = async { fetchLimitPoolCount(isLimitUp = false) }

            val board = boardDeferred.await()
            MarketStats(
                riseCount = board.riseCount,
                fallCount = board.fallCount,
                riseGt5Count = board.riseGt5Count,
                riseLt5Count = board.riseLt5Count,
                fallGt5Count = board.fallGt5Count,
                fallLt5Count = board.fallLt5Count,
                limitUpCount = limitUpDeferred.await(),
                limitDownCount = limitDownDeferred.await(),
                flatCount = board.flatCount,
                tradeDate = board.tradeDate,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    /** 沪深 A 股（不含北交所）涨跌统计 */
    private suspend fun fetchHsABoardStats(): BoardAggregate = coroutineScope {
        val totalRaw = fetchSinaNodeCount(NODE_HS_A)
        val pageSize = SINA_PAGE_SIZE
        val pageCount = ((totalRaw + pageSize - 1) / pageSize).coerceAtLeast(1)
        val semaphore = Semaphore(permits = 12)

        val pages = (1..pageCount).map { page ->
            async {
                semaphore.withPermit {
                    fetchSinaPage(NODE_HS_A, page, pageSize)
                }
            }
        }.awaitAll()

        var rise = 0
        var fall = 0
        var flat = 0
        var riseGt5 = 0
        var riseLt5 = 0
        var fallGt5 = 0
        var fallLt5 = 0
        var tradeDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        for (row in pages.flatten()) {
            if (isBeijingStock(row.symbol, row.code)) continue
            if (isStStock(row.name)) continue
            val pct = row.changePercent ?: continue
            when {
                pct > 0 -> {
                    rise++
                    if (pct >= 5.0) riseGt5++ else riseLt5++
                }
                pct < 0 -> {
                    fall++
                    if (pct <= -5.0) fallGt5++ else fallLt5++
                }
                else -> flat++
            }
            if (row.tickTime.isNotBlank() && row.tickTime.length >= 8) {
                // ticktime 形如 10:53:22，交易日用今天（日内）；收盘后仍是当日
                tradeDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            }
        }

        BoardAggregate(
            riseCount = rise,
            fallCount = fall,
            riseGt5Count = riseGt5,
            riseLt5Count = riseLt5,
            fallGt5Count = fallGt5,
            fallLt5Count = fallLt5,
            flatCount = flat,
            tradeDate = tradeDate,
        )
    }

    private fun fetchSinaNodeCount(node: String): Int {
        val url =
            "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
                "Market_Center.getHQNodeStockCount?node=$node"
        val body = httpGet(url).trim().trim('"')
        return body.toIntOrNull() ?: error("无法解析沪深A股总数: $body")
    }

    private fun fetchSinaPage(node: String, page: Int, num: Int): List<SinaStock> {
        val url =
            "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
                "Market_Center.getHQNodeData?page=$page&num=$num&sort=symbol&asc=1&node=$node&symbol="
        val body = httpGet(url).trim()
        if (body.isEmpty() || body == "null" || body == "[]") return emptyList()
        val arr = json.parseToJsonElement(body).jsonArray
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val symbol = obj["symbol"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val code = obj["code"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val pct = obj["changepercent"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val tick = obj["ticktime"]?.jsonPrimitive?.contentOrNull.orEmpty()
            SinaStock(
                symbol = symbol,
                code = code,
                name = name,
                changePercent = pct,
                tickTime = tick,
            )
        }
    }

    private fun fetchLimitPoolCount(isLimitUp: Boolean): Int {
        val path = if (isLimitUp) "getTopicZTPool" else "getTopicDTPool"
        val sort = if (isLimitUp) "fbt:asc" else "fund:asc"
        // 尝试今天及往前几天，兼容非交易日
        val today = LocalDate.now()
        for (i in 0..5) {
            val date = today.minusDays(i.toLong()).format(DateTimeFormatter.BASIC_ISO_DATE)
            val url =
                "https://push2ex.eastmoney.com/$path" +
                    "?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt" +
                    "&Pageindex=0&pagesize=5000&sort=$sort&date=$date"
            val body = httpGet(url)
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: continue
            val pool = data["pool"] as? JsonArray
            if (pool != null) {
                val count = pool.count { el ->
                    val obj = el as? JsonObject ?: return@count false
                    val code = obj["c"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val name = obj["n"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    !isBeijingCode(code) && !isStStock(name)
                }
                // 有数据或明确空池都返回
                if (count > 0 || data["tc"]?.jsonPrimitive?.intOrNull == 0) {
                    return count
                }
            }
            val tc = data["tc"]?.jsonPrimitive?.intOrNull
            if (tc != null && tc > 0) {
                // 无 pool 细节时退回 tc（该接口通常已不含北交所）
                return tc
            }
        }
        return 0
    }

    private fun isBeijingStock(symbol: String, code: String): Boolean {
        if (symbol.startsWith("bj", ignoreCase = true)) return true
        return isBeijingCode(code)
    }

    private fun isBeijingCode(code: String): Boolean {
        // 北交所：4/8 开头老代码，92 开头新代码
        return code.startsWith("4") || code.startsWith("8") || code.startsWith("92")
    }

    /** ST / *ST / S*ST / SST 等风险警示股 */
    private fun isStStock(name: String): Boolean {
        if (name.isBlank()) return false
        val normalized = name.uppercase().replace(" ", "")
        return normalized.contains("ST")
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            .header("Referer", "https://finance.sina.com.cn/")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    private data class SinaStock(
        val symbol: String,
        val code: String,
        val name: String,
        val changePercent: Double?,
        val tickTime: String,
    )

    private data class BoardAggregate(
        val riseCount: Int,
        val fallCount: Int,
        val riseGt5Count: Int,
        val riseLt5Count: Int,
        val fallGt5Count: Int,
        val fallLt5Count: Int,
        val flatCount: Int,
        val tradeDate: String,
    )

    companion object {
        private const val NODE_HS_A = "hs_a"
        private const val SINA_PAGE_SIZE = 100
    }
}
