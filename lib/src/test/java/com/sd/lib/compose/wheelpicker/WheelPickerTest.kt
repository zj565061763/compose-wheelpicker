package com.sd.lib.compose.wheelpicker

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.RulerScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelPickerTest {
  @Test
  fun testInvalidWheelPickerParametersThrowIllegalArgumentException() {
    runBlocking {
      val clock = BroadcastFrameClock()
      val recomposer = Recomposer(coroutineContext + clock)
      try {
        for (invalidSize in listOf(0.dp, (-1).dp, Dp.Unspecified, Dp.Infinity)) {
          assertCompositionThrowsIllegalArgument(recomposer) {
            VerticalWheelPicker(count = { 1 }, itemHeight = invalidSize) { }
          }
          assertCompositionThrowsIllegalArgument(recomposer) {
            HorizontalWheelPicker(count = { 1 }, itemWidth = invalidSize) { }
          }
        }

        assertCompositionThrowsIllegalArgument(recomposer) {
          VerticalWheelPicker(count = { 1 }, unfocusedCount = -1) { }
        }
        assertCompositionThrowsIllegalArgument(recomposer) {
          HorizontalWheelPicker(count = { 1 }, unfocusedCount = -1) { }
        }

        assertCompositionThrowsIllegalArgument(recomposer) {
          VerticalWheelPicker(count = { -1 }) { }
        }
        assertCompositionThrowsIllegalArgument(recomposer) {
          HorizontalWheelPicker(count = { -1 }) { }
        }
      } finally {
        recomposer.cancel()
      }
    }
  }

  @Test
  fun testCalculateSafeUnfocusedCount() {
    assertEquals(
      2,
      calculateSafeUnfocusedCount(
        maxSizePx = Constraints.Infinity,
        itemSizePx = 48,
        unfocusedCount = 2,
      ),
    )
    assertEquals(
      2,
      calculateSafeUnfocusedCount(
        maxSizePx = 240,
        itemSizePx = 48,
        unfocusedCount = 2,
      ),
    )
    assertEquals(
      1,
      calculateSafeUnfocusedCount(
        maxSizePx = 239,
        itemSizePx = 48,
        unfocusedCount = 2,
      ),
    )
    assertEquals(
      1,
      calculateSafeUnfocusedCount(
        maxSizePx = 144,
        itemSizePx = 48,
        unfocusedCount = 20,
      ),
    )
    assertEquals(
      0,
      calculateSafeUnfocusedCount(
        maxSizePx = 143,
        itemSizePx = 48,
        unfocusedCount = 20,
      ),
    )
    assertEquals(
      0,
      calculateSafeUnfocusedCount(
        maxSizePx = 20,
        itemSizePx = 48,
        unfocusedCount = 2,
      ),
    )
  }

  @Test
  fun testCalculateSnappedIndex() {
    assertEquals(-1, calculateSnappedIndex(0, 0, count = 0, itemSizePx = 48))
    assertEquals(5, calculateSnappedIndex(5, 0, count = 10, itemSizePx = 48))
    assertEquals(5, calculateSnappedIndex(5, 24, count = 10, itemSizePx = 48))
    assertEquals(6, calculateSnappedIndex(5, 25, count = 10, itemSizePx = 48))
    assertEquals(9, calculateSnappedIndex(15, 0, count = 10, itemSizePx = 48))
  }

  @Test
  fun testCalculateScrollPercentageAndVisualDirection() {
    assertNull(calculateScrollPercentage(0, 0, 0, unfocusedCount = 2, itemSizePx = 0))

    assertEquals(
      0f,
      calculateScrollPercentage(5, 5, 0, unfocusedCount = 2, itemSizePx = 100)!!,
      0.0001f,
    )
    assertEquals(
      -1f / 3f,
      calculateScrollPercentage(4, 5, 0, unfocusedCount = 2, itemSizePx = 100)!!,
      0.0001f,
    )
    assertEquals(
      1f / 3f,
      calculateScrollPercentage(6, 5, 0, unfocusedCount = 2, itemSizePx = 100)!!,
      0.0001f,
    )
    assertEquals(
      -1f,
      calculateScrollPercentage(0, 50, 0, unfocusedCount = 2, itemSizePx = 100)!!,
      0.0001f,
    )
    assertEquals(
      1f,
      calculateScrollPercentage(100, 50, 99, unfocusedCount = 2, itemSizePx = 100)!!,
      0.0001f,
    )

    assertFalse(WheelOrientation.Vertical.isVisualDirectionReversed(reverseLayout = false, layoutDirection = LayoutDirection.Ltr))
    assertFalse(WheelOrientation.Vertical.isVisualDirectionReversed(reverseLayout = false, layoutDirection = LayoutDirection.Rtl))
    assertTrue(WheelOrientation.Vertical.isVisualDirectionReversed(reverseLayout = true, layoutDirection = LayoutDirection.Ltr))
    assertTrue(WheelOrientation.Vertical.isVisualDirectionReversed(reverseLayout = true, layoutDirection = LayoutDirection.Rtl))

    assertFalse(WheelOrientation.Horizontal.isVisualDirectionReversed(reverseLayout = false, layoutDirection = LayoutDirection.Ltr))
    assertTrue(WheelOrientation.Horizontal.isVisualDirectionReversed(reverseLayout = false, layoutDirection = LayoutDirection.Rtl))
    assertTrue(WheelOrientation.Horizontal.isVisualDirectionReversed(reverseLayout = true, layoutDirection = LayoutDirection.Ltr))
    assertFalse(WheelOrientation.Horizontal.isVisualDirectionReversed(reverseLayout = true, layoutDirection = LayoutDirection.Rtl))

    val state = WheelPickerState(initialIndex = 5)
    state.updateAttachment(
      count = 10,
      unfocusedCount = 2,
      itemSizePx = 100,
      reverseVisualDirection = true,
    )
    val centerPercentage = state.scrollPercentageOf(5)!!
    assertEquals(0f, centerPercentage, 0.0001f)
    assertEquals(0f.toRawBits(), centerPercentage.toRawBits())
    assertEquals(1f / 3f, state.scrollPercentageOf(4)!!, 0.0001f)
    assertEquals(-1f / 3f, state.scrollPercentageOf(6)!!, 0.0001f)
  }

  @Test
  fun testStateLifecycleShrinkClampAndSaver() {
    val defaultState = WheelPickerState()
    assertEquals(
      0,
      with(WheelPickerState.Saver) { SaverScope { true }.save(defaultState) },
    )

    val state = WheelPickerState(initialIndex = 7)
    assertEquals(-1, state.currentIndex)
    assertEquals(-1, state.snappedIndex)
    assertNull(state.scrollPercentageOf(7))

    // 未挂载或无数据时，Saver 仍保留 initialIndex
    val savedBeforeAttach = with(WheelPickerState.Saver) {
      SaverScope { true }.save(state)
    }
    assertEquals(7, savedBeforeAttach)

    state.updateAttachment(count = 10, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(7, state.currentIndex)
    assertEquals(7, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(7)!!, 0.0001f)

    // 缩容使原选中索引 >= 新条目数时，立即夹紧至 count - 1
    state.updateAttachment(count = 6, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(5, state.snappedIndex)

    // 旧组件卸载后挂载新组件，恢复最近选中索引并限制在新条目数边界内
    state.detach()
    assertEquals(-1, state.currentIndex)
    assertEquals(-1, state.snappedIndex)
    assertNull(state.scrollPercentageOf(5))

    state.updateAttachment(count = 5, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(4, state.currentIndex)
    assertEquals(4, state.snappedIndex)

    // count 变为 0 时，状态重置为 -1，Saver 保存进入空列表前的有效选中项 4
    state.updateAttachment(count = 0, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(-1, state.currentIndex)
    assertEquals(-1, state.snappedIndex)
    assertNull(state.scrollPercentageOf(4))
    val savedWhenEmpty = with(WheelPickerState.Saver) {
      SaverScope { true }.save(state)
    }
    assertEquals(4, savedWhenEmpty)

    // 模拟 LazyList 曾非空后变为空时将 firstVisibleItemIndex 重置为 0 的行为，数据恢复后仍能找回索引 4
    state.lazyListState.requestScrollToItem(0)
    state.updateAttachment(count = 10, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(4, state.currentIndex)
    assertEquals(4, state.snappedIndex)

    state.detach()
    assertEquals(-1, state.currentIndex)
    assertEquals(-1, state.snappedIndex)
    assertNull(state.scrollPercentageOf(4))
  }

  @Test
  fun testPreJumpWhileDetachedOrEmptyDoesNotSuspend() {
    runBlocking {
      withTimeout(5.seconds) {
        val state = WheelPickerState(initialIndex = 0)
        state.scrollToIndex(6)
        assertEquals(-1, state.snappedIndex)

        state.updateAttachment(
          count = 10,
          unfocusedCount = 2,
          itemSizePx = 48,
        )
        assertEquals(6, state.currentIndex)
        assertEquals(6, state.snappedIndex)

        // count == 0 时调用 animateScrollToIndex 不挂起且覆盖待恢复位置
        state.updateAttachment(
          count = 0,
          unfocusedCount = 2,
          itemSizePx = 48,
        )
        state.animateScrollToIndex(8)
        assertEquals(-1, state.snappedIndex)

        state.updateAttachment(
          count = 10,
          unfocusedCount = 2,
          itemSizePx = 48,
        )
        assertEquals(8, state.currentIndex)
        assertEquals(8, state.snappedIndex)

        // 卸载后预跳转超出后续挂载条目数时自动夹紧至边界
        state.detach()
        state.animateScrollToIndex(20)
        assertEquals(-1, state.snappedIndex)

        state.updateAttachment(
          count = 10,
          unfocusedCount = 2,
          itemSizePx = 48,
        )
        assertEquals(9, state.currentIndex)
        assertEquals(9, state.snappedIndex)
      }
    }
  }

  @Test
  fun testViewportMeasurePolicyIntrinsicsAndClipping() {
    val density = Density(density = 1f, fontScale = 1f)

    for (orientation in listOf(WheelOrientation.Vertical, WheelOrientation.Horizontal)) {
      val viewportState = WheelViewportState(
        orientation = orientation,
        density = density,
        itemSizePx = 48,
        initialUnfocusedCount = 2,
      )
      val paddingRef = viewportState.contentPadding
      var resolvedCount = -1
      val policy = createWheelViewportMeasurePolicy(
        orientation = orientation,
        itemSizePx = 48,
        unfocusedCount = 2,
        viewportState = viewportState,
        onResolvedUnfocusedCount = { resolvedCount = it },
      )

      val throwingListMeasurable = ThrowingIntrinsicMeasurable(
        measuredWidth = if (orientation == WheelOrientation.Vertical) 80 else 240,
        measuredHeight = if (orientation == WheelOrientation.Vertical) 240 else 64,
      )
      val focusMeasurable = FakeBoxMeasurable(intrinsicCrossSize = 72)
      val measurables = listOf(throwingListMeasurable, focusMeasurable)
      val measureScope = FakeMeasureScope(backingDensity = density)

      if (orientation == WheelOrientation.Vertical) {
        assertEquals(72, with(policy) { measureScope.minIntrinsicWidth(measurables, 240) })
        assertEquals(72, with(policy) { measureScope.maxIntrinsicWidth(measurables, 240) })
        assertEquals(240, with(policy) { measureScope.minIntrinsicHeight(measurables, 80) })
        assertEquals(240, with(policy) { measureScope.maxIntrinsicHeight(measurables, 80) })

        val normalResult = with(policy) {
          measureScope.measure(measurables, Constraints(minWidth = 0, maxWidth = 200, minHeight = 0, maxHeight = 500))
        }
        assertEquals(80, normalResult.width)
        assertEquals(240, normalResult.height)
        assertEquals(2, resolvedCount)
        assertFalse(viewportState.shouldClip)
        assertEquals(Constraints.fixed(80, 48), checkNotNull(focusMeasurable.lastMeasuredConstraints))

        val tightList = ThrowingIntrinsicMeasurable(measuredWidth = 80, measuredHeight = 48)
        val tightResult = with(policy) {
          measureScope.measure(listOf(tightList, focusMeasurable), Constraints.fixed(80, 20))
        }
        assertEquals(80, tightResult.width)
        assertEquals(20, tightResult.height)
        assertEquals(0, resolvedCount)
        assertTrue(viewportState.shouldClip)
        assertSame(paddingRef, viewportState.contentPadding)
      } else {
        assertEquals(240, with(policy) { measureScope.minIntrinsicWidth(measurables, 64) })
        assertEquals(240, with(policy) { measureScope.maxIntrinsicWidth(measurables, 64) })
        assertEquals(72, with(policy) { measureScope.minIntrinsicHeight(measurables, 240) })
        assertEquals(72, with(policy) { measureScope.maxIntrinsicHeight(measurables, 240) })

        val normalResult = with(policy) {
          measureScope.measure(measurables, Constraints(minWidth = 0, maxWidth = 500, minHeight = 0, maxHeight = 200))
        }
        assertEquals(240, normalResult.width)
        assertEquals(64, normalResult.height)
        assertEquals(2, resolvedCount)
        assertFalse(viewportState.shouldClip)
        assertEquals(Constraints.fixed(48, 64), checkNotNull(focusMeasurable.lastMeasuredConstraints))
      }
    }

    // 验证视口测量阶段更新内边距、裁剪与单侧未选中条目数时不触发快照读观测
    val observeState = WheelPickerState(initialIndex = 2)
    observeState.updateAttachment(
      count = 10,
      unfocusedCount = 2,
      itemSizePx = 48,
    )
    val observeViewportState = WheelViewportState(
      orientation = WheelOrientation.Vertical,
      density = density,
      itemSizePx = 48,
      initialUnfocusedCount = 2,
    )
    val observePolicy = createWheelViewportMeasurePolicy(
      orientation = WheelOrientation.Vertical,
      itemSizePx = 48,
      unfocusedCount = 2,
      viewportState = observeViewportState,
      onResolvedUnfocusedCount = { safeCount ->
        observeState.updateViewportUnfocusedCount(safeCount)
      },
    )
    val observeScope = FakeMeasureScope(backingDensity = density)
    val observeList = ThrowingIntrinsicMeasurable(measuredWidth = 80, measuredHeight = 48)
    val observeFocus = FakeBoxMeasurable(intrinsicCrossSize = 72)
    val measureObservedReads = mutableListOf<Any>()
    Snapshot.observe(readObserver = { measureObservedReads.add(it) }) {
      with(observePolicy) {
        observeScope.measure(listOf(observeList, observeFocus), Constraints.fixed(80, 20))
      }
    }
    assertTrue(measureObservedReads.isEmpty())
    assertTrue(observeViewportState.shouldClip)
    assertEquals(0.dp, observeViewportState.contentPadding.calculateTopPadding())
    assertEquals(-1f, observeState.scrollPercentageOf(1)!!, 0.0001f)
  }
}

private class EmptyApplier : AbstractApplier<Unit>(Unit) {
  override fun insertTopDown(index: Int, instance: Unit) {
  }

  override fun insertBottomUp(index: Int, instance: Unit) {
  }

  override fun remove(index: Int, count: Int) {
  }

  override fun move(from: Int, to: Int, count: Int) {
  }

  override fun onClear() {
  }
}

private class ThrowingIntrinsicMeasurable(
  private val measuredWidth: Int,
  private val measuredHeight: Int,
) : Measurable {
  override val parentData: Any?
    get() {
      return null
    }

  override fun measure(constraints: Constraints): Placeable {
    return FakePlaceable(
      w = measuredWidth.coerceIn(constraints.minWidth, constraints.maxWidth),
      h = measuredHeight.coerceIn(constraints.minHeight, constraints.maxHeight),
    )
  }

  override fun minIntrinsicWidth(height: Int): Int {
    throw AssertionError("LazyList minIntrinsicWidth must not be queried")
  }

  override fun maxIntrinsicWidth(height: Int): Int {
    throw AssertionError("LazyList maxIntrinsicWidth must not be queried")
  }

  override fun minIntrinsicHeight(width: Int): Int {
    throw AssertionError("LazyList minIntrinsicHeight must not be queried")
  }

  override fun maxIntrinsicHeight(width: Int): Int {
    throw AssertionError("LazyList maxIntrinsicHeight must not be queried")
  }
}

private class FakeBoxMeasurable(
  private val intrinsicCrossSize: Int,
) : Measurable {
  var lastMeasuredConstraints: Constraints? = null

  override val parentData: Any?
    get() {
      return null
    }

  override fun measure(constraints: Constraints): Placeable {
    lastMeasuredConstraints = constraints
    return FakePlaceable(
      w = constraints.maxWidth,
      h = constraints.maxHeight,
    )
  }

  override fun minIntrinsicWidth(height: Int): Int {
    return intrinsicCrossSize
  }

  override fun maxIntrinsicWidth(height: Int): Int {
    return intrinsicCrossSize
  }

  override fun minIntrinsicHeight(width: Int): Int {
    return intrinsicCrossSize
  }

  override fun maxIntrinsicHeight(width: Int): Int {
    return intrinsicCrossSize
  }
}

private class FakePlaceable(
  w: Int,
  h: Int,
) : Placeable() {
  override fun get(alignmentLine: AlignmentLine): Int {
    return AlignmentLine.Unspecified
  }

  override fun placeAt(
    position: IntOffset,
    zIndex: Float,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
  ) {
  }

  init {
    measuredSize = IntSize(w, h)
  }
}

private class FakeMeasureScope(
  private val backingDensity: Density,
) : MeasureScope, IntrinsicMeasureScope {
  override val density: Float
    get() {
      return backingDensity.density
    }

  override val fontScale: Float
    get() {
      return backingDensity.fontScale
    }

  override val layoutDirection: LayoutDirection
    get() {
      return LayoutDirection.Ltr
    }

  override fun layout(
    width: Int,
    height: Int,
    alignmentLines: Map<AlignmentLine, Int>,
    rulers: (RulerScope.() -> Unit)?,
    placementBlock: Placeable.PlacementScope.() -> Unit,
  ): MeasureResult {
    return object : MeasureResult {
      override val width: Int
        get() {
          return width
        }

      override val height: Int
        get() {
          return height
        }

      override val alignmentLines: Map<AlignmentLine, Int>
        get() {
          return alignmentLines
        }

      override val rulers: (RulerScope.() -> Unit)?
        get() {
          return rulers
        }

      override fun placeChildren() {
      }
    }
  }
}

private fun assertCompositionThrowsIllegalArgument(
  recomposer: Recomposer,
  content: @Composable () -> Unit,
) {
  val composition = Composition(EmptyApplier(), recomposer)
  var thrown = false
  try {
    composition.setContent(content)
  } catch (_: IllegalArgumentException) {
    thrown = true
  } finally {
    composition.dispose()
  }
  assertTrue("Expected IllegalArgumentException to be thrown", thrown)
}
