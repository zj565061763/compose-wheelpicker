package com.sd.demo.compose.wheelpicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sd.demo.compose.wheelpicker.theme.AppTheme
import com.sd.lib.compose.wheelpicker.HorizontalWheelPicker
import com.sd.lib.compose.wheelpicker.SnappedIndex
import com.sd.lib.compose.wheelpicker.VerticalWheelPicker
import com.sd.lib.compose.wheelpicker.rememberWheelPickerState

class SampleDefaultActivity : ComponentActivity() {
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
  val verticalState = rememberWheelPickerState(initialIndex = 5)
  val horizontalState = rememberWheelPickerState(initialIndex = 3)

  verticalState.SnappedIndex { index ->
    logMsg { "vertical snappedIndex=$index" }
  }
  horizontalState.SnappedIndex { index ->
    logMsg { "horizontal snappedIndex=$index" }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
  ) {
    Text(
      text = "Vertical (current=${verticalState.currentIndex}, snapped=${verticalState.snappedIndex})",
      style = MaterialTheme.typography.titleMedium,
    )
    VerticalWheelPicker(
      modifier = Modifier.width(80.dp),
      count = { 50 },
      state = verticalState,
      itemHeight = 48.dp,
      unfocusedCount = 2,
    ) { index ->
      Text(text = index.toString())
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Horizontal (current=${horizontalState.currentIndex}, snapped=${horizontalState.snappedIndex})",
      style = MaterialTheme.typography.titleMedium,
    )
    HorizontalWheelPicker(
      modifier = Modifier.height(64.dp),
      count = { 50 },
      state = horizontalState,
      itemWidth = 56.dp,
      unfocusedCount = 2,
    ) { index ->
      Text(text = index.toString())
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
