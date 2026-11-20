package com.sd.demo.compose.wheelpicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
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
import com.sd.lib.compose.wheelpicker.VerticalWheelPicker

class SampleReverseLayoutActivity : ComponentActivity() {
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
          text = "Reverse Layout",
          style = MaterialTheme.typography.labelLarge,
        )
        VerticalWheelPicker(
          modifier = Modifier.width(88.dp),
          count = { 50 },
          reverseLayout = true,
        ) { index ->
          Text(text = index.toString())
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Safe Max Height",
          style = MaterialTheme.typography.labelLarge,
        )
        VerticalWheelPicker(
          modifier = Modifier
            .width(88.dp)
            .heightIn(max = 220.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
          count = { 100 },
          unfocusedCount = 20,
        ) { index ->
          Text(text = index.toString())
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
