package com.nuvio.tv.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Fork: dark rounded clock pill for the top-right corner (Home, Details, player OSD).
 * [trailing] is an optional muted second part, e.g. "ends at 14:34", shown after a divider.
 */
@Composable
fun ClockPill(time: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .background(Color(0xE61A1C22), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
        if (trailing != null) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/** Current wall-clock time in milliseconds, updated on each minute boundary. */
@Composable
fun rememberMinuteClockMs(): Long {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            nowMs = current
            delay((60_000L - (current % 60_000L)).coerceAtLeast(250L))
        }
    }
    return nowMs
}

/** The time-only pill used on Home and Details, in the device's 12/24-hour format. */
@Composable
fun ScreenClockPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    ClockPill(time = formatter.format(Date(rememberMinuteClockMs())), modifier = modifier)
}
