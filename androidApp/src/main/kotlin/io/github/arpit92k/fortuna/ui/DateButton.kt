package io.github.arpit92k.fortuna.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.arpit92k.fortuna.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.Instant
import java.time.ZoneOffset

/**
 * A button showing a date, which opens a calendar to choose another. No date
 * after today can be chosen, nor one before [earliest]. Null stands for today,
 * so a form left open past midnight still means the new day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(
    date: LocalDate?,
    onDate: (LocalDate?) -> Unit,
    todayText: String,
    dateText: (String) -> String,
    modifier: Modifier = Modifier,
    earliest: LocalDate? = null,
    enabled: Boolean = true,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Text(if (date == null) todayText else dateText(formatDate(date)))
    }
    if (!picking) return

    // The calendar works in UTC midnights.
    val today = java.time.LocalDate.now()
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val earliestMillis = earliest?.toJavaLocalDate()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (date?.toJavaLocalDate() ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= todayMillis && (earliestMillis == null || utcTimeMillis >= earliestMillis)

            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = { picking = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onDate(if (picked == today) null else picked.toKotlinLocalDate())
                }
                picking = false
            }) { Text(stringResource(R.string.date_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { picking = false }) { Text(stringResource(R.string.date_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
