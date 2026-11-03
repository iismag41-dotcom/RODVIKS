package com.rodviks.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Deterministic avatar color from a username seed. */
fun avatarColor(seed: String): Color {
    var hash = 0
    for (c in seed) hash = 31 * hash + c.code
    val palette = listOf(
        0xFF2E6BE6, 0xFF7C5CE6, 0xFFE65C8A, 0xFFE6955C,
        0xFF5CB8E6, 0xFF5CE6A0, 0xFFB8E65C, 0xFFE65C5C,
        0xFF8A5CE6, 0xFF00897B, 0xFFD84315, 0xFF6A1B9A
    )
    return Color(palette[((hash % palette.size) + palette.size) % palette.size])
}

@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    showOnlineDot: Boolean = false
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(fullUrl(url))
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(avatarColor(name)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.trim().take(1).uppercase().ifBlank { "?" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.42f).sp
                )
            }
        }
        if (showOnlineDot && online) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.25f)
                    .clip(CircleShape)
                    .background(Color(0xFF44C77D))
            )
        }
    }
}

/** Resolves a possibly relative /uploads/... URL against the current base URL. */
fun fullUrl(url: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = (currentBaseUrlOverride ?: "https://rodviks.onrender.com").trimEnd('/')
    return if (url.startsWith("/")) "$base$url" else "$base/$url"
}

/** Global base URL for image resolution; set by the app on startup and settings change. */
var currentBaseUrlOverride: String? = null

/** Formats ISO timestamp to short display. */
fun formatTimeShort(iso: String?): String {
    val parsed = parseIso(iso) ?: return ""
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = parsed }
    val now = java.util.Calendar.getInstance()
    return if (cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
    ) {
        String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    } else {
        String.format("%02d.%02d", cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH) + 1)
    }
}

/** Formats ISO timestamp to time-only HH:mm. */
fun formatTimeOnly(iso: String?): String {
    val parsed = parseIso(iso) ?: return ""
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = parsed }
    return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}

fun parseIso(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            java.time.Instant.parse(iso).toEpochMilli()
        } catch (e2: Exception) {
            try {
                java.time.LocalDateTime.parse(iso)
                    .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (e3: Exception) {
                null
            }
        }
    }
}
