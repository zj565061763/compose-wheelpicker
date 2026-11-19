package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
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
          count = 20,
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
          count = 20,
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
          count = 10,
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
            count = 3,
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
            count = 1,
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
    lateinit var composeScope: CoroutineScope
    var preFlingInvoked = false
    var wheelMovingDuringPreFling = false
    var preFlingSuspendFrames = 4
    var programmaticJumpTargetDuringPreFling: Int? = null
    var programmaticJumpStep = 1
    var animateJumpTargetDuringPreFling: Int? = null
    var animateCancelledByStaleFling = false
    var snappedDuringPreFlingJump = -1
    var itemHeightPx = 0
    var touchSlopPx = 0f

    val multiFramePreFlingConnection = object : NestedScrollConnection {
      override suspend fun onPreFling(available: Velocity): Velocity {
        preFlingInvoked = true
        val jumpTarget = programmaticJumpTargetDuringPreFling
        val jumpStep = programmaticJumpStep
        val animateTarget = animateJumpTargetDuringPreFling
        repeat(preFlingSuspendFrames) { step ->
          withFrameNanos { }
          if (jumpTarget != null && step == jumpStep) {
            state.scrollToIndex(jumpTarget)
            snappedDuringPreFlingJump = state.snappedIndex
          } else if (animateTarget != null && step == 1) {
            composeScope.launch {
              try {
                state.animateScrollToIndex(animateTarget)
              } catch (_: kotlinx.coroutines.CancellationException) {
                animateCancelledByStaleFling = true
              }
            }
          } else if (jumpTarget == null && animateTarget == null && state.isScrollInProgress) {
            wheelMovingDuringPreFling = true
          }
        }
        return Velocity.Zero
      }
    }

    composeRule.setContent {
      composeScope = rememberCoroutineScope()
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
            count = 150,
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

    // 父容器 onPreFling 挂起超过 60 帧保护期后再调用 scrollToIndex(60)，恢复的旧 fling 同样不得覆盖 60
    preFlingInvoked = false
    preFlingSuspendFrames = 65
    programmaticJumpTargetDuringPreFling = 60
    programmaticJumpStep = 62

    composeRule.onNodeWithTag("sheetWheel").performTouchInput {
      swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80L)
    }
    composeRule.waitForIdle()

    assertTrue("Expected NestedScrollConnection.onPreFling to be invoked", preFlingInvoked)
    assertEquals(60, snappedDuringPreFlingJump)
    assertEquals(60, state.snappedIndex)
    assertEquals(60, state.currentIndex)
    assertEquals(0f, state.scrollPercentageOf(60))

    // 父容器 onPreFling 挂起期间启动 animateScrollToIndex(120)，已作废的旧 fling 不得抢占取消飞行中的动画
    composeRule.runOnUiThread {
      composeScope.launch {
        state.scrollToIndex(20)
      }
    }
    composeRule.waitForIdle()
    assertEquals(20, state.snappedIndex)

    programmaticJumpTargetDuringPreFling = null
    preFlingSuspendFrames = 4
    programmaticJumpStep = 1
    animateJumpTargetDuringPreFling = 120
    preFlingInvoked = false

    composeRule.onNodeWithTag("sheetWheel").performTouchInput {
      swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80L)
    }
    composeRule.waitForIdle()

    assertTrue("Expected NestedScrollConnection.onPreFling to be invoked", preFlingInvoked)
    assertFalse("Stale fling must not cancel in-flight animateScrollToIndex(120)", animateCancelledByStaleFling)
    assertEquals(120, state.snappedIndex)
    assertEquals(120, state.currentIndex)
    assertEquals(0f, state.scrollPercentageOf(120))

    // 后续新手势仍可正常滑动
    composeRule.runOnUiThread {
      composeScope.launch {
        state.scrollToIndex(60)
      }
    }
    composeRule.waitForIdle()
    animateJumpTargetDuringPreFling = null
    preFlingInvoked = false

    composeRule.onNodeWithTag("sheetWheel").performTouchInput {
      swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80L)
    }
    composeRule.waitForIdle()

    assertTrue("Expected subsequent gesture NestedScrollConnection.onPreFling to be invoked", preFlingInvoked)
    assertTrue("Expected subsequent fresh fling to advance past 60, actual=${state.snappedIndex}", state.snappedIndex > 62)
    assertEquals(state.currentIndex, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(state.snappedIndex))
  }

  @Test
  fun testOutOfOrderPreFlingResumptionDoesNotOverwriteSettledGesture() {
    val state = WheelPickerState(initialIndex = 20)
    lateinit var composeScope: CoroutineScope
    var holdNextPreFling = true
    var firstPreFlingEntered = CompletableDeferred<Unit>()
    var releaseFirstPreFling = CompletableDeferred<Unit>()
    var preFlingCount = 0

    val outOfOrderPreFlingConnection = object : NestedScrollConnection {
      override suspend fun onPreFling(available: Velocity): Velocity {
        preFlingCount++
        if (holdNextPreFling) {
          holdNextPreFling = false
          val entered = firstPreFlingEntered
          val release = releaseFirstPreFling
          entered.complete(Unit)
          release.await()
        } else {
          withFrameNanos { }
        }
        return Velocity.Zero
      }
    }

    composeRule.setContent {
      composeScope = rememberCoroutineScope()
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .nestedScroll(outOfOrderPreFlingConnection),
      ) {
        VerticalWheelPicker(
          modifier = Modifier.testTag("outOfOrderWheel"),
          count = 150,
          state = state,
          itemHeight = 40.dp,
          unfocusedCount = 2,
        ) { index ->
          BasicText(text = "Order-$index")
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(20, state.snappedIndex)

    // 1. 第一次手势向上快速滑动并挂起在父容器 onPreFling，随后调用 scrollToIndex(60)，再由第二次手势向下滑停稳后乱序放行第一次 onPreFling
    composeRule.onNodeWithTag("outOfOrderWheel").performTouchInput {
      swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80L)
    }
    composeRule.waitUntil(5_000L) { firstPreFlingEntered.isCompleted }
    composeRule.waitForIdle()
    assertEquals(1, preFlingCount)

    composeRule.runOnUiThread {
      composeScope.launch {
        state.scrollToIndex(60)
      }
    }
    composeRule.waitForIdle()
    assertEquals(60, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(60))

    composeRule.onNodeWithTag("outOfOrderWheel").performTouchInput {
      swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 200L)
    }
    composeRule.waitForIdle()
    assertEquals(2, preFlingCount)
    val settledAfterScrollAndSecondGesture = state.snappedIndex
    assertTrue(
      "Expected second gesture to settle below 60, actual=$settledAfterScrollAndSecondGesture",
      settledAfterScrollAndSecondGesture < 60,
    )
    assertEquals(settledAfterScrollAndSecondGesture, state.currentIndex)
    assertEquals(0f, state.scrollPercentageOf(settledAfterScrollAndSecondGesture))

    releaseFirstPreFling.complete(Unit)
    composeRule.waitForIdle()
    assertEquals(settledAfterScrollAndSecondGesture, state.snappedIndex)
    assertEquals(settledAfterScrollAndSecondGesture, state.currentIndex)
    assertEquals(0f, state.scrollPercentageOf(settledAfterScrollAndSecondGesture))

    // 2. 不经过显式 scrollToIndex，第一次向上滑动挂起在 onPreFling 期间直接执行第二次向下滑动并停稳，乱序放行第一次 onPreFling 同样不得覆盖
    composeRule.runOnUiThread {
      composeScope.launch {
        state.scrollToIndex(60)
      }
    }
    composeRule.waitForIdle()
    assertEquals(60, state.snappedIndex)

    firstPreFlingEntered = CompletableDeferred()
    releaseFirstPreFling = CompletableDeferred()
    holdNextPreFling = true

    composeRule.onNodeWithTag("outOfOrderWheel").performTouchInput {
      swipeUp(startY = bottom - 10f, endY = top + 10f, durationMillis = 80L)
    }
    composeRule.waitUntil(5_000L) { firstPreFlingEntered.isCompleted }
    composeRule.waitForIdle()
    assertEquals(3, preFlingCount)

    composeRule.onNodeWithTag("outOfOrderWheel").performTouchInput {
      swipeDown(startY = top + 10f, endY = bottom - 10f, durationMillis = 200L)
    }
    composeRule.waitForIdle()
    assertEquals(4, preFlingCount)
    val settledAfterDirectSecondGesture = state.snappedIndex
    assertTrue(
      "Expected direct second gesture to settle below 60, actual=$settledAfterDirectSecondGesture",
      settledAfterDirectSecondGesture < 60,
    )

    releaseFirstPreFling.complete(Unit)
    composeRule.waitForIdle()
    assertEquals(settledAfterDirectSecondGesture, state.snappedIndex)
    assertEquals(settledAfterDirectSecondGesture, state.currentIndex)
    assertEquals(0f, state.scrollPercentageOf(settledAfterDirectSecondGesture))
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
        count = items.size,
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
          count = 6,
          state = rtlState,
          itemWidth = 50.dp,
          unfocusedCount = 2,
          focus = {
            WheelPickerFocusHorizontal(
              modifier = Modifier.testTag("focusHorizontalZero"),
              thickness = 0.dp,
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

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("focusHorizontalZero").assertIsDisplayed()
    composeRule.onNodeWithTag("focusVerticalTransparent").assertIsDisplayed()

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
    var triggerClocklessAnimateOnRemount by mutableStateOf(false)
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
          count = stateList.size,
          state = state,
          itemHeight = 48.dp,
          unfocusedCount = 2,
          key = { stateList[it] },
        ) { index ->
          BasicText(text = stateList[index])
        }
        SideEffect {
          if (triggerClocklessAnimateOnRemount) {
            triggerClocklessAnimateOnRemount = false
            rawMainScope.launch {
              try {
                state.animateScrollToIndex(0)
              } catch (_: IllegalStateException) {
                clocklessExceptionCaught = true
              }
            }
          }
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

    // 3. 重挂载时无帧时钟动画抛异常后不残留状态，后续手势滑动仍正常更新 snappedIndex
    composeRule.runOnUiThread {
      visible = false
    }
    composeRule.waitForIdle()
    composeRule.runOnUiThread {
      triggerClocklessAnimateOnRemount = true
      visible = true
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
        count = 30,
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
}
