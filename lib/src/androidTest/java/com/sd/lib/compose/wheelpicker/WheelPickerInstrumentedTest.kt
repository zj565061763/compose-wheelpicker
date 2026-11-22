package com.sd.lib.compose.wheelpicker

import android.os.Build
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LookaheadScope
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
import androidx.compose.ui.unit.Velocity
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

    // 用户手指按下并超过触摸阈值拖动打断正在飞行的编程式动画
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

    // 2. 卸载隐藏期间重排列表并以 UNDISPATCHED 调用 scrollToIndex，重新挂载后覆盖旧索引且不受残留旧 key 影响
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
  fun testHeadShrinkPreservesStableKey() {
    var items by mutableStateOf((0..13).toList())
    val state = WheelPickerState(initialIndex = 8)

    composeRule.setContent {
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

    composeRule.waitForIdle()
    assertEquals(8, state.snappedIndex)
    assertEquals(8, items[state.snappedIndex])

    // 删除列表开头 6 项使原选中索引 8 >= 新条目数 8，测量后稳定 key = 8 重定位至索引 2 并保持居中选中
    composeRule.runOnUiThread {
      items = items.drop(6)
    }
    composeRule.waitForIdle()

    assertEquals(2, state.snappedIndex)
    assertEquals(2, state.currentIndex)
    assertEquals(8, items[state.snappedIndex])
    assertEquals(0f, state.scrollPercentageOf(2))
    composeRule.onNodeWithText("Key-8").assertIsSelected()
    composeRule.onNodeWithText("Key-8").assertIsDisplayed()
  }

  @Test
  fun testTailShrinkWithSubHalfItemDragCentersNewLastItem() {
    val items = mutableStateListOf("Item-0", "Item-1", "Item-2", "Item-3", "Item-4")
    val state = WheelPickerState(initialIndex = 2)
    var itemHeightPx = 0f
    var touchSlopPx = 0f

    composeRule.setContent {
      val density = LocalDensity.current
      val viewConfig = LocalViewConfiguration.current
      itemHeightPx = with(density) { 48.dp.roundToPx().toFloat() }
      touchSlopPx = viewConfig.touchSlop

      VerticalWheelPicker(
        modifier = Modifier.testTag("tailShrinkWheel"),
        count = { items.size },
        state = state,
        key = { items[it] },
        itemHeight = 48.dp,
        unfocusedCount = 2,
      ) { index ->
        BasicText(text = items[index])
      }
    }

    composeRule.waitForIdle()
    assertEquals(2, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(2))
    composeRule.onNodeWithText("Item-2").assertIsSelected()

    // 向上拖动不到半格产生正像素偏移时删除尾项使 Item-2 成为新末项，重测立即回拉归零且松手后保持居中选中
    composeRule.onNodeWithTag("tailShrinkWheel").performTouchInput {
      down(center)
      moveBy(Offset(0f, -(touchSlopPx + itemHeightPx * 0.3f)))
    }
    composeRule.waitForIdle()
    val midDragPercentage = state.scrollPercentageOf(2)!!
    assertEquals(2, state.currentIndex)
    assertTrue("Expected non-zero sub-half-item drag offset, actual=$midDragPercentage", midDragPercentage < -0.02f)

    composeRule.runOnUiThread {
      items.removeAt(4)
      items.removeAt(3)
    }
    composeRule.waitForIdle()
    assertEquals(0f, state.scrollPercentageOf(2)!!, 0.0001f)

    composeRule.onNodeWithTag("tailShrinkWheel").performTouchInput {
      up()
    }
    composeRule.waitForIdle()

    assertEquals(2, state.currentIndex)
    assertEquals(2, state.snappedIndex)
    assertEquals("Item-2", items[state.snappedIndex])
    assertEquals(0f, state.scrollPercentageOf(2)!!, 0.0001f)
    composeRule.onNodeWithText("Item-1").assertIsNotSelected()
    composeRule.onNodeWithText("Item-2").assertIsDisplayed()
    composeRule.onNodeWithText("Item-2").assertIsSelected()
    val wheelNode = composeRule.onNodeWithTag("tailShrinkWheel").fetchSemanticsNode()
    val itemNode = composeRule.onNodeWithText("Item-2").fetchSemanticsNode()
    assertWheelItemCenteredVisibleAndSelected(
      wheelNode = wheelNode,
      itemNode = itemNode,
      orientation = WheelOrientation.Vertical,
      expectedItemSizePx = itemHeightPx,
      phase = "tailShrinkSettled",
    )
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

    // 同一次主线程回调中将 12 项缩容至 1 项并以 UNDISPATCHED 在重组前立即执行 scrollToIndex(0)，重测不越界且跳转返回当下已完成吸附结算
    var verticalJumpCompleted = false
    var horizontalJumpCompleted = false
    composeRule.runOnUiThread {
      stateList.removeRange(1, 12)
      composeScope.launch(start = CoroutineStart.UNDISPATCHED) {
        verticalState.scrollToIndex(0)
        snappedRightAfterVerticalJump = verticalState.snappedIndex
        verticalJumpCompleted = true
      }

      boxedItems = listOf("BoxedItem-0")
      composeScope.launch(start = CoroutineStart.UNDISPATCHED) {
        horizontalState.scrollToIndex(0)
        snappedRightAfterHorizontalJump = horizontalState.snappedIndex
        horizontalJumpCompleted = true
      }
    }
    assertTrue("Expected vertical scrollToIndex(0) to execute synchronously before recomposition", verticalJumpCompleted)
    assertTrue("Expected horizontal scrollToIndex(0) to execute synchronously before recomposition", horizontalJumpCompleted)

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

    // 同一次主线程回调中将 1 项扩容至 6 项并以 UNDISPATCHED 在重组前立即调用 scrollToIndex(5)，key 与 content 读取同一状态不越界
    var verticalJumpCompleted = false
    var horizontalJumpCompleted = false
    composeRule.runOnUiThread {
      for (index in 1..5) {
        stateList.add("StateItem-$index")
      }
      composeScope.launch(start = CoroutineStart.UNDISPATCHED) {
        verticalState.scrollToIndex(5)
        snappedRightAfterVerticalJump = verticalState.snappedIndex
        verticalJumpCompleted = true
      }

      boxedItems = (0..5).map { "BoxedItem-$it" }
      composeScope.launch(start = CoroutineStart.UNDISPATCHED) {
        horizontalState.scrollToIndex(5)
        snappedRightAfterHorizontalJump = horizontalState.snappedIndex
        horizontalJumpCompleted = true
      }
    }
    assertTrue("Expected vertical scrollToIndex(5) to execute synchronously before recomposition", verticalJumpCompleted)
    assertTrue("Expected horizontal scrollToIndex(5) to execute synchronously before recomposition", horizontalJumpCompleted)

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

    // 再次以 UNDISPATCHED 在重组前从 6 项扩容至 12 项并立即启动 animateScrollToIndex(11)，动画正常落地至新末项
    composeRule.runOnUiThread {
      for (index in 6..11) {
        stateList.add("StateItem-$index")
      }
      composeScope.launch(start = CoroutineStart.UNDISPATCHED) {
        verticalState.animateScrollToIndex(11)
      }

      boxedItems = (0..11).map { "BoxedItem-$it" }
      composeScope.launch(start = CoroutineStart.UNDISPATCHED) {
        horizontalState.animateScrollToIndex(11)
      }
    }
    assertTrue("Expected vertical animateScrollToIndex(11) to start before recomposition", verticalState.isScrollInProgress)
    assertTrue("Expected horizontal animateScrollToIndex(11) to start before recomposition", horizontalState.isScrollInProgress)

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

    // 2. 重新挂载空列表后同帧回填数据，重组恢复前公开状态保持 (-1, -1, null) 不暴露假索引 0，重组后恢复至 3
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
    var preRecomposePercentage: Float? = 99f
    composeRule.runOnUiThread {
      repeat(8) { backingList.add("Restored-$it") }
      preRecomposeCurrent = verticalState.currentIndex
      preRecomposeSnapped = verticalState.snappedIndex
      preRecomposePercentage = verticalState.scrollPercentageOf(0)
    }
    assertEquals(-1, preRecomposeCurrent)
    assertEquals(-1, preRecomposeSnapped)
    assertNull(preRecomposePercentage)

    composeRule.waitForIdle()
    assertEquals(3, verticalState.currentIndex)
    assertEquals(3, verticalState.snappedIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(3))
    composeRule.onNodeWithText("Restored-3").assertIsDisplayed()
    composeRule.onNodeWithText("Restored-3").assertIsSelected()
  }

  @OptIn(ExperimentalSharedTransitionApi::class)
  @Test
  fun testLookaheadAnimateBoundsExpandPreservesLastItemCenterAndSnappedIndex() {
    var expanded by mutableStateOf(false)
    val verticalState = WheelPickerState(initialIndex = 9)
    val horizontalState = WheelPickerState(initialIndex = 9)
    val observedVerticalSnaps = mutableListOf<Int>()
    val observedHorizontalSnaps = mutableListOf<Int>()
    lateinit var composeScope: CoroutineScope
    var expectedCollapsedPx = 0f
    var expectedExpandedPx = 0f

    composeRule.setContent {
      composeScope = rememberCoroutineScope()
      val density = LocalDensity.current
      expectedCollapsedPx = with(density) { 48.dp.roundToPx().toFloat() }
      expectedExpandedPx = with(density) { 240.dp.roundToPx().toFloat() }
      verticalState.SnappedIndex { observedVerticalSnaps.add(it) }
      horizontalState.SnappedIndex { observedHorizontalSnaps.add(it) }

      LookaheadScope {
        Column {
          VerticalWheelPicker(
            modifier = Modifier
              .testTag("expandVerticalWheel")
              .animateBounds(
                lookaheadScope = this@LookaheadScope,
                modifier = Modifier.height(if (expanded) 240.dp else 48.dp),
              ),
            count = { 10 },
            state = verticalState,
            itemHeight = 48.dp,
            unfocusedCount = 2,
          ) { index ->
            BasicText(text = "V-Expand-$index")
          }

          HorizontalWheelPicker(
            modifier = Modifier
              .testTag("expandHorizontalWheel")
              .animateBounds(
                lookaheadScope = this@LookaheadScope,
                modifier = Modifier.width(if (expanded) 240.dp else 48.dp),
              ),
            count = { 10 },
            state = horizontalState,
            itemWidth = 48.dp,
            unfocusedCount = 2,
          ) { index ->
            BasicText(text = "H-Expand-$index")
          }
        }
      }
    }

    composeRule.waitForIdle()
    assertEquals(9, verticalState.snappedIndex)
    assertEquals(9, verticalState.currentIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(9))
    assertEquals(listOf(9), observedVerticalSnaps)
    assertEquals(9, horizontalState.snappedIndex)
    assertEquals(9, horizontalState.currentIndex)
    assertEquals(0f, horizontalState.scrollPercentageOf(9))
    assertEquals(listOf(9), observedHorizontalSnaps)

    val initialVerticalNode = composeRule.onNodeWithTag("expandVerticalWheel").fetchSemanticsNode()
    val initialHorizontalNode = composeRule.onNodeWithTag("expandHorizontalWheel").fetchSemanticsNode()
    assertTrue(abs(initialVerticalNode.unclippedBoundsInRoot().height - expectedCollapsedPx) <= 1.5f)
    assertTrue(abs(initialHorizontalNode.unclippedBoundsInRoot().width - expectedCollapsedPx) <= 1.5f)

    // 1. 不触摸滚轮且不修改数据，从 48.dp 展开动画至 240.dp：逐帧校验未裁剪与可见裁剪边界居中及 Selected 语义
    try {
      composeRule.mainClock.autoAdvance = false
      composeRule.runOnUiThread {
        expanded = true
      }
      var sawIntermediateVerticalHeight = false
      var sawIntermediateHorizontalWidth = false
      var reachedExpandedTarget = false
      for (frame in 0 until 120) {
        composeRule.mainClock.advanceTimeBy(16L)
        assertEquals(9, verticalState.currentIndex)
        assertEquals(9, verticalState.snappedIndex)
        assertEquals(0f, verticalState.scrollPercentageOf(9))
        assertEquals(9, horizontalState.currentIndex)
        assertEquals(9, horizontalState.snappedIndex)
        assertEquals(0f, horizontalState.scrollPercentageOf(9))

        val verticalWheelNode = composeRule.onNodeWithTag("expandVerticalWheel").fetchSemanticsNode()
        val verticalItemNode = composeRule.onNodeWithText("V-Expand-9").fetchSemanticsNode()
        assertWheelItemCenteredVisibleAndSelected(
          wheelNode = verticalWheelNode,
          itemNode = verticalItemNode,
          orientation = WheelOrientation.Vertical,
          expectedItemSizePx = expectedCollapsedPx,
          phase = "expand(frame=$frame)",
        )
        val verticalHeight = verticalWheelNode.unclippedBoundsInRoot().height
        if (verticalHeight > expectedCollapsedPx + 2f && verticalHeight < expectedExpandedPx - 2f) {
          sawIntermediateVerticalHeight = true
        }

        val horizontalWheelNode = composeRule.onNodeWithTag("expandHorizontalWheel").fetchSemanticsNode()
        val horizontalItemNode = composeRule.onNodeWithText("H-Expand-9").fetchSemanticsNode()
        assertWheelItemCenteredVisibleAndSelected(
          wheelNode = horizontalWheelNode,
          itemNode = horizontalItemNode,
          orientation = WheelOrientation.Horizontal,
          expectedItemSizePx = expectedCollapsedPx,
          phase = "expand(frame=$frame)",
        )
        val horizontalWidth = horizontalWheelNode.unclippedBoundsInRoot().width
        if (horizontalWidth > expectedCollapsedPx + 2f && horizontalWidth < expectedExpandedPx - 2f) {
          sawIntermediateHorizontalWidth = true
        }

        if (
          abs(verticalHeight - expectedExpandedPx) <= 0.5f &&
          abs(horizontalWidth - expectedExpandedPx) <= 0.5f
        ) {
          reachedExpandedTarget = true
          break
        }
      }
      assertTrue("Expected vertical animateBounds intermediate frame height", sawIntermediateVerticalHeight)
      assertTrue("Expected horizontal animateBounds intermediate frame width", sawIntermediateHorizontalWidth)
      assertTrue("Expected both wheels to reach expanded target size within frame limit", reachedExpandedTarget)
    } finally {
      composeRule.mainClock.autoAdvance = true
    }

    composeRule.waitForIdle()
    assertEquals(9, verticalState.snappedIndex)
    assertEquals(9, verticalState.currentIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(9))
    assertEquals(listOf(9), observedVerticalSnaps)
    composeRule.onNodeWithText("V-Expand-9").assertIsDisplayed()
    composeRule.onNodeWithText("V-Expand-9").assertIsSelected()
    val expandedVerticalWheelNode = composeRule.onNodeWithTag("expandVerticalWheel").fetchSemanticsNode()
    val expandedVerticalItemNode = composeRule.onNodeWithText("V-Expand-9").fetchSemanticsNode()
    assertTrue(abs(expandedVerticalWheelNode.unclippedBoundsInRoot().height - expectedExpandedPx) <= 1.5f)
    assertWheelItemCenteredVisibleAndSelected(
      wheelNode = expandedVerticalWheelNode,
      itemNode = expandedVerticalItemNode,
      orientation = WheelOrientation.Vertical,
      expectedItemSizePx = expectedCollapsedPx,
      phase = "expandedSettled",
    )

    assertEquals(9, horizontalState.snappedIndex)
    assertEquals(9, horizontalState.currentIndex)
    assertEquals(0f, horizontalState.scrollPercentageOf(9))
    assertEquals(listOf(9), observedHorizontalSnaps)
    composeRule.onNodeWithText("H-Expand-9").assertIsDisplayed()
    composeRule.onNodeWithText("H-Expand-9").assertIsSelected()
    val expandedHorizontalWheelNode = composeRule.onNodeWithTag("expandHorizontalWheel").fetchSemanticsNode()
    val expandedHorizontalItemNode = composeRule.onNodeWithText("H-Expand-9").fetchSemanticsNode()
    assertTrue(abs(expandedHorizontalWheelNode.unclippedBoundsInRoot().width - expectedExpandedPx) <= 1.5f)
    assertWheelItemCenteredVisibleAndSelected(
      wheelNode = expandedHorizontalWheelNode,
      itemNode = expandedHorizontalItemNode,
      orientation = WheelOrientation.Horizontal,
      expectedItemSizePx = expectedCollapsedPx,
      phase = "expandedSettled",
    )

    // 2. 先跳至首项 0，再在从 240.dp 收缩至 48.dp 动画途中调用 scrollToIndex(9)：推进下一帧时钟前立即校验当帧位置与状态
    composeRule.runOnUiThread {
      composeScope.launch {
        verticalState.scrollToIndex(0)
        horizontalState.scrollToIndex(0)
      }
    }
    composeRule.waitForIdle()
    assertEquals(0, verticalState.snappedIndex)
    assertEquals(0, horizontalState.snappedIndex)

    var verticalJumpCompleted = false
    var horizontalJumpCompleted = false
    var verticalCurrentRightAfterShrinkJump = -1
    var verticalSnappedRightAfterShrinkJump = -1
    var horizontalCurrentRightAfterShrinkJump = -1
    var horizontalSnappedRightAfterShrinkJump = -1
    try {
      composeRule.mainClock.autoAdvance = false
      composeRule.runOnUiThread {
        expanded = false
      }
      var jumpedDuringShrink = false
      var reachedCollapsedTarget = false
      for (frame in 0 until 120) {
        composeRule.mainClock.advanceTimeBy(16L)
        val verticalWheelNode = composeRule.onNodeWithTag("expandVerticalWheel").fetchSemanticsNode()
        val horizontalWheelNode = composeRule.onNodeWithTag("expandHorizontalWheel").fetchSemanticsNode()
        val verticalWheelBounds = verticalWheelNode.unclippedBoundsInRoot()
        val horizontalWheelBounds = horizontalWheelNode.unclippedBoundsInRoot()
        val isVerticalIntermediate =
          verticalWheelBounds.height > expectedCollapsedPx + 2f && verticalWheelBounds.height < expectedExpandedPx - 2f
        val isHorizontalIntermediate =
          horizontalWheelBounds.width > expectedCollapsedPx + 2f && horizontalWheelBounds.width < expectedExpandedPx - 2f
        if (!jumpedDuringShrink && isVerticalIntermediate && isHorizontalIntermediate) {
          jumpedDuringShrink = true
          composeRule.runOnUiThread {
            composeScope.launch(start = CoroutineStart.UNDISPATCHED) {
              verticalState.scrollToIndex(9)
              verticalCurrentRightAfterShrinkJump = verticalState.currentIndex
              verticalSnappedRightAfterShrinkJump = verticalState.snappedIndex
              verticalJumpCompleted = true
            }

            composeScope.launch(start = CoroutineStart.UNDISPATCHED) {
              horizontalState.scrollToIndex(9)
              horizontalCurrentRightAfterShrinkJump = horizontalState.currentIndex
              horizontalSnappedRightAfterShrinkJump = horizontalState.snappedIndex
              horizontalJumpCompleted = true
            }

            Snapshot.sendApplyNotifications()
          }

          assertTrue("Expected vertical scrollToIndex(9) to complete synchronously before next frame", verticalJumpCompleted)
          assertTrue("Expected horizontal scrollToIndex(9) to complete synchronously before next frame", horizontalJumpCompleted)
          assertEquals(9, verticalState.currentIndex)
          assertEquals(9, verticalState.snappedIndex)
          assertEquals(0f, verticalState.scrollPercentageOf(9))
          assertEquals(9, horizontalState.currentIndex)
          assertEquals(9, horizontalState.snappedIndex)
          assertEquals(0f, horizontalState.scrollPercentageOf(9))

          val postJumpVerticalWheelNode = composeRule.onNodeWithTag("expandVerticalWheel").fetchSemanticsNode()
          val postJumpVerticalItemNode = composeRule.onNodeWithText("V-Expand-9").fetchSemanticsNode()
          val postJumpVerticalWheelBounds = postJumpVerticalWheelNode.unclippedBoundsInRoot()
          assertTrue(
            "Expected vertical wheel height unchanged on jump frame before clock advance",
            abs(postJumpVerticalWheelBounds.height - verticalWheelBounds.height) <= 0.5f,
          )
          assertWheelItemCenteredVisibleAndSelected(
            wheelNode = postJumpVerticalWheelNode,
            itemNode = postJumpVerticalItemNode,
            orientation = WheelOrientation.Vertical,
            expectedItemSizePx = expectedCollapsedPx,
            phase = "shrinkJumpSameFrame(frame=$frame)",
          )

          val postJumpHorizontalWheelNode = composeRule.onNodeWithTag("expandHorizontalWheel").fetchSemanticsNode()
          val postJumpHorizontalItemNode = composeRule.onNodeWithText("H-Expand-9").fetchSemanticsNode()
          val postJumpHorizontalWheelBounds = postJumpHorizontalWheelNode.unclippedBoundsInRoot()
          assertTrue(
            "Expected horizontal wheel width unchanged on jump frame before clock advance",
            abs(postJumpHorizontalWheelBounds.width - horizontalWheelBounds.width) <= 0.5f,
          )
          assertWheelItemCenteredVisibleAndSelected(
            wheelNode = postJumpHorizontalWheelNode,
            itemNode = postJumpHorizontalItemNode,
            orientation = WheelOrientation.Horizontal,
            expectedItemSizePx = expectedCollapsedPx,
            phase = "shrinkJumpSameFrame(frame=$frame)",
          )
        } else if (jumpedDuringShrink) {
          assertEquals(9, verticalState.currentIndex)
          assertEquals(9, verticalState.snappedIndex)
          assertEquals(0f, verticalState.scrollPercentageOf(9))
          assertEquals(9, horizontalState.currentIndex)
          assertEquals(9, horizontalState.snappedIndex)
          assertEquals(0f, horizontalState.scrollPercentageOf(9))

          val verticalItemNode = composeRule.onNodeWithText("V-Expand-9").fetchSemanticsNode()
          assertWheelItemCenteredVisibleAndSelected(
            wheelNode = verticalWheelNode,
            itemNode = verticalItemNode,
            orientation = WheelOrientation.Vertical,
            expectedItemSizePx = expectedCollapsedPx,
            phase = "shrinkRemaining(frame=$frame)",
          )

          val horizontalItemNode = composeRule.onNodeWithText("H-Expand-9").fetchSemanticsNode()
          assertWheelItemCenteredVisibleAndSelected(
            wheelNode = horizontalWheelNode,
            itemNode = horizontalItemNode,
            orientation = WheelOrientation.Horizontal,
            expectedItemSizePx = expectedCollapsedPx,
            phase = "shrinkRemaining(frame=$frame)",
          )

          if (
            abs(verticalWheelBounds.height - expectedCollapsedPx) <= 0.5f &&
            abs(horizontalWheelBounds.width - expectedCollapsedPx) <= 0.5f
          ) {
            reachedCollapsedTarget = true
            break
          }
        }
      }
      assertTrue("Expected scrollToIndex(9) during shrink animation", jumpedDuringShrink)
      assertTrue("Expected both wheels to reach collapsed target size within frame limit", reachedCollapsedTarget)
      assertEquals(9, verticalCurrentRightAfterShrinkJump)
      assertEquals(9, verticalSnappedRightAfterShrinkJump)
      assertEquals(9, horizontalCurrentRightAfterShrinkJump)
      assertEquals(9, horizontalSnappedRightAfterShrinkJump)
    } finally {
      composeRule.mainClock.autoAdvance = true
    }

    composeRule.waitForIdle()
    assertEquals(9, verticalState.currentIndex)
    assertEquals(9, verticalState.snappedIndex)
    assertEquals(0f, verticalState.scrollPercentageOf(9))
    composeRule.onNodeWithText("V-Expand-9").assertIsDisplayed()
    composeRule.onNodeWithText("V-Expand-9").assertIsSelected()
    val collapsedVerticalWheelNode = composeRule.onNodeWithTag("expandVerticalWheel").fetchSemanticsNode()
    val collapsedVerticalItemNode = composeRule.onNodeWithText("V-Expand-9").fetchSemanticsNode()
    assertTrue(abs(collapsedVerticalWheelNode.unclippedBoundsInRoot().height - expectedCollapsedPx) <= 1.5f)
    assertWheelItemCenteredVisibleAndSelected(
      wheelNode = collapsedVerticalWheelNode,
      itemNode = collapsedVerticalItemNode,
      orientation = WheelOrientation.Vertical,
      expectedItemSizePx = expectedCollapsedPx,
      phase = "collapsedSettled",
    )

    assertEquals(9, horizontalState.currentIndex)
    assertEquals(9, horizontalState.snappedIndex)
    assertEquals(0f, horizontalState.scrollPercentageOf(9))
    composeRule.onNodeWithText("H-Expand-9").assertIsDisplayed()
    composeRule.onNodeWithText("H-Expand-9").assertIsSelected()
    val collapsedHorizontalWheelNode = composeRule.onNodeWithTag("expandHorizontalWheel").fetchSemanticsNode()
    val collapsedHorizontalItemNode = composeRule.onNodeWithText("H-Expand-9").fetchSemanticsNode()
    assertTrue(abs(collapsedHorizontalWheelNode.unclippedBoundsInRoot().width - expectedCollapsedPx) <= 1.5f)
    assertWheelItemCenteredVisibleAndSelected(
      wheelNode = collapsedHorizontalWheelNode,
      itemNode = collapsedHorizontalItemNode,
      orientation = WheelOrientation.Horizontal,
      expectedItemSizePx = expectedCollapsedPx,
      phase = "collapsedSettled",
    )

    // 3. 再次展开至 240.dp，末项 9 仍保持选中且居中
    composeRule.runOnUiThread {
      expanded = true
    }
    composeRule.waitForIdle()
    assertEquals(9, verticalState.snappedIndex)
    assertEquals(9, horizontalState.snappedIndex)
    val reExpandedVerticalWheelNode = composeRule.onNodeWithTag("expandVerticalWheel").fetchSemanticsNode()
    val reExpandedVerticalItemNode = composeRule.onNodeWithText("V-Expand-9").fetchSemanticsNode()
    assertWheelItemCenteredVisibleAndSelected(
      wheelNode = reExpandedVerticalWheelNode,
      itemNode = reExpandedVerticalItemNode,
      orientation = WheelOrientation.Vertical,
      expectedItemSizePx = expectedCollapsedPx,
      phase = "reExpandedSettled",
    )
    val reExpandedHorizontalWheelNode = composeRule.onNodeWithTag("expandHorizontalWheel").fetchSemanticsNode()
    val reExpandedHorizontalItemNode = composeRule.onNodeWithText("H-Expand-9").fetchSemanticsNode()
    assertWheelItemCenteredVisibleAndSelected(
      wheelNode = reExpandedHorizontalWheelNode,
      itemNode = reExpandedHorizontalItemNode,
      orientation = WheelOrientation.Horizontal,
      expectedItemSizePx = expectedCollapsedPx,
      phase = "reExpandedSettled",
    )
  }
}

