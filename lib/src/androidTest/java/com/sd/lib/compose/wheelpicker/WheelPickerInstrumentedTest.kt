package com.sd.lib.compose.wheelpicker

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WheelPickerInstrumentedTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun testIntrinsicMeasurementsAndTightParentClipping() {
    var verticalMeasuredSize = IntSize.Zero
    var horizontalMeasuredSize = IntSize.Zero
    var expectedMainAxisPx = 0
    var expectedCrossAxisPx = 0
    val tightState = WheelPickerState(initialIndex = 2)
    var scene by mutableStateOf(0)

    composeRule.setContent {
      val density = LocalDensity.current
      val itemSizeDp = 40.dp
      val crossAxisDp = 96.dp
      expectedMainAxisPx = with(density) { itemSizeDp.roundToPx() * 5 }
      expectedCrossAxisPx = with(density) { crossAxisDp.roundToPx() }

      when (scene) {
        0 -> {
          Box(
            modifier = Modifier
              .width(IntrinsicSize.Min)
              .height(IntrinsicSize.Max)
              .onSizeChanged { verticalMeasuredSize = it },
          ) {
            VerticalWheelPicker(
              count = { 20 },
              itemHeight = itemSizeDp,
              unfocusedCount = 2,
              focus = {
                Box(modifier = Modifier.width(crossAxisDp))
              },
            ) { index ->
              BasicText(text = "V-$index")
            }
          }
        }

        1 -> {
          Box(
            modifier = Modifier
              .width(IntrinsicSize.Max)
              .height(IntrinsicSize.Min)
              .onSizeChanged { horizontalMeasuredSize = it },
          ) {
            HorizontalWheelPicker(
              count = { 20 },
              itemWidth = itemSizeDp,
              unfocusedCount = 2,
              focus = {
                Box(modifier = Modifier.height(crossAxisDp))
              },
            ) { index ->
              BasicText(text = "H-$index")
            }
          }
        }

        else -> {
          Box(modifier = Modifier.size(width = 60.dp, height = 24.dp)) {
            VerticalWheelPicker(
              count = { 10 },
              state = tightState,
              itemHeight = 48.dp,
              unfocusedCount = 2,
              focus = {
                Box(modifier = Modifier.size(width = 120.dp, height = 48.dp))
              },
            ) { index ->
              BasicText(text = "T-$index")
            }
          }
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(expectedCrossAxisPx, verticalMeasuredSize.width)
    assertEquals(expectedMainAxisPx, verticalMeasuredSize.height)

    composeRule.runOnUiThread {
      scene = 1
    }
    composeRule.waitForIdle()
    assertEquals(expectedMainAxisPx, horizontalMeasuredSize.width)
    assertEquals(expectedCrossAxisPx, horizontalMeasuredSize.height)

    composeRule.runOnUiThread {
      scene = 2
    }
    composeRule.waitForIdle()
    assertEquals(2, tightState.snappedIndex)
    assertEquals(0f, tightState.scrollPercentageOf(2))
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Test
  fun testModalBottomSheetNestedScrollFlingAndSnap() {
    val state = WheelPickerState(initialIndex = 20)

    composeRule.setContent {
      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          VerticalWheelPicker(
            modifier = Modifier.testTag("sheetWheel"),
            count = { 150 },
            state = state,
            itemHeight = 40.dp,
            unfocusedCount = 2,
          ) { index ->
            BasicText(text = "Row-$index")
          }
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(20, state.snappedIndex)

    composeRule.onNodeWithTag("sheetWheel").performTouchInput {
      swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80L)
    }
    composeRule.waitForIdle()

    assertTrue("Expected fling to advance past initial index 20, actual=${state.snappedIndex}", state.snappedIndex > 22)
    assertEquals(state.currentIndex, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(state.snappedIndex))
  }

  @Test
  fun testGestureInterruptAndCancellationAutoSnapAndKeyPrepend() {
    val state = WheelPickerState(initialIndex = 0)
    var items by mutableStateOf((0..99).toList())
    lateinit var composeScope: CoroutineScope
    var itemHeightPx = 0f
    var touchSlopPx = 0f

    composeRule.setContent {
      composeScope = rememberCoroutineScope()
      val density = LocalDensity.current
      val viewConfig = LocalViewConfiguration.current
      itemHeightPx = with(density) { 44.dp.toPx() }
      touchSlopPx = viewConfig.touchSlop

      VerticalWheelPicker(
        modifier = Modifier.testTag("interruptWheel"),
        count = { items.size },
        state = state,
        itemHeight = 44.dp,
        unfocusedCount = 2,
        key = { items[it] },
      ) { index ->
        BasicText(text = "K-${items[index]}")
      }
    }

    composeRule.waitForIdle()
    composeRule.mainClock.autoAdvance = false
    lateinit var animateJob: Job
    composeRule.runOnUiThread {
      animateJob = composeScope.launch {
        state.animateScrollToIndex(80)
      }
    }

    var steps = 0
    while (state.currentIndex < 8 && steps < 60) {
      composeRule.mainClock.advanceTimeByFrame()
      steps++
    }
    assertTrue(state.currentIndex >= 8)
    assertTrue(state.currentIndex < 80)
    assertTrue(animateJob.isActive)

    // 1. 用户手指按下拖动打断正在飞行的编程式动画
    composeRule.onNodeWithTag("interruptWheel").performTouchInput {
      down(center)
      moveBy(Offset(0f, -(touchSlopPx + itemHeightPx * 0.25f)))
      advanceEventTime(120L)
      up()
    }
    composeRule.mainClock.autoAdvance = true
    composeRule.waitForIdle()

    assertTrue(animateJob.isCancelled)
    val stoppedKey = items[state.snappedIndex]
    assertTrue("Expected interrupted stopped key in 8..79, actual=$stoppedKey", stoppedKey in 8..79)
    assertEquals(0f, state.scrollPercentageOf(state.snappedIndex))

    // 2. 外部主动取消飞行中的动画后，滚轮自动归位至最近整格
    composeRule.mainClock.autoAdvance = false
    lateinit var cancelJob: Job
    composeRule.runOnUiThread {
      cancelJob = composeScope.launch {
        state.animateScrollToIndex(95)
      }
    }
    var cancelSteps = 0
    while (
      (state.lazyListState.firstVisibleItemScrollOffset == 0 || state.currentIndex <= stoppedKey + 1) &&
      cancelSteps < 60
    ) {
      composeRule.mainClock.advanceTimeByFrame()
      cancelSteps++
    }
    composeRule.runOnUiThread {
      cancelJob.cancel()
    }
    composeRule.mainClock.autoAdvance = true
    composeRule.waitForIdle()
    assertEquals(0, state.lazyListState.firstVisibleItemScrollOffset)
    assertEquals(state.currentIndex, state.snappedIndex)
    val settledAfterCancelKey = items[state.snappedIndex]

    // 3. 后续头插 10 项，LazyList 原生 key 保持停稳条目且 snappedIndex 同步更新
    composeRule.runOnUiThread {
      items = (-10..-1).toList() + items
    }
    composeRule.waitForIdle()
    assertEquals(settledAfterCancelKey, items[state.snappedIndex])
  }

  @Test
  fun testHorizontalWheelPickerRtlVisualPercentageAndFocusIndicators() {
    val rtlState = WheelPickerState(initialIndex = 2)

    composeRule.setContent {
      Column {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          HorizontalWheelPicker(
            count = { 6 },
            state = rtlState,
            itemWidth = 50.dp,
            unfocusedCount = 2,
            focus = {
              WheelPickerFocusHorizontal(
                modifier = Modifier.testTag("focusHorizontalHairline"),
                thickness = Dp.Hairline,
              )
            },
            display = { index ->
              DefaultWheelPickerDisplay(
                modifier = Modifier.testTag("rtlDisplay-$index"),
                index = index,
              )
            },
          ) { index ->
            BasicText(text = "RTL-$index")
          }
        }

        WheelPickerFocusVertical(
          modifier = Modifier
            .size(80.dp, 40.dp)
            .testTag("focusVerticalTransparent"),
          color = Color.Transparent,
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("focusHorizontalHairline").assertIsDisplayed()
    composeRule.onNodeWithTag("focusVerticalTransparent").assertIsDisplayed()
    composeRule.onNodeWithTag("rtlDisplay-2", useUnmergedTree = true).assertIsDisplayed()

    val centerPct = rtlState.scrollPercentageOf(2)!!
    val visualRightPct = rtlState.scrollPercentageOf(1)!!
    val visualLeftPct = rtlState.scrollPercentageOf(3)!!
    assertEquals(0f.toRawBits(), centerPct.toRawBits())
    assertTrue("Expected index 1 (visual right in RTL) > 0f, actual=$visualRightPct", visualRightPct > 0f)
    assertTrue("Expected index 3 (visual left in RTL) < 0f, actual=$visualLeftPct", visualLeftPct < 0f)
  }

  @Test
  fun testTightParentClippingAndFocusIndicatorPixels() {
    assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }
    val tightState = WheelPickerState(initialIndex = 2)
    var scene by mutableStateOf(0)

    composeRule.setContent {
      when (scene) {
        0 -> {
          Box(
            modifier = Modifier
              .testTag("tightClipHost")
              .size(width = 160.dp, height = 80.dp)
              .background(Color.White),
            contentAlignment = Alignment.Center,
          ) {
            VerticalWheelPicker(
              modifier = Modifier
                .testTag("tightClipWheel")
                .size(width = 60.dp, height = 24.dp),
              count = { 10 },
              state = tightState,
              itemHeight = 48.dp,
              unfocusedCount = 2,
              focus = {
                Box(
                  modifier = Modifier.size(width = 120.dp, height = 48.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Box(
                    modifier = Modifier
                      .requiredWidth(120.dp)
                      .height(8.dp)
                      .background(Color.Blue),
                  )
                }
              },
              display = { index ->
                Content(index)
              },
            ) { _ ->
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .background(Color.Red),
              )
            }
          }
        }

        1 -> {
          Box(
            modifier = Modifier
              .size(width = 80.dp, height = 40.dp)
              .background(Color.Green),
          ) {
            WheelPickerFocusHorizontal(
              modifier = Modifier.testTag("focusHorizontalPixels"),
              thickness = Dp.Hairline,
              color = Color.Red,
            )
          }
        }

        2 -> {
          Box(
            modifier = Modifier
              .size(width = 80.dp, height = 40.dp)
              .background(Color.Green),
          ) {
            WheelPickerFocusVertical(
              modifier = Modifier.testTag("focusVerticalPixels"),
              thickness = Dp.Hairline,
              color = Color.Red,
            )
          }
        }

        else -> {
          Box(
            modifier = Modifier
              .size(width = 80.dp, height = 40.dp)
              .background(Color.Green),
          ) {
            WheelPickerFocusVertical(
              modifier = Modifier.testTag("focusTransparentPixels"),
              color = Color.Transparent,
            )
          }
        }
      }
    }

    composeRule.waitForIdle()

    val hostBounds = composeRule.onNodeWithTag("tightClipHost").fetchSemanticsNode().unclippedBoundsInRoot()
    val wheelBounds = composeRule.onNodeWithTag("tightClipWheel").fetchSemanticsNode().unclippedBoundsInRoot()
    val hostPixels = composeRule.onNodeWithTag("tightClipHost").captureToImage().toPixelMap()
    val wheelLeft = (wheelBounds.left - hostBounds.left).roundToInt()
    val wheelTop = (wheelBounds.top - hostBounds.top).roundToInt()
    val wheelRight = (wheelBounds.right - hostBounds.left).roundToInt()
    val wheelBottom = (wheelBounds.bottom - hostBounds.top).roundToInt()
    val hostBg = hostPixels[0, 0]
    assertTrue("Expected white host background, actual=$hostBg", hostBg.red > 0.95f && hostBg.green > 0.95f && hostBg.blue > 0.95f)

    var hasRedInside = false
    var hasBlueInside = false
    for (y in 0 until hostPixels.height) {
      for (x in 0 until hostPixels.width) {
        val color = hostPixels[x, y]
        val insideWheel = x in wheelLeft until wheelRight && y in wheelTop until wheelBottom
        if (insideWheel) {
          if (color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f) {
            hasRedInside = true
          }
          if (color.blue > 0.8f && color.red < 0.2f && color.green < 0.2f) {
            hasBlueInside = true
          }
        } else {
          assertEquals(
            "Unexpected overflow pixel at ($x, $y) outside 60x24dp viewport [$wheelLeft..$wheelRight, $wheelTop..$wheelBottom)",
            hostBg,
            color,
          )
        }
      }
    }
    assertTrue("Expected red item pixels inside tight viewport", hasRedInside)
    assertTrue("Expected blue focus pixels inside tight viewport", hasBlueInside)

    composeRule.runOnUiThread {
      scene = 1
    }
    composeRule.waitForIdle()
    val horizontalPixels = composeRule.onNodeWithTag("focusHorizontalPixels").captureToImage().toPixelMap()
    val horizontalBg = horizontalPixels[horizontalPixels.width / 2, horizontalPixels.height / 2]
    assertTrue(
      "Expected green background in horizontal focus center, actual=$horizontalBg",
      horizontalBg.green > 0.9f && horizontalBg.red < 0.1f && horizontalBg.blue < 0.1f,
    )
    val horizontalEdge = horizontalPixels[0, 0]
    assertTrue(
      "Expected red divider on horizontal focus edge, actual=$horizontalEdge",
      horizontalEdge.red > 0.8f && horizontalEdge.green < 0.2f && horizontalEdge.blue < 0.2f,
    )
    for (y in 0 until horizontalPixels.height) {
      for (x in 0 until horizontalPixels.width) {
        if (x == 0 || x == horizontalPixels.width - 1) {
          assertEquals("Expected continuous horizontal divider pixel at ($x, $y)", horizontalEdge, horizontalPixels[x, y])
        } else {
          assertEquals("Expected untouched background pixel at ($x, $y)", horizontalBg, horizontalPixels[x, y])
        }
      }
    }

    composeRule.runOnUiThread {
      scene = 2
    }
    composeRule.waitForIdle()
    val verticalPixels = composeRule.onNodeWithTag("focusVerticalPixels").captureToImage().toPixelMap()
    val verticalBg = verticalPixels[verticalPixels.width / 2, verticalPixels.height / 2]
    assertTrue(
      "Expected green background in vertical focus center, actual=$verticalBg",
      verticalBg.green > 0.9f && verticalBg.red < 0.1f && verticalBg.blue < 0.1f,
    )
    val verticalEdge = verticalPixels[0, 0]
    assertTrue(
      "Expected red divider on vertical focus edge, actual=$verticalEdge",
      verticalEdge.red > 0.8f && verticalEdge.green < 0.2f && verticalEdge.blue < 0.2f,
    )
    for (y in 0 until verticalPixels.height) {
      for (x in 0 until verticalPixels.width) {
        if (y == 0 || y == verticalPixels.height - 1) {
          assertEquals("Expected continuous vertical divider pixel at ($x, $y)", verticalEdge, verticalPixels[x, y])
        } else {
          assertEquals("Expected untouched background pixel at ($x, $y)", verticalBg, verticalPixels[x, y])
        }
      }
    }

    composeRule.runOnUiThread {
      scene = 3
    }
    composeRule.waitForIdle()
    val transparentPixels = composeRule.onNodeWithTag("focusTransparentPixels").captureToImage().toPixelMap()
    val transparentBg = transparentPixels[transparentPixels.width / 2, transparentPixels.height / 2]
    assertTrue(
      "Expected green background under transparent focus, actual=$transparentBg",
      transparentBg.green > 0.9f && transparentBg.red < 0.1f && transparentBg.blue < 0.1f,
    )
    for (y in 0 until transparentPixels.height) {
      for (x in 0 until transparentPixels.width) {
        assertEquals("Expected transparent focus not to alter pixel at ($x, $y)", transparentBg, transparentPixels[x, y])
      }
    }
  }

  @Test
  fun testNonZeroOffsetShrinkClampsSnappedIndexAndRemountScrollToIndex() {
    val state = WheelPickerState(initialIndex = 4)
    val stateList = mutableStateListOf("A", "B", "C", "D", "E")
    var visible by mutableStateOf(true)
    var itemHeightPx = 0
    lateinit var mainScope: CoroutineScope

    composeRule.setContent {
      mainScope = rememberCoroutineScope()
      val density = LocalDensity.current
      itemHeightPx = with(density) { 48.dp.roundToPx() }
      if (visible) {
        VerticalWheelPicker(
          modifier = Modifier.testTag("shrinkWheel"),
          count = { stateList.size },
          state = state,
          itemHeight = 48.dp,
          unfocusedCount = 2,
          key = { stateList[it] },
        ) { index ->
          BasicText(text = stateList[index])
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(4, state.snappedIndex)

    // 1. 按住手指制造非零滚动偏移期间缩容列表，snappedIndex 立即夹紧至末项避免越界
    composeRule.onNodeWithTag("shrinkWheel").performTouchInput {
      down(center)
      moveBy(Offset(0f, itemHeightPx * 1.4f))
    }
    composeRule.waitForIdle()
    composeRule.runOnUiThread {
      stateList.removeAt(4)
      stateList.removeAt(3)
    }
    composeRule.waitForIdle()
    assertTrue("snappedIndex must be < size (${stateList.size}), actual=${state.snappedIndex}", state.snappedIndex in stateList.indices)
    composeRule.onNodeWithTag("shrinkWheel").performTouchInput {
      up()
    }
    composeRule.waitForIdle()

    // 2. 卸载隐藏期间重排列表并调用 scrollToIndex，重新挂载后覆盖旧索引且不受残留旧 key 影响
    composeRule.runOnUiThread {
      mainScope.launch {
        state.scrollToIndex(2)
      }
    }
    composeRule.waitForIdle()
    assertEquals("C", stateList[state.snappedIndex])

    composeRule.runOnUiThread {
      visible = false
    }
    composeRule.waitForIdle()
    assertEquals(-1, state.snappedIndex)

    var remountJumpCompleted = false
    var snappedWhileDetached = 99
    composeRule.runOnUiThread {
      stateList.clear()
      stateList.addAll(listOf("C", "A", "B") + (0..26).map { "Item-$it" })
      mainScope.launch(start = CoroutineStart.UNDISPATCHED) {
        state.scrollToIndex(1)
        snappedWhileDetached = state.snappedIndex
        remountJumpCompleted = true
      }
      visible = true
    }
    assertTrue("Expected scrollToIndex(1) to complete synchronously while detached", remountJumpCompleted)
    assertEquals(-1, snappedWhileDetached)

    composeRule.waitForIdle()
    assertEquals(1, state.snappedIndex)
    assertEquals("A", stateList[state.snappedIndex])
  }

  @Test
  fun testItemSelectedSemanticsUpdatesOnSnap() {
    val state = WheelPickerState(initialIndex = 2)
    lateinit var composeScope: CoroutineScope

    composeRule.setContent {
      composeScope = rememberCoroutineScope()
      VerticalWheelPicker(
        modifier = Modifier.testTag("semanticsWheel"),
        count = { 30 },
        state = state,
        itemHeight = 40.dp,
        unfocusedCount = 2,
      ) { index ->
        BasicText(text = "Option-$index")
      }
    }

    composeRule.waitForIdle()
    assertEquals(2, state.snappedIndex)
    composeRule.onNodeWithText("Option-1").assertIsNotSelected()
    composeRule.onNodeWithText("Option-2").assertIsSelected()
    composeRule.onNodeWithText("Option-3").assertIsNotSelected()

    composeRule.runOnUiThread {
      composeScope.launch {
        state.animateScrollToIndex(5)
      }
    }
    composeRule.waitForIdle()
    assertEquals(5, state.snappedIndex)
    composeRule.onNodeWithText("Option-4").assertIsNotSelected()
    composeRule.onNodeWithText("Option-5").assertIsSelected()
    composeRule.onNodeWithText("Option-6").assertIsNotSelected()

    composeRule.onNodeWithTag("semanticsWheel").performTouchInput {
      swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80L)
    }
    composeRule.waitForIdle()

    val settled = state.snappedIndex
    assertTrue("Expected gesture fling to advance past 5, actual=$settled", settled > 5)
    composeRule.onNodeWithText("Option-${settled - 1}").assertIsNotSelected()
    composeRule.onNodeWithText("Option-$settled").assertIsSelected()
    if (settled + 1 < 30) {
      composeRule.onNodeWithText("Option-${settled + 1}").assertIsNotSelected()
    }
  }

  @Test
  fun testHeadAndTailShrinkPreserveStableKey() {
    var headItems by mutableStateOf((0..13).toList())
    val headState = WheelPickerState(initialIndex = 8)
    val tailItems = mutableStateListOf("Item-0", "Item-1", "Item-2", "Item-3", "Item-4")
    val tailState = WheelPickerState(initialIndex = 2)
    var itemHeightPx = 0f
    var touchSlopPx = 0f

    composeRule.setContent {
      val density = LocalDensity.current
      val viewConfig = LocalViewConfiguration.current
      itemHeightPx = with(density) { 48.dp.roundToPx().toFloat() }
      touchSlopPx = viewConfig.touchSlop

      Column {
        VerticalWheelPicker(
          modifier = Modifier.testTag("headShrinkWheel"),
          count = { headItems.size },
          state = headState,
          key = { headItems[it] },
          itemHeight = 48.dp,
          unfocusedCount = 2,
        ) { index ->
          BasicText(text = "Key-${headItems[index]}")
        }

        VerticalWheelPicker(
          modifier = Modifier.testTag("tailShrinkWheel"),
          count = { tailItems.size },
          state = tailState,
          key = { tailItems[it] },
          itemHeight = 48.dp,
          unfocusedCount = 2,
        ) { index ->
          BasicText(text = tailItems[index])
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(8, headState.snappedIndex)
    assertEquals(2, tailState.snappedIndex)

    // 1. 删除开头 6 项使原选中索引 8 >= 新条目数 8，测量后稳定 key = 8 重定位至索引 2 并保持居中选中
    composeRule.runOnUiThread {
      headItems = headItems.drop(6)
    }
    composeRule.waitForIdle()
    assertEquals(2, headState.snappedIndex)
    assertEquals(2, headState.currentIndex)
    assertEquals(8, headItems[headState.snappedIndex])
    assertEquals(0f, headState.scrollPercentageOf(2))
    composeRule.onNodeWithText("Key-8").assertIsSelected()
    composeRule.onNodeWithText("Key-8").assertIsDisplayed()

    // 2. 向上拖动不到半格产生正像素偏移时删除尾项使 Item-2 成为新末项，重测回拉归零且松手后保持居中选中
    composeRule.onNodeWithTag("tailShrinkWheel").performTouchInput {
      down(center)
      moveBy(Offset(0f, -(touchSlopPx + itemHeightPx * 0.3f)))
    }
    composeRule.waitForIdle()
    assertTrue(tailState.scrollPercentageOf(2)!! < -0.02f)

    composeRule.runOnUiThread {
      tailItems.removeAt(4)
      tailItems.removeAt(3)
    }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("tailShrinkWheel").performTouchInput {
      up()
    }
    composeRule.waitForIdle()

    assertEquals(2, tailState.currentIndex)
    assertEquals(2, tailState.snappedIndex)
    assertEquals("Item-2", tailItems[tailState.snappedIndex])
    assertEquals(0f, tailState.scrollPercentageOf(2)!!, 0.0001f)
    val wheelNode = composeRule.onNodeWithTag("tailShrinkWheel").fetchSemanticsNode()
    val itemNode = composeRule.onNodeWithText("Item-2").fetchSemanticsNode()
    assertWheelItemCenteredVisibleAndSelected(
      wheelNode = wheelNode,
      itemNode = itemNode,
      expectedItemSizePx = itemHeightPx,
    )
  }

  @Test
  fun testDynamicMutateListAndScrollToIndex() {
    val stateList = mutableStateListOf<String>().also { list ->
      repeat(12) { list.add("StateItem-$it") }
    }
    var boxedItems by mutableStateOf((0..11).map { "BoxedItem-$it" })
    var targetJump by mutableStateOf<Int?>(8)
    val verticalState = WheelPickerState(initialIndex = 0)
    val horizontalState = WheelPickerState(initialIndex = 0)

    composeRule.setContent {
      LaunchedEffect(stateList.size, targetJump) {
        val target = targetJump ?: return@LaunchedEffect
        verticalState.animateScrollToIndex(target)
      }

      LaunchedEffect(boxedItems.size, targetJump) {
        val target = targetJump ?: return@LaunchedEffect
        horizontalState.scrollToIndex(target)
      }

      VerticalWheelPicker(
        count = { stateList.size },
        state = verticalState,
        key = { stateList[it] },
        itemHeight = 48.dp,
        unfocusedCount = 2,
      ) { index ->
        BasicText(text = stateList[index])
      }

      HorizontalWheelPicker(
        count = { boxedItems.size },
        state = horizontalState,
        key = { boxedItems[it] },
        itemWidth = 48.dp,
        unfocusedCount = 2,
      ) { index ->
        BasicText(text = boxedItems[index])
      }
    }

    composeRule.waitForIdle()
    assertEquals(8, verticalState.snappedIndex)
    assertEquals(8, horizontalState.snappedIndex)

    // 1. 将 12 项缩容至 1 项并联动跳转至 0
    composeRule.runOnUiThread {
      stateList.removeRange(1, 12)
      boxedItems = listOf("BoxedItem-0")
      targetJump = 0
    }
    composeRule.waitForIdle()
    assertEquals(0, verticalState.snappedIndex)
    assertEquals("StateItem-0", stateList[verticalState.snappedIndex])
    composeRule.onNodeWithText("StateItem-0").assertIsSelected()

    assertEquals(0, horizontalState.snappedIndex)
    assertEquals("BoxedItem-0", boxedItems[horizontalState.snappedIndex])
    composeRule.onNodeWithText("BoxedItem-0").assertIsSelected()

    // 2. 从 1 项扩容至 12 项并联动跳转至 11
    composeRule.runOnUiThread {
      for (index in 1..11) {
        stateList.add("StateItem-$index")
      }
      boxedItems = (0..11).map { "BoxedItem-$it" }
      targetJump = 11
    }
    composeRule.waitForIdle()
    assertEquals(11, verticalState.snappedIndex)
    assertEquals("StateItem-11", stateList[verticalState.snappedIndex])
    composeRule.onNodeWithText("StateItem-11").assertIsSelected()

    assertEquals(11, horizontalState.snappedIndex)
    assertEquals("BoxedItem-11", boxedItems[horizontalState.snappedIndex])
    composeRule.onNodeWithText("BoxedItem-11").assertIsSelected()
  }

  @Test
  fun testConditionalUnmountAndEmptySameFrameRefillKeepConsistentPublicState() {
    var activeList by mutableStateOf<SnapshotStateList<String>?>(
      mutableStateListOf<String>().also { list ->
        repeat(8) { list.add("Item-$it") }
      },
    )
    val verticalState = WheelPickerState(initialIndex = 3)
    val observedSnaps = mutableListOf<Int>()

    composeRule.setContent {
      verticalState.SnappedIndex { observedSnaps.add(it) }

      Column {
        activeList?.also { items ->
          VerticalWheelPicker(
            count = { items.size },
            state = verticalState,
            key = { items[it] },
            itemHeight = 48.dp,
            unfocusedCount = 2,
          ) { index ->
            BasicText(text = items[index])
          }
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(3, verticalState.snappedIndex)
    assertEquals(listOf(3), observedSnaps)

    // 1. 条件渲染卸载滚轮后公开状态归位为 (-1, -1, null)
    composeRule.runOnUiThread {
      activeList = null
    }
    composeRule.waitForIdle()
    assertEquals(-1, verticalState.currentIndex)
    assertEquals(-1, verticalState.snappedIndex)
    assertNull(verticalState.scrollPercentageOf(3))

    // 2. 重新挂载空列表后回填数据，重组恢复前公开状态保持 (-1, -1, null)，重组后恢复至 3
    val backingList = mutableStateListOf<String>()
    composeRule.runOnUiThread {
      activeList = backingList
    }
    composeRule.waitForIdle()
    assertEquals(-1, verticalState.currentIndex)
    assertEquals(-1, verticalState.snappedIndex)
    assertNull(verticalState.scrollPercentageOf(0))

    var preRecomposeCurrent = 99
    var preRecomposeSnapped = 99
    composeRule.runOnUiThread {
      repeat(8) { backingList.add("Restored-$it") }
      preRecomposeCurrent = verticalState.currentIndex
      preRecomposeSnapped = verticalState.snappedIndex
    }
    assertEquals(-1, preRecomposeCurrent)
    assertEquals(-1, preRecomposeSnapped)

    composeRule.waitForIdle()
    assertEquals(3, verticalState.currentIndex)
    assertEquals(3, verticalState.snappedIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(3))
    composeRule.onNodeWithText("Restored-3").assertIsDisplayed()
    composeRule.onNodeWithText("Restored-3").assertIsSelected()
  }
}

/** 校验垂直滚轮目标条目在可见裁剪边界下居中且无障碍选中语义为 true */
private fun assertWheelItemCenteredVisibleAndSelected(
  wheelNode: SemanticsNode,
  itemNode: SemanticsNode,
  expectedItemSizePx: Float,
) {
  val wheelUnclipped = wheelNode.unclippedBoundsInRoot()
  val itemClipped = itemNode.boundsInRoot
  assertFalse(itemClipped.isEmpty)
  assertEquals(true, itemNode.config.getOrNull(SemanticsProperties.Selected))
  assertTrue(abs(itemClipped.center.y - wheelUnclipped.center.y) <= 1.5f)
  assertTrue(abs(itemClipped.height - expectedItemSizePx) <= 1.5f)
}

private fun SemanticsNode.unclippedBoundsInRoot(): Rect {
  return Rect(
    offset = positionInRoot,
    size = size.toSize(),
  )
}
