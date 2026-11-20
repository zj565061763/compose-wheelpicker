package com.sd.lib.compose.wheelpicker

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    var hugeItemMeasuredHeight = 0
    var hugeFocusMeasuredHeight = 0
    val tightState = WheelPickerState(initialIndex = 2)
    val hugeVerticalState = WheelPickerState(initialIndex = 0)
    val hugeHorizontalState = WheelPickerState(initialIndex = 0)

    composeRule.setContent {
      val density = LocalDensity.current
      val itemSizeDp = 40.dp
      val crossAxisDp = 96.dp
      expectedMainAxisPx = with(density) { itemSizeDp.roundToPx() * 5 }
      expectedCrossAxisPx = with(density) { crossAxisDp.roundToPx() }

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

      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(modifier = Modifier.size(width = 120.dp, height = 160.dp)) {
          VerticalWheelPicker(
            count = { 3 },
            state = hugeVerticalState,
            itemHeight = 400_000.dp,
            unfocusedCount = 2,
            focus = {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .onSizeChanged { hugeFocusMeasuredHeight = it.height },
              )
            },
          ) { index ->
            Box(
              modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                  if (index == 0) {
                    hugeItemMeasuredHeight = it.height
                  }
                },
            ) {
              BasicText(text = "HV-$index")
            }
          }
        }

        Box(
          modifier = Modifier
            .width(IntrinsicSize.Max)
            .height(IntrinsicSize.Min),
        ) {
          HorizontalWheelPicker(
            count = { 1 },
            state = hugeHorizontalState,
            itemWidth = 400_000.dp,
            unfocusedCount = 2,
            focus = {
              Box(modifier = Modifier.height(48.dp))
            },
          ) { index ->
            BasicText(text = "HH-$index")
          }
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(expectedCrossAxisPx, verticalMeasuredSize.width)
    assertEquals(expectedMainAxisPx, verticalMeasuredSize.height)
    assertEquals(expectedMainAxisPx, horizontalMeasuredSize.width)
    assertEquals(expectedCrossAxisPx, horizontalMeasuredSize.height)
    assertEquals(2, tightState.snappedIndex)
    assertEquals(0f, tightState.scrollPercentageOf(2))
    assertEquals(MAX_CONSTRAINT_AXIS_PX, hugeItemMeasuredHeight)
    assertEquals(MAX_CONSTRAINT_AXIS_PX, hugeFocusMeasuredHeight)
    assertEquals(0, hugeVerticalState.snappedIndex)
    assertEquals(0f, hugeVerticalState.scrollPercentageOf(0))
    assertEquals(1f, hugeVerticalState.scrollPercentageOf(1))
    assertEquals(0, hugeHorizontalState.snappedIndex)
    assertEquals(0f, hugeHorizontalState.scrollPercentageOf(0))
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Test
  fun testModalBottomSheetAndMultiFrameOnPreFlingNestedScrollSafety() {
    val state = WheelPickerState(initialIndex = 20)
    var preFlingInvoked = false
    var wheelMovingDuringPreFling = false
    var itemHeightPx = 0
    var touchSlopPx = 0f

    val multiFramePreFlingConnection = object : NestedScrollConnection {
      override suspend fun onPreFling(available: Velocity): Velocity {
        preFlingInvoked = true
        repeat(4) {
          withFrameNanos { }
          if (state.isScrollInProgress) {
            wheelMovingDuringPreFling = true
          }
        }
        return Velocity.Zero
      }
    }

    composeRule.setContent {
      val density = LocalDensity.current
      val viewConfiguration = LocalViewConfiguration.current
      itemHeightPx = with(density) { 40.dp.roundToPx() }
      touchSlopPx = viewConfiguration.touchSlop

      val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
      ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(multiFramePreFlingConnection),
        ) {
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

    assertTrue("Expected NestedScrollConnection.onPreFling to be invoked", preFlingInvoked)
    assertFalse(
      "Auto-snap must not start prematurely while onPreFling is still suspended",
      wheelMovingDuringPreFling,
    )
    assertTrue("Expected fling to advance past initial index 20, actual=${state.snappedIndex}", state.snappedIndex > 22)
    assertEquals(state.currentIndex, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(state.snappedIndex))

    // 拖动至非零偏移后发送触摸取消，父容器多帧 onPreFling 完成前同样不得提前启动自动吸附
    preFlingInvoked = false
    wheelMovingDuringPreFling = false

    composeRule.onNodeWithTag("sheetWheel").performTouchInput {
      down(center)
      moveBy(Offset(0f, -(touchSlopPx + itemHeightPx * 0.4f)))
    }
    composeRule.waitForIdle()
    val midDragPct = state.scrollPercentageOf(state.currentIndex)!!
    assertTrue("Expected non-zero scroll offset before touch cancel, actual=$midDragPct", abs(midDragPct) > 0.02f)

    composeRule.onNodeWithTag("sheetWheel").performTouchInput {
      cancel()
    }
    composeRule.waitForIdle()

    assertTrue("Expected NestedScrollConnection.onPreFling to be invoked after touch cancel", preFlingInvoked)
    assertFalse(
      "Auto-snap must not start prematurely after touch cancel while onPreFling is still suspended",
      wheelMovingDuringPreFling,
    )
    assertEquals(state.currentIndex, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(state.snappedIndex))
  }

  @Test
  fun testConcurrentGestureInterruptMidAnimationAndSubsequentPrepend() {
    val state = WheelPickerState(initialIndex = 0)
    var items by mutableStateOf((0..99).toList())
    lateinit var composeScope: CoroutineScope

    composeRule.setContent {
      composeScope = rememberCoroutineScope()
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
    composeRule.runOnUiThread {
      composeScope.launch {
        state.animateScrollToIndex(80)
      }
    }

    var steps = 0
    while (state.currentIndex < 8 && steps < 60) {
      composeRule.mainClock.advanceTimeByFrame()
      steps++
    }
    assertTrue(state.currentIndex >= 8)

    // 用户手指按下并小幅拖动打断正在飞行的编程式动画
    composeRule.onNodeWithTag("interruptWheel").performTouchInput {
      down(center)
      moveBy(Offset(0f, -24f))
      up()
    }
    composeRule.mainClock.autoAdvance = true
    composeRule.waitForIdle()

    val stoppedKey = items[state.snappedIndex]
    assertTrue("Expected stopped key > 0, actual=$stoppedKey", stoppedKey > 0)

    // 后续头插 10 项，LazyList 原生 key 保持停稳条目且 snappedIndex 同步更新
    composeRule.runOnUiThread {
      items = (-10..-1).toList() + items
    }
    composeRule.waitForIdle()
    assertEquals(stoppedKey, items[state.snappedIndex])
  }

  @Test
  fun testHorizontalWheelPickerRtlVisualPercentageAndFocusIndicators() {
    val rtlState = WheelPickerState(initialIndex = 2)

    composeRule.setContent {
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

      WheelPickerFocusVertical(
        modifier = Modifier
          .size(80.dp, 40.dp)
          .testTag("focusVerticalNegativeThickness"),
        thickness = (-1).dp,
      )
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("focusHorizontalHairline").assertIsDisplayed()
    composeRule.onNodeWithTag("focusVerticalTransparent").assertIsDisplayed()
    composeRule.onNodeWithTag("focusVerticalNegativeThickness").assertIsDisplayed()
    composeRule.onNodeWithTag("rtlDisplay-2", useUnmergedTree = true).assertIsDisplayed()

    val centerPct = rtlState.scrollPercentageOf(2)!!
    val visualRightPct = rtlState.scrollPercentageOf(1)!!
    val visualLeftPct = rtlState.scrollPercentageOf(3)!!
    assertEquals(0f.toRawBits(), centerPct.toRawBits())
    assertTrue("Expected index 1 (visual right in RTL) > 0f, actual=$visualRightPct", visualRightPct > 0f)
    assertTrue("Expected index 3 (visual left in RTL) < 0f, actual=$visualLeftPct", visualLeftPct < 0f)
  }

  @Test
  fun testNonZeroOffsetShrinkClampsSnappedIndexAndRemountScrollToIndex() {
    val state = WheelPickerState(initialIndex = 4)
    val stateList = mutableStateListOf("A", "B", "C", "D", "E")
    var visible by mutableStateOf(true)
    var itemHeightPx = 0
    var clocklessExceptionCaught = false
    val rawMainScope = CoroutineScope(Dispatchers.Main.immediate)
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

    // 2. 卸载隐藏期间重排列表并调用 scrollToIndex，重新挂载后按新索引定位且不受残留旧 key 影响
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

    composeRule.runOnUiThread {
      stateList.clear()
      stateList.addAll(listOf("C", "A", "B") + (0..26).map { "Item-$it" })
      mainScope.launch {
        state.scrollToIndex(2)
      }
      visible = true
    }
    composeRule.waitForIdle()
    assertEquals(2, state.snappedIndex)
    assertEquals("B", stateList[state.snappedIndex])

    // 3. 重挂载摆放完成后在无帧时钟作用域调用动画抛异常不残留状态，后续手势滑动仍正常更新 snappedIndex
    composeRule.runOnUiThread {
      visible = false
    }
    composeRule.waitForIdle()
    composeRule.runOnUiThread {
      visible = true
    }
    composeRule.waitForIdle()
    composeRule.runOnUiThread {
      rawMainScope.launch {
        try {
          state.animateScrollToIndex(0)
        } catch (_: IllegalStateException) {
          clocklessExceptionCaught = true
        }
      }
    }
    composeRule.waitForIdle()
    assertTrue(clocklessExceptionCaught)
    assertEquals(2, state.snappedIndex)

    composeRule.onNodeWithTag("shrinkWheel").performTouchInput {
      swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80L)
    }
    composeRule.waitForIdle()
    assertTrue(state.currentIndex > 2)
    assertEquals(state.currentIndex, state.snappedIndex)
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
  fun testMeasuredBeforeFirstPlacementProgrammaticScrollDoesNotHang() {
    val scrollState = WheelPickerState(initialIndex = 0)
    val animateState = WheelPickerState(initialIndex = 0)
    lateinit var composeScope: CoroutineScope
    var showScrollWheel by mutableStateOf(true)
    var scrollJumpTarget by mutableStateOf(8)
    var scrollMeasured by mutableStateOf(false)
    var allowScrollPlace by mutableStateOf(false)
    var totalItemsDuringScrollJump = -1

    var animateMeasured by mutableStateOf(false)
    var allowAnimatePlace by mutableStateOf(false)
    var totalItemsDuringAnimateJump = -1

    val remountAnimateState = WheelPickerState(initialIndex = 0)
    var remountStage by mutableStateOf(0)
    var allowRemountSecondMeasure by mutableStateOf(false)

    composeRule.setContent {
      composeScope = rememberCoroutineScope()

      if (showScrollWheel) {
        LaunchedEffect(scrollMeasured, scrollJumpTarget) {
          if (scrollMeasured && !allowScrollPlace) {
            totalItemsDuringScrollJump = scrollState.lazyListState.layoutInfo.totalItemsCount
            scrollState.scrollToIndex(scrollJumpTarget)
            allowScrollPlace = true
          }
        }

        Layout(
          content = {
            VerticalWheelPicker(
              count = { 12 },
              state = scrollState,
              itemHeight = 40.dp,
              unfocusedCount = 2,
            ) { index ->
              BasicText(text = "Immediate-$index")
            }
          },
        ) { measurables, constraints ->
          val placeable = measurables.first().measure(constraints)
          scrollMeasured = true
          layout(placeable.width, placeable.height) {
            if (allowScrollPlace) {
              placeable.placeRelative(0, 0)
            }
          }
        }
      }

      LaunchedEffect(animateMeasured) {
        if (animateMeasured) {
          totalItemsDuringAnimateJump = animateState.lazyListState.layoutInfo.totalItemsCount
          animateState.animateScrollToIndex(8)
          allowAnimatePlace = true
        }
      }

      Layout(
        content = {
          HorizontalWheelPicker(
            count = { 12 },
            state = animateState,
            itemWidth = 40.dp,
            unfocusedCount = 2,
          ) { index ->
            BasicText(text = "Animated-$index")
          }
        },
      ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints)
        animateMeasured = true
        layout(placeable.width, placeable.height) {
          if (allowAnimatePlace) {
            placeable.placeRelative(0, 0)
          }
        }
      }

      when (remountStage) {
        0 -> {
          VerticalWheelPicker(
            count = { 1 },
            state = remountAnimateState,
            itemHeight = 40.dp,
            unfocusedCount = 2,
          ) { index ->
            BasicText(text = "RemountOld-$index")
          }
        }
        2 -> {
          LaunchedEffect(Unit) {
            remountAnimateState.animateScrollToIndex(8)
            allowRemountSecondMeasure = true
          }
          Layout(
            content = {
              VerticalWheelPicker(
                count = { 20 },
                state = remountAnimateState,
                itemHeight = 40.dp,
                unfocusedCount = 2,
              ) { index ->
                BasicText(text = "RemountNew-$index")
              }
            },
          ) { measurables, constraints ->
            if (!allowRemountSecondMeasure) {
              layout(0, 0) {}
            } else {
              val placeable = measurables.first().measure(constraints)
              layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
              }
            }
          }
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(12, totalItemsDuringScrollJump)
    assertTrue(allowScrollPlace)
    assertEquals(8, scrollState.snappedIndex)
    assertEquals(8, scrollState.currentIndex)
    assertEquals(0f, scrollState.scrollPercentageOf(8))
    composeRule.onNodeWithText("Immediate-8").assertIsDisplayed()
    composeRule.onNodeWithText("Immediate-8").assertIsSelected()

    assertEquals(12, totalItemsDuringAnimateJump)
    assertTrue(allowAnimatePlace)
    assertEquals(8, animateState.snappedIndex)
    assertEquals(8, animateState.currentIndex)
    assertEquals(0f, animateState.scrollPercentageOf(8))
    composeRule.onNodeWithText("Animated-8").assertIsDisplayed()
    composeRule.onNodeWithText("Animated-8").assertIsSelected()

    // 首次摆放完成后再次调用 animateScrollToIndex 可正常动画滚动到新目标
    composeRule.runOnUiThread {
      composeScope.launch {
        animateState.animateScrollToIndex(3)
      }
    }
    composeRule.waitForIdle()
    assertEquals(3, animateState.snappedIndex)
    assertEquals(3, animateState.currentIndex)
    assertEquals(0f, animateState.scrollPercentageOf(3))
    composeRule.onNodeWithText("Animated-3").assertIsSelected()

    // 卸载后复用同一 WheelPickerState 重新挂载到先测量后摆放的容器时，首次摆放前跳转同样不挂起
    composeRule.runOnUiThread {
      showScrollWheel = false
    }
    composeRule.waitForIdle()
    assertEquals(-1, scrollState.snappedIndex)

    composeRule.runOnUiThread {
      totalItemsDuringScrollJump = -1
      scrollMeasured = false
      allowScrollPlace = false
      scrollJumpTarget = 4
      showScrollWheel = true
    }
    composeRule.waitForIdle()
    assertEquals(12, totalItemsDuringScrollJump)
    assertTrue(allowScrollPlace)
    assertEquals(4, scrollState.snappedIndex)
    assertEquals(4, scrollState.currentIndex)
    assertEquals(0f, scrollState.scrollPercentageOf(4))
    composeRule.onNodeWithText("Immediate-4").assertIsDisplayed()
    composeRule.onNodeWithText("Immediate-4").assertIsSelected()

    // 旧 1 项滚轮卸载后复用同一状态挂载 20 项新滚轮，首次测量前调用 animateScrollToIndex(8) 不丢失目标
    assertEquals(0, remountAnimateState.snappedIndex)
    composeRule.onNodeWithText("RemountOld-0").assertIsSelected()

    composeRule.runOnUiThread {
      remountStage = 1
    }
    composeRule.waitForIdle()
    assertEquals(-1, remountAnimateState.snappedIndex)

    composeRule.runOnUiThread {
      allowRemountSecondMeasure = false
      remountStage = 2
    }
    composeRule.waitForIdle()
    assertTrue(allowRemountSecondMeasure)
    assertEquals(8, remountAnimateState.snappedIndex)
    assertEquals(8, remountAnimateState.currentIndex)
    assertEquals(0f, remountAnimateState.scrollPercentageOf(8))
    composeRule.onNodeWithText("RemountNew-8").assertIsDisplayed()
    composeRule.onNodeWithText("RemountNew-8").assertIsSelected()
  }

  @Test
  fun testHeadShrinkDuringSuspendedPreFlingPreservesStableKey() {
    var items by mutableStateOf((0..13).toList())
    val state = WheelPickerState(initialIndex = 8)
    lateinit var composeScope: CoroutineScope
    var itemHeightPx = 0f
    var touchSlopPx = 0f
    var holdNextPreFling = true
    var preFlingEntered = CompletableDeferred<Unit>()
    var releasePreFling = CompletableDeferred<Unit>()

    val suspendPreFlingConnection = object : NestedScrollConnection {
      override suspend fun onPreFling(available: Velocity): Velocity {
        if (holdNextPreFling) {
          holdNextPreFling = false
          val entered = preFlingEntered
          val release = releasePreFling
          entered.complete(Unit)
          release.await()
        }
        return Velocity.Zero
      }
    }

    composeRule.setContent {
      composeScope = rememberCoroutineScope()
      val density = LocalDensity.current
      val viewConfig = LocalViewConfiguration.current
      itemHeightPx = with(density) { 48.dp.toPx() }
      touchSlopPx = viewConfig.touchSlop

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .nestedScroll(suspendPreFlingConnection),
      ) {
        VerticalWheelPicker(
          modifier = Modifier.testTag("headShrinkWheel"),
          count = { items.size },
          state = state,
          key = { items[it] },
          itemHeight = 48.dp,
          unfocusedCount = 2,
        ) { index ->
          BasicText(text = "Key-${items[index]}")
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(8, state.snappedIndex)
    assertEquals(8, items[state.snappedIndex])

    try {
      // 1. 轻拖不到半格后松手，使父容器挂起在 onPreFling（关闭自动推进防止 waitForIdle 直接跑完 60 帧超时）
      composeRule.mainClock.autoAdvance = false
      composeRule.onNodeWithTag("headShrinkWheel").performTouchInput {
        down(center)
        val steps = 10
        val stepDeltaY = -(touchSlopPx + itemHeightPx * 0.25f) / steps
        repeat(steps) {
          advanceEventTime(30L)
          moveBy(Offset(0f, stepDeltaY))
        }
        advanceEventTime(120L)
        up()
      }
      composeRule.mainClock.advanceTimeByFrame()
      assertTrue("Expected preFling to be entered", preFlingEntered.isCompleted)
      assertEquals(8, state.lazyListState.firstVisibleItemIndex)
      assertTrue(
        "Expected positive offset < half item, actual=${state.lazyListState.firstVisibleItemScrollOffset}",
        state.lazyListState.firstVisibleItemScrollOffset in 1..<(itemHeightPx * 0.5f).toInt(),
      )

      // 在父容器 onPreFling 尚未返回时删除列表开头 6 项，key = 8 从索引 8 移至索引 2，交接完成前 snappedIndex 先夹紧为 7
      composeRule.runOnUiThread {
        items = items.drop(6)
      }
      composeRule.mainClock.advanceTimeByFrame()
      composeRule.mainClock.advanceTimeByFrame()
      assertEquals(7, state.snappedIndex)
      assertEquals(2, state.lazyListState.firstVisibleItemIndex)
      assertEquals(2, state.currentIndex)

      // 恢复父容器 onPreFling 后，滚轮应吸附在索引 2（key = 8），而非误选新末项索引 7（key = 13）
      composeRule.mainClock.autoAdvance = true
      releasePreFling.complete(Unit)
      composeRule.waitForIdle()

      assertEquals(2, state.snappedIndex)
      assertEquals(2, state.currentIndex)
      assertEquals(8, items[state.snappedIndex])
      assertEquals(0f, state.scrollPercentageOf(2))
      composeRule.onNodeWithText("Key-8").assertIsSelected()
      composeRule.onNodeWithText("Key-8").assertIsDisplayed()

      // 2. 拖到旧索引 6 恰好对齐后松手挂起 onPreFling，删除开头 6 项时 snappedIndex 先夹紧至 7（不误变为 6），交接完成后更新为 0
      composeRule.runOnUiThread {
        items = (0..13).toList()
      }
      composeRule.waitForIdle()
      composeRule.runOnUiThread {
        composeScope.launch {
          state.scrollToIndex(8)
        }
      }
      composeRule.waitForIdle()
      assertEquals(8, state.snappedIndex)

      preFlingEntered = CompletableDeferred()
      releasePreFling = CompletableDeferred()
      holdNextPreFling = true
      composeRule.mainClock.autoAdvance = false

      composeRule.onNodeWithTag("headShrinkWheel").performTouchInput {
        down(center)
        moveBy(Offset(0f, touchSlopPx))
        val steps = 10
        val stepDeltaY = (itemHeightPx * 2f) / steps
        repeat(steps) {
          advanceEventTime(30L)
          moveBy(Offset(0f, stepDeltaY))
        }
        advanceEventTime(120L)
        up()
      }
      composeRule.mainClock.advanceTimeByFrame()
      assertTrue("Expected second preFling to be entered", preFlingEntered.isCompleted)
      assertEquals(6, state.lazyListState.firstVisibleItemIndex)
      assertEquals(0, state.lazyListState.firstVisibleItemScrollOffset)
      assertEquals(8, state.snappedIndex)

      composeRule.runOnUiThread {
        items = items.drop(6)
      }
      composeRule.mainClock.advanceTimeByFrame()
      composeRule.mainClock.advanceTimeByFrame()
      assertEquals(7, state.snappedIndex)
      assertEquals(0, state.lazyListState.firstVisibleItemIndex)
      assertEquals(0, state.currentIndex)

      composeRule.mainClock.autoAdvance = true
      releasePreFling.complete(Unit)
      composeRule.waitForIdle()

      assertEquals(0, state.snappedIndex)
      assertEquals(0, state.currentIndex)
      assertEquals(6, items[state.snappedIndex])
      assertEquals(0f, state.scrollPercentageOf(0))
      composeRule.onNodeWithText("Key-6").assertIsSelected()
      composeRule.onNodeWithText("Key-6").assertIsDisplayed()
    } finally {
      composeRule.mainClock.autoAdvance = true
    }
  }

  @Test
  fun testShrinkAndImmediateScrollToIndexBeforeRecompositionDoesNotCrash() {
    val stateList = mutableStateListOf<String>().also { list ->
      repeat(12) { list.add("StateItem-$it") }
    }
    var boxedItems by mutableStateOf((0..11).map { "BoxedItem-$it" })
    val verticalState = WheelPickerState(initialIndex = 8)
    val horizontalState = WheelPickerState(initialIndex = 8)
    lateinit var composeScope: CoroutineScope
    var snappedRightAfterVerticalJump = -1
    var snappedRightAfterHorizontalJump = -1

    composeRule.setContent {
      composeScope = rememberCoroutineScope()

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
        itemWidth = 48.dp,
        unfocusedCount = 2,
      ) { index ->
        BasicText(text = boxedItems[index])
      }
    }

    composeRule.waitForIdle()
    assertEquals(8, verticalState.snappedIndex)
    assertEquals(8, horizontalState.snappedIndex)

    // 同一次主线程回调中将 12 项缩容至 1 项并立即 launch scrollToIndex(0)，重测不越界且跳转返回当下已完成吸附结算
    composeRule.runOnUiThread {
      stateList.removeRange(1, 12)
      composeScope.launch {
        verticalState.scrollToIndex(0)
        snappedRightAfterVerticalJump = verticalState.snappedIndex
      }

      boxedItems = listOf("BoxedItem-0")
      composeScope.launch {
        horizontalState.scrollToIndex(0)
        snappedRightAfterHorizontalJump = horizontalState.snappedIndex
      }
    }

    composeRule.waitForIdle()
    assertEquals(0, snappedRightAfterVerticalJump)
    assertEquals(0, verticalState.snappedIndex)
    assertEquals(0, verticalState.currentIndex)
    assertEquals("StateItem-0", stateList[verticalState.snappedIndex])
    composeRule.onNodeWithText("StateItem-0").assertIsDisplayed()
    composeRule.onNodeWithText("StateItem-0").assertIsSelected()

    assertEquals(0, snappedRightAfterHorizontalJump)
    assertEquals(0, horizontalState.snappedIndex)
    assertEquals(0, horizontalState.currentIndex)
    assertEquals("BoxedItem-0", boxedItems[horizontalState.snappedIndex])
    composeRule.onNodeWithText("BoxedItem-0").assertIsDisplayed()
    composeRule.onNodeWithText("BoxedItem-0").assertIsSelected()
  }

  @Test
  fun testExpandAndImmediateScrollToIndexBeforeRecompositionDoesNotCrash() {
    val stateList = mutableStateListOf("StateItem-0")
    var boxedItems by mutableStateOf(listOf("BoxedItem-0"))
    val verticalState = WheelPickerState(initialIndex = 0)
    val horizontalState = WheelPickerState(initialIndex = 0)
    lateinit var composeScope: CoroutineScope
    var snappedRightAfterVerticalJump = -1
    var snappedRightAfterHorizontalJump = -1

    composeRule.setContent {
      composeScope = rememberCoroutineScope()

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
    assertEquals(0, verticalState.snappedIndex)
    assertEquals(0, horizontalState.snappedIndex)

    // 同一次主线程回调中将 1 项扩容至 6 项并立即在重组前调用 scrollToIndex(5)，key 与 content 读取同一状态不越界
    composeRule.runOnUiThread {
      for (index in 1..5) {
        stateList.add("StateItem-$index")
      }
      composeScope.launch {
        verticalState.scrollToIndex(5)
        snappedRightAfterVerticalJump = verticalState.snappedIndex
      }

      boxedItems = (0..5).map { "BoxedItem-$it" }
      composeScope.launch {
        horizontalState.scrollToIndex(5)
        snappedRightAfterHorizontalJump = horizontalState.snappedIndex
      }
    }

    composeRule.waitForIdle()
    assertEquals(5, snappedRightAfterVerticalJump)
    assertEquals(5, verticalState.snappedIndex)
    assertEquals(5, verticalState.currentIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(5))
    assertEquals("StateItem-5", stateList[verticalState.snappedIndex])
    composeRule.onNodeWithText("StateItem-5").assertIsDisplayed()
    composeRule.onNodeWithText("StateItem-5").assertIsSelected()

    assertEquals(5, snappedRightAfterHorizontalJump)
    assertEquals(5, horizontalState.snappedIndex)
    assertEquals(5, horizontalState.currentIndex)
    assertEquals(0f, horizontalState.scrollPercentageOf(5))
    assertEquals("BoxedItem-5", boxedItems[horizontalState.snappedIndex])
    composeRule.onNodeWithText("BoxedItem-5").assertIsDisplayed()
    composeRule.onNodeWithText("BoxedItem-5").assertIsSelected()

    // 再次在重组前从 6 项扩容至 12 项并立即调用 animateScrollToIndex(11)，动画正常落地至新末项
    composeRule.runOnUiThread {
      for (index in 6..11) {
        stateList.add("StateItem-$index")
      }
      composeScope.launch {
        verticalState.animateScrollToIndex(11)
      }

      boxedItems = (0..11).map { "BoxedItem-$it" }
      composeScope.launch {
        horizontalState.animateScrollToIndex(11)
      }
    }

    composeRule.waitForIdle()
    assertEquals(11, verticalState.snappedIndex)
    assertEquals(11, verticalState.currentIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(11))
    assertEquals("StateItem-11", stateList[verticalState.snappedIndex])
    composeRule.onNodeWithText("StateItem-11").assertIsDisplayed()
    composeRule.onNodeWithText("StateItem-11").assertIsSelected()

    assertEquals(11, horizontalState.snappedIndex)
    assertEquals(11, horizontalState.currentIndex)
    assertEquals(0f, horizontalState.scrollPercentageOf(11))
    assertEquals("BoxedItem-11", boxedItems[horizontalState.snappedIndex])
    composeRule.onNodeWithText("BoxedItem-11").assertIsDisplayed()
    composeRule.onNodeWithText("BoxedItem-11").assertIsSelected()
  }

  @Test
  fun testSameFrameMutateScrollAndRestoreOriginalCountSyncsState() {
    val stateList = mutableStateListOf<String>().also { list ->
      repeat(10) { list.add("Init-$it") }
    }
    var boxedItems by mutableStateOf((0..9).map { "BoxedInit-$it" })
    val verticalState = WheelPickerState(initialIndex = 2)
    val horizontalState = WheelPickerState(initialIndex = 2)
    lateinit var composeScope: CoroutineScope

    composeRule.setContent {
      composeScope = rememberCoroutineScope()

      Column(modifier = Modifier.background(Color.White)) {
        VerticalWheelPicker(
          modifier = Modifier
            .width(120.dp)
            .testTag("sameFrameVerticalWheel"),
          count = { stateList.size },
          state = verticalState,
          key = { stateList[it] },
          itemHeight = 48.dp,
          unfocusedCount = 2,
          focus = {},
        ) { index ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Red),
          ) {
            BasicText(text = stateList[index])
          }
        }

        HorizontalWheelPicker(
          modifier = Modifier
            .height(64.dp)
            .testTag("sameFrameHorizontalWheel"),
          count = { boxedItems.size },
          state = horizontalState,
          itemWidth = 48.dp,
          unfocusedCount = 2,
          focus = {},
        ) { index ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Blue),
          ) {
            BasicText(text = boxedItems[index])
          }
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(2, verticalState.snappedIndex)
    assertEquals(2, horizontalState.snappedIndex)

    // 1. 同帧内 10 -> 0 -> scrollToIndex(7) -> 回填 10 项：snappedIndex 更新为 7 且默认展示非透明
    composeRule.runOnUiThread {
      composeScope.launch {
        stateList.clear()
        verticalState.scrollToIndex(7)
        repeat(10) { stateList.add("Refilled-$it") }
      }

      composeScope.launch {
        boxedItems = emptyList()
        horizontalState.scrollToIndex(7)
        boxedItems = (0..9).map { "BoxedRefilled-$it" }
      }
    }

    composeRule.waitForIdle()
    assertEquals(7, verticalState.currentIndex)
    assertEquals(7, verticalState.snappedIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(7))
    assertEquals("Refilled-7", stateList[verticalState.snappedIndex])
    composeRule.onNodeWithText("Refilled-7").assertIsDisplayed()
    composeRule.onNodeWithText("Refilled-7").assertIsSelected()

    assertEquals(7, horizontalState.currentIndex)
    assertEquals(7, horizontalState.snappedIndex)
    assertEquals(0f, horizontalState.scrollPercentageOf(7))
    assertEquals("BoxedRefilled-7", boxedItems[horizontalState.snappedIndex])
    composeRule.onNodeWithText("BoxedRefilled-7").assertIsDisplayed()
    composeRule.onNodeWithText("BoxedRefilled-7").assertIsSelected()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val verticalPixels = composeRule.onNodeWithTag("sameFrameVerticalWheel").captureToImage().toPixelMap()
      val verticalCenter = verticalPixels[verticalPixels.width / 2, verticalPixels.height / 2]
      assertTrue("Expected opaque red item at center, but got $verticalCenter", verticalCenter.red > 0.8f && verticalCenter.green < 0.2f)

      val horizontalPixels = composeRule.onNodeWithTag("sameFrameHorizontalWheel").captureToImage().toPixelMap()
      val horizontalCenter = horizontalPixels[horizontalPixels.width / 2, horizontalPixels.height / 2]
      assertTrue("Expected opaque blue item at center, but got $horizontalCenter", horizontalCenter.blue > 0.8f && horizontalCenter.red < 0.2f)
    }

    // 2. 同帧内 10 -> 20 -> scrollToIndex(17) -> 缩回 10 项：currentIndex 与 snappedIndex 均收敛至 9 不越界
    composeRule.runOnUiThread {
      composeScope.launch {
        repeat(10) { stateList.add("Expanded-${it + 10}") }
        verticalState.scrollToIndex(17)
        stateList.removeRange(10, 20)
      }

      composeScope.launch {
        boxedItems = (0..19).map { "BoxedExpanded-$it" }
        horizontalState.scrollToIndex(17)
        boxedItems = boxedItems.take(10)
      }
    }

    composeRule.waitForIdle()
    assertEquals(9, verticalState.currentIndex)
    assertEquals(9, verticalState.snappedIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(9))
    assertEquals("Refilled-9", stateList[verticalState.snappedIndex])
    composeRule.onNodeWithText("Refilled-9").assertIsDisplayed()
    composeRule.onNodeWithText("Refilled-9").assertIsSelected()

    assertEquals(9, horizontalState.currentIndex)
    assertEquals(9, horizontalState.snappedIndex)
    assertEquals(0f, horizontalState.scrollPercentageOf(9))
    assertEquals("BoxedExpanded-9", boxedItems[horizontalState.snappedIndex])
    composeRule.onNodeWithText("BoxedExpanded-9").assertIsDisplayed()
    composeRule.onNodeWithText("BoxedExpanded-9").assertIsSelected()
  }

  @Test
  fun testClearAndShrinkListImmediatelyUpdatesPublicStateInSameCallbackAndComposition() {
    val stateList = mutableStateListOf<String>().also { list ->
      repeat(8) { list.add("Item-$it") }
    }
    var boxedItems by mutableStateOf((0..7).map { "Boxed-$it" })
    val verticalState = WheelPickerState(initialIndex = 3)
    val horizontalState = WheelPickerState(initialIndex = 3)

    composeRule.setContent {
      Column {
        // 先于滚轮 SideEffect 在组合阶段按 snappedIndex >= 0 读取列表，清空或缩容重组时不得越界
        val verticalHeader = if (verticalState.snappedIndex >= 0) {
          "V:${stateList[verticalState.snappedIndex]}"
        } else {
          "V:Empty"
        }
        val horizontalHeader = if (horizontalState.snappedIndex >= 0) {
          "H:${boxedItems[horizontalState.snappedIndex]}"
        } else {
          "H:Empty"
        }
        BasicText(text = verticalHeader)
        BasicText(text = horizontalHeader)

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
          itemWidth = 48.dp,
          unfocusedCount = 2,
        ) { index ->
          BasicText(text = boxedItems[index])
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(3, verticalState.currentIndex)
    assertEquals(3, verticalState.snappedIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(3))
    composeRule.onNodeWithText("V:Item-3").assertIsDisplayed()
    composeRule.onNodeWithText("H:Boxed-3").assertIsDisplayed()

    // 1. 同一主线程回调内执行 clear() 并立即读取公开状态，严格返回 (-1, -1, null)
    var immediateVerticalCurrent = 99
    var immediateVerticalSnapped = 99
    var immediateVerticalPercentage: Float? = 99f
    var immediateHorizontalCurrent = 99
    var immediateHorizontalSnapped = 99
    var immediateHorizontalPercentage: Float? = 99f

    composeRule.runOnUiThread {
      stateList.clear()
      immediateVerticalCurrent = verticalState.currentIndex
      immediateVerticalSnapped = verticalState.snappedIndex
      immediateVerticalPercentage = verticalState.scrollPercentageOf(3)

      boxedItems = emptyList()
      immediateHorizontalCurrent = horizontalState.currentIndex
      immediateHorizontalSnapped = horizontalState.snappedIndex
      immediateHorizontalPercentage = horizontalState.scrollPercentageOf(3)
    }

    assertEquals(-1, immediateVerticalCurrent)
    assertEquals(-1, immediateVerticalSnapped)
    assertNull(immediateVerticalPercentage)
    assertEquals(-1, immediateHorizontalCurrent)
    assertEquals(-1, immediateHorizontalSnapped)
    assertNull(immediateHorizontalPercentage)

    composeRule.waitForIdle()
    composeRule.onNodeWithText("V:Empty").assertIsDisplayed()
    composeRule.onNodeWithText("H:Empty").assertIsDisplayed()

    // 2. 重新回填列表后恢复至清空前选中的第 4 项（index = 3），随后缩容至 2 项时同回调与组合期均收敛至 1 不越界
    composeRule.runOnUiThread {
      repeat(8) { stateList.add("Restored-$it") }
      boxedItems = (0..7).map { "BoxedRestored-$it" }
    }
    composeRule.waitForIdle()
    assertEquals(3, verticalState.snappedIndex)
    assertEquals(3, horizontalState.snappedIndex)
    composeRule.onNodeWithText("V:Restored-3").assertIsDisplayed()
    composeRule.onNodeWithText("H:BoxedRestored-3").assertIsDisplayed()

    var shrunkVerticalSnapped = 99
    var shrunkHorizontalSnapped = 99
    composeRule.runOnUiThread {
      stateList.removeRange(2, 8)
      shrunkVerticalSnapped = verticalState.snappedIndex

      boxedItems = boxedItems.take(2)
      shrunkHorizontalSnapped = horizontalState.snappedIndex
    }
    assertEquals(1, shrunkVerticalSnapped)
    assertEquals(1, shrunkHorizontalSnapped)

    composeRule.waitForIdle()
    assertEquals(1, verticalState.snappedIndex)
    assertEquals(1, horizontalState.snappedIndex)
    composeRule.onNodeWithText("V:Restored-1").assertIsDisplayed()
    composeRule.onNodeWithText("H:BoxedRestored-1").assertIsDisplayed()
  }
}
