package de.pilunia.app.ui.components

import android.app.TimePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.pilunia.app.R
import de.pilunia.app.ui.theme.Pilunia

/**
 * Öffnet den nativen Android-Zeitdialog (24 Stunden, minutengenau) in den App-Farben.
 * Aufruf: `val pickTime = rememberNativeTimePicker()` und dann `pickTime(minuteOfDay) { neu -> … }`.
 */
@Composable
fun rememberNativeTimePicker(): (minuteOfDay: Int, onPicked: (Int) -> Unit) -> Unit {
    val context = LocalContext.current
    val dark = Pilunia.colors.isDark
    return remember(context, dark) {
        { minuteOfDay, onPicked ->
            val m = ((minuteOfDay % 1440) + 1440) % 1440
            TimePickerDialog(
                context,
                if (dark) R.style.Pilunia_TimePicker_Dark else R.style.Pilunia_TimePicker_Light,
                { _, hour, minute -> onPicked(hour * 60 + minute) },
                m / 60, m % 60, true,
            ).show()
        }
    }
}
