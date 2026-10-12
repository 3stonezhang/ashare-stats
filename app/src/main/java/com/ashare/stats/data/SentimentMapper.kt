package com.ashare.stats.data

/**
 * 根据图片对照表，把实时家数映射为市场情绪 / 市场状态 / 亏钱效应。
 * 区间边界采用「左闭右开」：落在分界点时归入更高一档；最末档为「以上」。
 * 跌停「0」单独为「无」，「0-5」按 1..5 处理。
 */
object SentimentMapper {

    data class Assessment(
        val category: String,
        val label: String,
    )

    fun fromRiseCount(count: Int): Assessment = Assessment(
        category = "市场情绪",
        label = when {
            count < 500 -> "绝对冰点"
            count < 800 -> "冰点"
            count < 2000 -> "正常"
            count < 3500 -> "情绪良好"
            count < 4500 -> "情绪高潮"
            else -> "极度亢奋"
        },
    )

    fun fromLimitUpCount(count: Int): Assessment = Assessment(
        category = "市场情绪",
        label = when {
            count < 20 -> "绝对冰点"
            count < 30 -> "冰点"
            count < 60 -> "正常"
            count < 80 -> "情绪良好"
            count < 100 -> "情绪高潮"
            else -> "极度亢奋"
        },
    )

    fun fromRiseGt5Count(count: Int): Assessment = Assessment(
        category = "市场状态",
        label = when {
            count < 50 -> "极差"
            count < 100 -> "很差"
            count < 150 -> "一般"
            count < 200 -> "正常"
            count < 300 -> "比较好"
            else -> "很好"
        },
    )

    fun fromLimitDownCount(count: Int): Assessment = Assessment(
        category = "亏钱效应",
        label = when {
            count <= 0 -> "无"
            count <= 5 -> "正常"
            count <= 10 -> "有点大"
            count <= 30 -> "很大"
            count <= 50 -> "非常大"
            else -> "股灾"
        },
    )

    fun fromFallGt5Count(count: Int): Assessment = Assessment(
        category = "亏钱效应",
        label = when {
            count < 30 -> "无"
            count < 60 -> "正常"
            count < 100 -> "有点大"
            count < 200 -> "很大"
            count < 500 -> "非常大"
            else -> "股灾"
        },
    )
}
