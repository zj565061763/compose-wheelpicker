package com.sd.demo.compose.wheelpicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sd.demo.compose.wheelpicker.theme.AppTheme
import com.sd.lib.compose.wheelpicker.VerticalWheelPicker
import com.sd.lib.compose.wheelpicker.rememberWheelPickerState
import kotlin.math.abs

class SampleCustomDisplayActivity : ComponentActivity() {
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
  val wheel3dState = rememberWheelPickerState(initialIndex = 8)
  val highlightState = rememberWheelPickerState(initialIndex = 5)

  Column(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(32.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "3D Rotation",
          style = MaterialTheme.typography.labelLarge,
        )
        VerticalWheelPicker(
          modifier = Modifier.width(96.dp),
          count = { 30 },
          state = wheel3dState,
          unfocusedCount = 3,
          display = { index ->
            Box(
              modifier = Modifier.graphicsLayer {
                val percentage = state.scrollPercentageOf(index) ?: 0f
                val distance = abs(percentage).coerceIn(0f, 1f)
                rotationX = -percentage * 55f
                val scale = 1f - distance * 0.35f
                scaleX = scale
                scaleY = scale
                alpha = (1f - distance * 0.7f).coerceIn(0f, 1f)
              },
              contentAlignment = Alignment.Center,
            ) {
              Content(index)
            }
          },
        ) { index ->
          Text(text = "Item $index")
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Center Highlight",
          style = MaterialTheme.typography.labelLarge,
        )
        VerticalWheelPicker(
          modifier = Modifier.width(96.dp),
          count = { 30 },
          state = highlightState,
          unfocusedCount = 2,
          display = { index ->
            Box(
              modifier = Modifier.graphicsLayer {
                val isCentered = state.currentIndex == index
                val scale = if (isCentered) 1.2f else 0.85f
                scaleX = scale
                scaleY = scale
                alpha = if (isCentered) 1f else 0.45f
              },
              contentAlignment = Alignment.Center,
            ) {
              Content(index)
            }
          },
        ) { index ->
          val isCentered = highlightState.currentIndex == index
          Text(
            text = "Item $index",
            color = if (isCentered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
          )
        }
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
