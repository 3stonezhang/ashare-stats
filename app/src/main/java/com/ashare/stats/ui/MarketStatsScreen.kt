package com.ashare.stats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashare.stats.data.SentimentMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun MarketStatsRoute(
    viewModel: MarketStatsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MarketStatsScreen(
        state = state,
        onRefresh = viewModel::refreshNow,
    )
}

@Composable
fun MarketStatsScreen(
    state: MarketUiState,
    onRefresh: () -> Unit,
) {
    val riseColor = Color(0xFFE53935)
    val fallColor = Color(0xFF43A047)
    val stats = state.stats
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        ),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 以常见手机高度约 720dp 为基准做缩放，自动适配大小屏
        val heightScale = (maxHeight.value / 720f).coerceIn(0.72f, 1.12f)
        val widthScale = (maxWidth.value / 360f).coerceIn(0.78f, 1.08f)
        val scale = min(heightScale, widthScale)
        val metrics = CompactMetrics(scale)

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(metrics.iconSize))
            }
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.pagePadding, vertical = metrics.pagePadding * 0.6f),
        ) {
            CompactHeader(
                title = "A股涨跌统计",
                subtitle = buildSubtitle(stats.tradeDate, stats.updatedAtMillis, stats.flatCount),
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                metrics = metrics,
            )

            if (state.errorMessage != null) {
                Text(
                    text = "刷新失败：${state.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = metrics.captionSp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(metrics.gap))
            }

            val riseSentiment = SentimentMapper.fromRiseCount(stats.riseCount)
            val riseGt5Status = SentimentMapper.fromRiseGt5Count(stats.riseGt5Count)
            val fallGt5Effect = SentimentMapper.fromFallGt5Count(stats.fallGt5Count)
            val limitUpSentiment = SentimentMapper.fromLimitUpCount(stats.limitUpCount)
            val limitDownEffect = SentimentMapper.fromLimitDownCount(stats.limitDownCount)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(metrics.gap),
            ) {
                StatRow(
                    modifier = Modifier.weight(1f),
                    gap = metrics.gap,
                    left = {
                        CompactStatCard(
                            title = "上涨",
                            value = stats.riseCount,
                            accent = riseColor,
                            assessment = "${riseSentiment.category} · ${riseSentiment.label}",
                            metrics = metrics,
                        )
                    },
                    right = {
                        CompactStatCard(
                            title = "下跌",
                            value = stats.fallCount,
                            accent = fallColor,
                            assessment = null,
                            metrics = metrics,
                        )
                    },
                )
                StatRow(
                    modifier = Modifier.weight(1f),
                    gap = metrics.gap,
                    left = {
                        CompactStatCard(
                            title = "涨幅≥5%",
                            value = stats.riseGt5Count,
                            accent = riseColor,
                            assessment = "${riseGt5Status.category} · ${riseGt5Status.label}",
                            metrics = metrics,
                        )
                    },
                    right = {
                        CompactStatCard(
                            title = "涨幅<5%",
                            value = stats.riseLt5Count,
                            accent = riseColor.copy(alpha = 0.8f),
                            assessment = null,
                            metrics = metrics,
                        )
                    },
                )
                StatRow(
                    modifier = Modifier.weight(1f),
                    gap = metrics.gap,
                    left = {
                        CompactStatCard(
                            title = "跌幅≥5%",
                            value = stats.fallGt5Count,
                            accent = fallColor,
                            assessment = "${fallGt5Effect.category} · ${fallGt5Effect.label}",
                            metrics = metrics,
                        )
                    },
                    right = {
                        CompactStatCard(
                            title = "跌幅<5%",
                            value = stats.fallLt5Count,
                            accent = fallColor.copy(alpha = 0.8f),
                            assessment = null,
                            metrics = metrics,
                        )
                    },
                )
                StatRow(
                    modifier = Modifier.weight(1f),
                    gap = metrics.gap,
                    left = {
                        CompactStatCard(
                            title = "涨停",
                            value = stats.limitUpCount,
                            accent = riseColor,
                            assessment = "${limitUpSentiment.category} · ${limitUpSentiment.label}",
                            metrics = metrics,
                        )
                    },
                    right = {
                        CompactStatCard(
                            title = "跌停",
                            value = stats.limitDownCount,
                            accent = fallColor,
                            assessment = "${limitDownEffect.category} · ${limitDownEffect.label}",
                            metrics = metrics,
                        )
                    },
                )
            }
        }
    }
}

private data class CompactMetrics(val scale: Float) {
    val pagePadding: Dp get() = (10f * scale).dp
    val gap: Dp get() = (6f * scale).dp
    val cardRadius: Dp get() = (10f * scale).dp
    val cardPaddingH: Dp get() = (10f * scale).dp
    val cardPaddingV: Dp get() = (8f * scale).dp
    val iconSize: Dp get() = (22f * scale).dp
    val titleSp: TextUnit get() = (17f * scale).sp
    val subtitleSp: TextUnit get() = (11f * scale).sp
    val labelSp: TextUnit get() = (12f * scale).sp
    val valueSp: TextUnit get() = (26f * scale).sp
    val assessSp: TextUnit get() = (12f * scale).sp
    val captionSp: TextUnit get() = (10f * scale).sp
}

@Composable
private fun CompactHeader(
    title: String,
    subtitle: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    metrics: CompactMetrics,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = metrics.titleSp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                fontSize = metrics.subtitleSp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.size(metrics.iconSize + 16.dp),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(metrics.iconSize),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "立即刷新",
                    modifier = Modifier.size(metrics.iconSize),
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    modifier: Modifier = Modifier,
    gap: Dp,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { left() }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { right() }
    }
}

@Composable
private fun CompactStatCard(
    title: String,
    value: Int,
    accent: Color,
    assessment: String?,
    metrics: CompactMetrics,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(metrics.cardRadius),
            )
            .padding(horizontal = metrics.cardPaddingH, vertical = metrics.cardPaddingV),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            fontSize = metrics.labelSp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = String.format(Locale.CHINA, "%,d", value),
            color = accent,
            fontSize = metrics.valueSp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            letterSpacing = (-0.5).sp,
        )
        if (assessment != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = assessment,
                fontSize = metrics.assessSp,
                fontWeight = FontWeight.SemiBold,
                color = accent.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildSubtitle(tradeDate: String, updatedAtMillis: Long, flatCount: Int): String {
    val parts = mutableListOf<String>()
    if (tradeDate.isNotBlank()) parts += tradeDate
    if (updatedAtMillis > 0L) {
        parts += SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(updatedAtMillis))
    }
    parts += "平盘$flatCount"
    parts += "不含北交所/ST"
    return parts.joinToString(" · ")
}
