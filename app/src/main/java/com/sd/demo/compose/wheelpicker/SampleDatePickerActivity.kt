package com.sd.demo.compose.wheelpicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sd.demo.compose.wheelpicker.theme.AppTheme
import com.sd.lib.compose.wheelpicker.SnappedIndex
import com.sd.lib.compose.wheelpicker.VerticalWheelPicker
import com.sd.lib.compose.wheelpicker.WheelPickerState
import com.sd.lib.compose.wheelpicker.rememberWheelPickerState
import java.util.Calendar

private const val MIN_YEAR = 2000
private const val MAX_YEAR = 2050

class SampleDatePickerActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme {
        Content()
      }
    }
  }
}

@Composable
private fun Content(
  modifier: Modifier = Modifier,
) {
  var selectedYear by rememberSaveable { mutableIntStateOf(2026) }
  var selectedMonth by rememberSaveable { mutableIntStateOf(2) }
  var selectedDay by rememberSaveable { mutableIntStateOf(28) }

  val yearState = rememberWheelPickerState(initialIndex = selectedYear - MIN_YEAR)
  val monthState = rememberWheelPickerState(initialIndex = selectedMonth - 1)
  val dayState = rememberWheelPickerState(initialIndex = selectedDay - 1)

  yearState.SnappedIndex { index -> selectedYear = MIN_YEAR + index }
  monthState.SnappedIndex { index -> selectedMonth = index + 1 }
  dayState.SnappedIndex { index -> selectedDay = index + 1 }

  val dayCount = remember(selectedYear, selectedMonth) {
    daysInMonth(selectedYear, selectedMonth)
  }
  val displayDay = selectedDay.coerceIn(1, dayCount)

  Column(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
  ) {
    Text(
      text = "$selectedYear-${selectedMonth.twoDigits()}-${displayDay.twoDigits()}",
      style = MaterialTheme.typography.headlineSmall,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      DateWheelColumn(
        modifier = Modifier.weight(1f),
        count = { MAX_YEAR - MIN_YEAR + 1 },
        state = yearState,
        formatText = { index -> "${MIN_YEAR + index}年" },
      )

      DateWheelColumn(
        modifier = Modifier.weight(1f),
        count = { 12 },
        state = monthState,
        formatText = { index -> "${(index + 1).twoDigits()}月" },
      )

      DateWheelColumn(
        modifier = Modifier.weight(1f),
        count = { dayCount },
        state = dayState,
        formatText = { index -> "${(index + 1).twoDigits()}日" },
      )
    }
  }
}

@Composable
private fun DateWheelColumn(
  modifier: Modifier = Modifier,
  count: () -> Int,
  state: WheelPickerState,
  formatText: (index: Int) -> String,
) {
  VerticalWheelPicker(
    modifier = modifier,
    count = count,
    state = state,
  ) { index ->
    Text(
      modifier = Modifier.fillMaxWidth(),
      text = formatText(index),
      textAlign = TextAlign.Center,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
  AppTheme {
    Content()
  }
}

/** 计算[year]年[month]月的总天数 */
private fun daysInMonth(year: Int, month: Int): Int {
  return Calendar.getInstance().apply {
    clear()
    set(Calendar.YEAR, year)
    set(Calendar.MONTH, month - 1)
  }.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun Int.twoDigits(): String {
  return toString().padStart(2, '0')
}
