package com.sd.demo.compose.wheelpicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sd.demo.compose.wheelpicker.theme.AppTheme
import com.sd.lib.compose.wheelpicker.VerticalWheelPicker
import com.sd.lib.compose.wheelpicker.rememberWheelPickerState
import kotlinx.coroutines.launch

private const val ITEM_COUNT = 50

class SampleScrollToIndexActivity : ComponentActivity() {
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
  val state = rememberWheelPickerState(initialIndex = 10)
  val coroutineScope = rememberCoroutineScope()

  Column(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
  ) {
    Text(
      text = "snappedIndex = ${state.snappedIndex}",
      style = MaterialTheme.typography.titleMedium,
    )

    VerticalWheelPicker(
      modifier = Modifier.width(80.dp),
      count = { ITEM_COUNT },
      state = state,
    ) { index ->
      Text(text = index.toString())
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Button(
        onClick = {
          coroutineScope.launch {
            state.animateScrollToIndex((state.snappedIndex - 1).coerceAtLeast(0))
          }
        },
      ) {
        Text(text = "-1 (Animate)")
      }

      Button(
        onClick = {
          coroutineScope.launch {
            state.animateScrollToIndex((state.snappedIndex + 1).coerceAtMost(ITEM_COUNT - 1))
          }
        },
      ) {
        Text(text = "+1 (Animate)")
      }

      Button(
        onClick = {
          coroutineScope.launch {
            state.animateScrollToIndex(25)
          }
        },
      ) {
        Text(text = "Animate to 25")
      }

      Button(
        onClick = {
          coroutineScope.launch {
            state.scrollToIndex(0)
          }
        },
      ) {
        Text(text = "Jump to 0")
      }

      Button(
        onClick = {
          coroutineScope.launch {
            state.scrollToIndex(ITEM_COUNT - 1)
          }
        },
      ) {
        Text(text = "Jump to ${ITEM_COUNT - 1}")
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
  AppTheme {
    Content()
  }
}
