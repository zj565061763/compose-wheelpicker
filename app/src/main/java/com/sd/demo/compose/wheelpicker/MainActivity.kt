package com.sd.demo.compose.wheelpicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sd.demo.compose.wheelpicker.theme.AppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme {
        Content(
          listActivity = listOf(
            SampleDefaultActivity::class.java,
            SampleScrollToIndexActivity::class.java,
            SampleCustomFocusActivity::class.java,
            SampleCustomDisplayActivity::class.java,
            SampleReverseLayoutActivity::class.java,
            SampleDatePickerActivity::class.java,
          ),
          onClickItem = {
            startActivity(Intent(this, it))
          },
        )
      }
    }
  }
}

@Composable
private fun Content(
  modifier: Modifier = Modifier,
  listActivity: List<Class<out Activity>>,
  onClickItem: (Class<out Activity>) -> Unit,
) {
  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(5.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    items(listActivity) { item ->
      Button(onClick = { onClickItem(item) }) {
        Text(text = item.simpleName)
      }
    }
  }
}

inline fun logMsg(block: () -> String) {
  Log.i("sd-demo", block())
}