/** 校验滚轮目标条目在未裁剪与裁剪可见边界下均居中无异常裁剪，且无障碍选中语义为 true */
private fun assertWheelItemCenteredVisibleAndSelected(
  wheelNode: SemanticsNode,
  itemNode: SemanticsNode,
  orientation: WheelOrientation,
  expectedItemSizePx: Float,
  phase: String,
) {
  val wheelUnclipped = wheelNode.unclippedBoundsInRoot()
  val itemUnclipped = itemNode.unclippedBoundsInRoot()
  val wheelClipped = wheelNode.boundsInRoot
  val itemClipped = itemNode.boundsInRoot
  assertFalse("Expected visible non-empty bounds for item during $phase, got $itemClipped", itemClipped.isEmpty)
  assertEquals(
    "Expected SemanticsProperties.Selected == true during $phase",
    true,
    itemNode.config.getOrNull(SemanticsProperties.Selected),
  )
  when (orientation) {
    WheelOrientation.Vertical -> {
      assertTrue(
        "Expected item unclipped centerY aligned during $phase, itemCenterY=${itemUnclipped.center.y}, wheelCenterY=${wheelUnclipped.center.y}, wheelHeight=${wheelUnclipped.height}",
        abs(itemUnclipped.center.y - wheelUnclipped.center.y) <= 1.5f,
      )
      assertTrue(
        "Expected item clipped centerY aligned during $phase, clippedCenterY=${itemClipped.center.y}, wheelCenterY=${wheelClipped.center.y}",
        abs(itemClipped.center.y - wheelClipped.center.y) <= 1.5f,
      )
      assertTrue(
        "Expected centered vertical item not clipped on main axis during $phase, clippedHeight=${itemClipped.height}, expected=$expectedItemSizePx",
        abs(itemClipped.height - expectedItemSizePx) <= 1.5f,
      )
    }

    WheelOrientation.Horizontal -> {
      assertTrue(
        "Expected item unclipped centerX aligned during $phase, itemCenterX=${itemUnclipped.center.x}, wheelCenterX=${wheelUnclipped.center.x}, wheelWidth=${wheelUnclipped.width}",
        abs(itemUnclipped.center.x - wheelUnclipped.center.x) <= 1.5f,
      )
      assertTrue(
        "Expected item clipped centerX aligned during $phase, clippedCenterX=${itemClipped.center.x}, wheelCenterX=${wheelClipped.center.x}",
        abs(itemClipped.center.x - wheelClipped.center.x) <= 1.5f,
      )
      assertTrue(
        "Expected centered horizontal item not clipped on main axis during $phase, clippedWidth=${itemClipped.width}, expected=$expectedItemSizePx",
        abs(itemClipped.width - expectedItemSizePx) <= 1.5f,
      )
    }
  }
}

private fun SemanticsNode.unclippedBoundsInRoot(): Rect {
  return Rect(
    offset = positionInRoot,
    size = size.toSize(),
  )
}
