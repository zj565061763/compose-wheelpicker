package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelPickerTest {
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

    // 无限尺寸容器下传入极大 unfocusedCount 时不发生整数溢出
    val capped = calculateSafeUnfocusedCount(
      maxSizePx = Constraints.Infinity,
      itemSizePx = 48,
      unfocusedCount = Int.MAX_VALUE,
    )
    assertTrue(capped > 0)
    assertTrue(48L * (capped.toLong() * 2L + 1L) <= 32_766L)

    // 超大但有限的 itemSize 换算像素时统一限制在 Compose Constraints 单轴上限内
    assertEquals(1, resolveWheelItemSizePx(density = Density(1f), itemSize = 0.001.dp))
    assertEquals(96, resolveWheelItemSizePx(density = Density(2f), itemSize = 48.dp))
    assertEquals(MAX_CONSTRAINT_AXIS_PX, resolveWheelItemSizePx(density = Density(1f), itemSize = 400_000.dp))
    assertEquals(MAX_CONSTRAINT_AXIS_PX, resolveWheelItemSizePx(density = Density(3f), itemSize = Float.MAX_VALUE.dp))
    assertEquals(0, calculateSafeUnfocusedCount(maxSizePx = Constraints.Infinity, itemSizePx = MAX_CONSTRAINT_AXIS_PX, unfocusedCount = 2))
    assertEquals(0, calculateSnappedIndex(0, MAX_CONSTRAINT_AXIS_PX / 2, count = 2, itemSizePx = MAX_CONSTRAINT_AXIS_PX))
    assertEquals(1, calculateSnappedIndex(0, MAX_CONSTRAINT_AXIS_PX / 2 + 1, count = 2, itemSizePx = MAX_CONSTRAINT_AXIS_PX))
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
  fun testCalculateScrollPercentage() {
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
    assertEquals(
      1f,
      calculateScrollPercentage(9, 5, 99, unfocusedCount = 2, itemSizePx = 100)!!,
      0.0001f,
    )

    // 极端索引差值不发生 Int 减法符号翻转溢出
    assertEquals(
      -1f,
      calculateScrollPercentage(Int.MIN_VALUE, 10, 0, unfocusedCount = 2, itemSizePx = 100)!!,
      0.0001f,
    )
    assertEquals(
      1f,
      calculateScrollPercentage(Int.MAX_VALUE, -10, 0, unfocusedCount = 2, itemSizePx = 100)!!,
      0.0001f,
    )
  }

  @Test
  fun testStateLifecycleAndSaver() {
    val state = WheelPickerState(initialIndex = 7)
    assertEquals(-1, state.currentIndex)
    assertEquals(-1, state.snappedIndex)
    assertNull(state.scrollPercentageOf(7))

    // 未挂载或无数据时，Saver 仍保留 initialIndex
    val savedBeforeAttach = with(WheelPickerState.Saver) {
      SaverScope { true }.save(state)
    }
    assertEquals(7, savedBeforeAttach)

    val ownerA = Any()
    state.updateAttachment(owner = ownerA, count = 10, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(7, state.currentIndex)
    assertEquals(7, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(7)!!, 0.0001f)

    // 新组件接管后，旧组件销毁不应清空新组件状态
    val ownerB = Any()
    state.updateAttachment(owner = ownerB, count = 5, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(4, state.currentIndex)
    assertEquals(4, state.snappedIndex)
    state.detach(ownerA)
    assertEquals(4, state.currentIndex)
    assertEquals(4, state.snappedIndex)

    // count 变为 0 时，状态重置为 -1，Saver 保存进入空列表前的有效选中项 4
    state.updateAttachment(owner = ownerB, count = 0, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(-1, state.currentIndex)
    assertEquals(-1, state.snappedIndex)
    assertNull(state.scrollPercentageOf(4))
    val savedWhenEmpty = with(WheelPickerState.Saver) {
      SaverScope { true }.save(state)
    }
    assertEquals(4, savedWhenEmpty)

    // 模拟 LazyList 曾非空后变为空时将 firstVisibleItemIndex 重置为 0 的行为，数据恢复后仍能找回索引 4
    state.lazyListState.requestScrollToItem(0)
    state.updateAttachment(owner = ownerB, count = 10, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(4, state.currentIndex)
    assertEquals(4, state.snappedIndex)

    // 当前持有者销毁后重置为 -1
    state.detach(ownerB)
    assertEquals(-1, state.currentIndex)
    assertEquals(-1, state.snappedIndex)
    assertNull(state.scrollPercentageOf(4))
  }

  @Test
  fun testScrollBeforeAttachAndBeforeFirstLayoutDoesNotSuspend() {
    runBlocking {
      val state = WheelPickerState(initialIndex = 0)
      state.scrollToIndex(6)
      assertEquals(-1, state.snappedIndex)

      val owner = Any()
      state.updateAttachment(owner = owner, count = 10, unfocusedCount = 2, itemSizePx = 48)
      assertEquals(6, state.currentIndex)
      assertEquals(6, state.snappedIndex)

      // 已挂载但尚未完成首次测量（totalItemsCount == 0）时，连续调用 scrollToIndex / animateScrollToIndex 均不挂起
      state.scrollToIndex(3)
      assertEquals(3, state.currentIndex)
      assertEquals(3, state.snappedIndex)

      state.animateScrollToIndex(99)
      assertEquals(9, state.currentIndex)
      assertEquals(9, state.snappedIndex)

      // count == 0 时调用 animateScrollToIndex 同样不阻塞且覆盖待恢复位置
      state.updateAttachment(owner = owner, count = 0, unfocusedCount = 2, itemSizePx = 48)
      state.lazyListState.requestScrollToItem(0)
      state.animateScrollToIndex(8)
      assertEquals(-1, state.snappedIndex)

      state.updateAttachment(owner = owner, count = 10, unfocusedCount = 2, itemSizePx = 48)
      assertEquals(8, state.currentIndex)
      assertEquals(8, state.snappedIndex)

      // 已完成测量（totalItemsCount > 0）但尚未完成首次摆放（isPositioned == false）时，两个跳转 API 均不挂起
      val measuredUnplacedController = FakeWheelScrollController(initialIndex = 0).apply {
        isPlaced = false
        pauseAnimations = true
      }
      val measuredUnplacedState = WheelPickerState(
        initialIndex = 0,
        scrollController = measuredUnplacedController,
      )
      val unplacedOwner = Any()
      measuredUnplacedState.updateAttachment(
        owner = unplacedOwner,
        count = 12,
        unfocusedCount = 2,
        itemSizePx = 48,
      )
      measuredUnplacedController.applyMeasure(count = 12)
      assertEquals(12, measuredUnplacedController.totalItemsCount)
      assertFalse(measuredUnplacedController.isPositioned)

      measuredUnplacedState.scrollToIndex(8)
      assertEquals(8, measuredUnplacedState.currentIndex)
      assertEquals(8, measuredUnplacedState.snappedIndex)

      measuredUnplacedState.animateScrollToIndex(5)
      assertEquals(5, measuredUnplacedState.currentIndex)
      assertEquals(5, measuredUnplacedState.snappedIndex)
      assertTrue(measuredUnplacedController.animateTargetsRequested.isEmpty())

      // 旧 1 项滚轮卸载后复用同一状态挂载 20 项新滚轮，首次测量前调用 animateScrollToIndex(8) 不丢失目标
      val remountController = FakeWheelScrollController(initialIndex = 0)
      val remountState = WheelPickerState(
        initialIndex = 0,
        scrollController = remountController,
      )
      val firstOwner = Any()
      val secondOwner = Any()
      remountState.updateAttachment(owner = firstOwner, count = 1, unfocusedCount = 2, itemSizePx = 48)
      remountController.applyMeasure(count = 1)
      assertTrue(remountController.isPositioned)
      assertEquals(0, remountState.snappedIndex)

      remountState.detach(firstOwner)
      assertFalse(remountController.isPositioned)
      assertEquals(1, remountController.totalItemsCount)

      remountState.updateAttachment(owner = secondOwner, count = 20, unfocusedCount = 2, itemSizePx = 48)
      assertFalse(remountController.isPositioned)
      remountState.animateScrollToIndex(8)
      assertTrue(remountController.animateTargetsRequested.isEmpty())
      assertEquals(8, remountState.snappedIndex)

      remountController.applyMeasure(count = 20)
      assertTrue(remountController.isPositioned)
      assertEquals(8, remountState.currentIndex)
      assertEquals(8, remountState.snappedIndex)
    }
  }

  @Test
  fun testOwnerSwitchWithSameParamsTriggersObserveSnapshot() {
    runBlocking {
      val state = WheelPickerState(initialIndex = 3)
      val ownerA = Any()
      val ownerB = Any()

      state.updateAttachment(owner = ownerA, count = 10, unfocusedCount = 2, itemSizePx = 48)

      val jobB = launch(start = CoroutineStart.UNDISPATCHED) {
        state.observeSnappedIndex(ownerB)
      }

      Snapshot.withMutableSnapshot {
        state.updateAttachment(owner = ownerB, count = 10, unfocusedCount = 2, itemSizePx = 48)
      }
      yield()
      assertEquals(3, state.snappedIndex)

      Snapshot.withMutableSnapshot {
        state.updateAttachment(owner = ownerB, count = 0, unfocusedCount = 2, itemSizePx = 48)
      }
      yield()
      assertEquals(-1, state.snappedIndex)

      Snapshot.withMutableSnapshot {
        state.updateAttachment(owner = ownerB, count = 10, unfocusedCount = 2, itemSizePx = 48)
      }
      yield()
      assertEquals(3, state.snappedIndex)

      jobB.cancel()

      // 飞行动画期间发生组件持有者接管时，立即取消旧动画并将目标夹紧至新组件边界
      val controller = FakeWheelScrollController(initialIndex = 1).apply {
        pauseAnimations = true
      }
      val handoffState = WheelPickerState(initialIndex = 1, scrollController = controller)
      val ownerC = Any()
      val ownerD = Any()
      handoffState.updateAttachment(owner = ownerC, count = 10, unfocusedCount = 2, itemSizePx = 48)
      controller.applyMeasure(count = 10)

      var cancelledByOwnerSwitch = false
      val inFlightJob = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
          handoffState.animateScrollToIndex(8)
        } catch (_: CancellationException) {
          cancelledByOwnerSwitch = true
        }
      }
      assertTrue(handoffState.isScrollInProgress)

      handoffState.updateAttachment(owner = ownerD, count = 5, unfocusedCount = 2, itemSizePx = 48)
      inFlightJob.join()
      assertTrue(cancelledByOwnerSwitch)
      assertEquals(4, handoffState.currentIndex)
      assertEquals(4, handoffState.snappedIndex)
    }
  }

  @Test
  fun testAutoSnapRecoversAfterRepeatedAnimateScrollCancellation() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 0)
      val state = WheelPickerState(initialIndex = 0, scrollController = controller)
      val owner = Any()

      Snapshot.withMutableSnapshot {
        state.updateAttachment(owner = owner, count = 10, unfocusedCount = 2, itemSizePx = 48)
        controller.applyMeasure(count = 10)
      }
      assertEquals(0, state.snappedIndex)

      val observeJob = launch(start = CoroutineStart.UNDISPATCHED) {
        state.observeSnappedIndex(owner)
      }

      // 1. 启动 animateScrollToIndex(5)，滚动产生非居中偏移（offset = 30px，实时焦点为 1，但尚未对齐）后取消
      controller.pauseAnimations = true
      val scrollJob1 = launch(start = CoroutineStart.UNDISPATCHED) {
        state.animateScrollToIndex(5)
      }
      controller.setPositionInScroll(index = 0, offset = 30)
      Snapshot.sendApplyNotifications()
      yield()

      scrollJob1.cancel()
      scrollJob1.join()
      Snapshot.sendApplyNotifications()

      // 2. observeSnappedIndex 收到空闲快照并完成 yield() 后进入自动吸附（此时仍在 index = 0, offset = 30，挂起在动画中）
      repeat(2) {
        yield()
      }
      Snapshot.sendApplyNotifications()
      assertTrue(state.isScrollInProgress)
      assertEquals(0, state.snappedIndex)

      // 3. 在自动吸附尚未移动任何像素时，连续多次启动并立即取消 animateScrollToIndex(5) 抢占自动吸附
      repeat(3) {
        val scrollJob2 = launch(start = CoroutineStart.UNDISPATCHED) {
          state.animateScrollToIndex(5)
        }
        Snapshot.sendApplyNotifications()
        scrollJob2.cancel()
        scrollJob2.join()
        Snapshot.sendApplyNotifications()
        repeat(2) {
          yield()
        }
      }

      assertEquals(30, controller.firstVisibleItemScrollOffset)
      assertEquals(0, state.snappedIndex)

      // 4. 放行动画，验证自动吸附不会被 lastAutoSnapState 跳过，最终归位至 (index = 1, offset = 0) 并更新 snappedIndex
      controller.pauseAnimations = false
      controller.resumeActiveAnimation()
      repeat(5) {
        Snapshot.sendApplyNotifications()
        yield()
      }

      assertEquals(1, controller.firstVisibleItemIndex)
      assertEquals(0, controller.firstVisibleItemScrollOffset)
      assertEquals(1, state.currentIndex)
      assertEquals(1, state.snappedIndex)

      observeJob.cancel()
    }
  }

  @Test
  fun testTargetIndexPreservedAcrossEmptyListRemeasureAndTransitions() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 0)
      val state = WheelPickerState(initialIndex = 0, scrollController = controller)
      val ownerA = Any()

      state.updateAttachment(owner = ownerA, count = 10, unfocusedCount = 2, itemSizePx = 48)
      controller.applyMeasure(count = 10)
      assertEquals(0, state.snappedIndex)

      // 场景 A：count 变为 0 -> 调用 scrollToIndex(8) -> 空列表重测量重置位置为 0 -> 恢复 10 条数据
      state.updateAttachment(owner = ownerA, count = 0, unfocusedCount = 2, itemSizePx = 48)
      controller.applyMeasure(count = 0)
      state.scrollToIndex(8)
      controller.applyMeasure(count = 0)
      assertEquals(0, controller.firstVisibleItemIndex)

      state.updateAttachment(owner = ownerA, count = 10, unfocusedCount = 2, itemSizePx = 48)
      controller.applyMeasure(count = 10)
      assertEquals(8, controller.firstVisibleItemIndex)
      assertEquals(8, state.currentIndex)
      assertEquals(8, state.snappedIndex)

      // 场景 B：发起 animateScrollToIndex(6) 后重组为 count = 0 并重测量 -> 恢复 10 条数据时仍落在 6
      state.scrollToIndex(2)
      assertEquals(2, state.snappedIndex)

      controller.pauseAnimations = true
      val animJob = launch(start = CoroutineStart.UNDISPATCHED) {
        state.animateScrollToIndex(6)
      }
      state.updateAttachment(owner = ownerA, count = 0, unfocusedCount = 2, itemSizePx = 48)
      controller.applyMeasure(count = 0)
      animJob.join()
      assertEquals(0, controller.firstVisibleItemIndex)

      state.updateAttachment(owner = ownerA, count = 10, unfocusedCount = 2, itemSizePx = 48)
      controller.applyMeasure(count = 10)
      assertEquals(6, controller.firstVisibleItemIndex)
      assertEquals(6, state.currentIndex)
      assertEquals(6, state.snappedIndex)

      // 场景 C：组件卸载期间调用 scrollToIndex(9)，随后新组件先以 count = 0 挂载并重测量，再加载 10 条数据
      state.detach(ownerA)
      state.scrollToIndex(9)

      val ownerB = Any()
      state.updateAttachment(owner = ownerB, count = 0, unfocusedCount = 2, itemSizePx = 48)
      controller.applyMeasure(count = 0)
      assertEquals(0, controller.firstVisibleItemIndex)

      state.updateAttachment(owner = ownerB, count = 10, unfocusedCount = 2, itemSizePx = 48)
      controller.applyMeasure(count = 10)
      assertEquals(9, controller.firstVisibleItemIndex)
      assertEquals(9, state.currentIndex)
      assertEquals(9, state.snappedIndex)
    }
  }

  @Test
  fun testScrollPercentageReturnsNullAfterDetachDespiteResidualLayoutInfo() {
    val controller = FakeWheelScrollController(initialIndex = 5)
    val state = WheelPickerState(initialIndex = 5, scrollController = controller)

    assertNull(state.scrollPercentageOf(5))

    controller.applyMeasure(count = 10, unfocusedCount = 2, itemSizePx = 48)
    assertNull(state.scrollPercentageOf(5))

    val ownerA = Any()
    state.updateAttachment(owner = ownerA, count = 10, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(5, state.currentIndex)
    assertEquals(5, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(5)!!, 0.0001f)

    state.detach(ownerA)
    assertEquals(-1, state.currentIndex)
    assertEquals(-1, state.snappedIndex)
    assertNull(state.scrollPercentageOf(5))

    val ownerB = Any()
    state.updateAttachment(owner = ownerB, count = 10, unfocusedCount = 1, itemSizePx = 60)
    controller.applyMeasure(count = 10, unfocusedCount = 1, itemSizePx = 60)
    assertEquals(5, state.currentIndex)
    assertEquals(5, state.snappedIndex)
    assertEquals(0f, state.scrollPercentageOf(5)!!, 0.0001f)
    assertEquals(-0.5f, state.scrollPercentageOf(4)!!, 0.0001f)
  }

  @Test
  fun testNativeKeyTrackingSyncsSnappedIndexOnInsertDeleteAndReorder() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 2).apply {
        supportsNativeWindowTracking = true
      }
      val state = WheelPickerState(initialIndex = 2, scrollController = controller)
      var items by mutableStateOf(listOf("A", "B", "C", "D", "E"))

      val harness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = controller,
        state = state,
      )
      try {
        harness.composeWheel(
          countProvider = { items.size },
          keyProvider = {
            val snapshot = items
            val fn: (Int) -> Any = { idx -> snapshot[idx] }
            fn
          },
        )
        harness.stepFrame()
        assertEquals(2, state.snappedIndex)
        assertEquals("C", items[state.snappedIndex])

        // 1. 头部插入 2 项，LazyList 按 key 将 firstVisibleItemIndex 修至 4，snappedIndex 自动同步为 4
        items = listOf("X", "Y", "A", "B", "C", "D", "E")
        harness.stepFrame()
        assertEquals(4, state.snappedIndex)
        assertEquals("C", items[state.snappedIndex])

        // 2. 删除前 3 项，LazyList 按 key 将 firstVisibleItemIndex 修至 1，snappedIndex 自动同步为 1
        items = listOf("B", "C", "D", "E")
        harness.stepFrame()
        assertEquals(1, state.snappedIndex)
        assertEquals("C", items[state.snappedIndex])

        // 3. 常规窗口内重排，选中项 "C" 移动到索引 3，snappedIndex 自动同步为 3
        items = listOf("E", "D", "B", "C")
        harness.stepFrame()
        assertEquals(3, state.snappedIndex)
        assertEquals("C", items[state.snappedIndex])
      } finally {
        harness.dispose()
      }
    }
  }

  @Test
  fun testConcurrentScrollPreemptionAndClocklessExceptionCleanup() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 0).apply {
        pauseAnimations = true
      }
      val state = WheelPickerState(initialIndex = 0, scrollController = controller)
      val items by mutableStateOf((0..29).toList())

      val harness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = controller,
        state = state,
      )
      try {
        harness.composeWheelWithChildParam(itemsProvider = { items })
        harness.stepFrame()
        assertEquals(0, state.snappedIndex)

        // 1. 正在执行的 animateScrollToIndex(15) 被后续 scrollToIndex(4) 抢占取消
        var firstCancelled = false
        val firstJob = launch(harness.clock, start = CoroutineStart.UNDISPATCHED) {
          try {
            state.animateScrollToIndex(15)
          } catch (_: CancellationException) {
            firstCancelled = true
          }
        }
        assertTrue(state.isScrollInProgress)

        state.scrollToIndex(4)
        firstJob.join()
        assertTrue(firstCancelled)
        assertEquals(4, state.currentIndex)
        assertEquals(4, state.snappedIndex)

        // 2. 含 MonotonicFrameClock 的协程调用 animateScrollToIndex 正常完成
        controller.pauseAnimations = false
        controller.emulateRealLazyListAnimateTiming = true
        val secondJob = launch(harness.clock, start = CoroutineStart.UNDISPATCHED) {
          state.animateScrollToIndex(12)
        }
        harness.advanceUntilIdle(secondJob)
        assertEquals(12, state.currentIndex)
        assertEquals(12, state.snappedIndex)

        // 3. 无 MonotonicFrameClock 的外部协程调用 animateScrollToIndex 抛出 IllegalStateException 后，不残留执行状态且后续手势正常吸附更新
        var missingClockThrown = false
        try {
          state.animateScrollToIndex(25)
        } catch (_: IllegalStateException) {
          missingClockThrown = true
        }
        assertTrue(missingClockThrown)
        assertEquals(12, state.snappedIndex)

        controller.beginGestureScroll(index = 28, offset = 30)
        harness.stepFrame()
        controller.endGestureScroll()
        harness.stepFrame()
        harness.stepFrame()
        assertEquals(29, state.currentIndex)
        assertEquals(29, state.snappedIndex)
      } finally {
        harness.dispose()
      }
    }
  }

  @Test
  fun testShrinkWithNonZeroScrollOffsetClampsSnappedIndex() {
    val controller = FakeWheelScrollController(initialIndex = 8)
    val state = WheelPickerState(initialIndex = 8, scrollController = controller)
    val owner = Any()

    var items = (0..9).toList()
    state.updateAttachment(owner = owner, count = items.size, unfocusedCount = 2, itemSizePx = 48)
    controller.applyMeasure(count = items.size)
    assertEquals(8, state.snappedIndex)

    controller.setPositionInScroll(index = 2, offset = 20)
    assertFalse(state.isScrollInProgress)
    items = (0..4).toList()
    state.updateAttachment(owner = owner, count = items.size, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(4, state.snappedIndex)
    assertEquals(4, items[state.snappedIndex])

    controller.beginGestureScroll(index = 1, offset = 20)
    items = (0..2).toList()
    state.updateAttachment(owner = owner, count = items.size, unfocusedCount = 2, itemSizePx = 48)
    assertEquals(2, state.snappedIndex)
    assertEquals(2, items[state.snappedIndex])
    controller.endGestureScroll()
  }

  @Test
  fun testNestedScrollPreFlingSuspensionNotInterceptedByAutoSnap() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 0).apply {
        emulateRealLazyListAnimateTiming = true
      }
      val state = WheelPickerState(initialIndex = 0, scrollController = controller)
      val items by mutableStateOf((0..49).toList())
      var userScrollEnabled by mutableStateOf(false)

      val harness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = controller,
        state = state,
      )
      try {
        harness.composeWheelWithChildParam(
          itemsProvider = { items },
          userScrollEnabledProvider = { userScrollEnabled },
        )
        harness.stepFrame()
        assertEquals(0, state.snappedIndex)

        val dragStart = controller.emitDragStart()
        controller.beginGestureScroll(index = 5, offset = 20)
        harness.stepFrame()

        controller.emitDragStop(dragStart)
        controller.endGestureScroll()
        Snapshot.sendApplyNotifications()
        repeat(3) {
          yield()
        }
        assertFalse(state.isScrollInProgress)
        assertTrue(controller.animateTargetsRequested.isEmpty())

        val preFlingHandoffJob = launch(harness.clock, start = CoroutineStart.UNDISPATCHED) {
          harness.clock.withFrameNanos {
            controller.beginGestureScroll(index = 12, offset = 18)
          }
        }
        harness.stepFrame()
        preFlingHandoffJob.join()

        assertTrue(state.isScrollInProgress)
        assertEquals(12, controller.firstVisibleItemIndex)
        assertTrue(controller.animateTargetsRequested.isEmpty())

        controller.endGestureScroll()
        harness.stepFrame()
        harness.stepFrame()

        assertEquals(12, controller.firstVisibleItemIndex)
        assertEquals(0, controller.firstVisibleItemScrollOffset)
        assertEquals(12, state.snappedIndex)

        // 启用离手保护时，拖动以 DragInteraction.Cancel 结束且父容器多帧挂起 onPreFling 期间不提前自动吸附
        userScrollEnabled = true
        harness.stepFrame()
        controller.animateTargetsRequested.clear()

        val cancelDragStart = controller.emitDragStart()
        controller.beginGestureScroll(index = 15, offset = 20)
        harness.stepFrame()

        controller.emitDragCancel(cancelDragStart)
        controller.endGestureScroll()
        repeat(4) {
          harness.stepFrame()
        }
        assertFalse(state.isScrollInProgress)
        assertTrue(controller.animateTargetsRequested.isEmpty())
        assertEquals(20, controller.firstVisibleItemScrollOffset)

        state.onFlingHandoffResolved(checkNotNull(harness.latestOwner))
        harness.stepFrame()
        harness.stepFrame()
        assertEquals(15, controller.firstVisibleItemIndex)
        assertEquals(0, controller.firstVisibleItemScrollOffset)
        assertEquals(15, state.snappedIndex)

        // 拖动期间滚动被禁用并发出 DragInteraction.Cancel 时，不卡在 60 帧离手保护，直接完成吸附
        controller.animateTargetsRequested.clear()
        val disableDragStart = controller.emitDragStart()
        controller.beginGestureScroll(index = 18, offset = 30)
        harness.stepFrame()

        userScrollEnabled = false
        controller.emitDragCancel(disableDragStart)
        controller.endGestureScroll()
        repeat(3) {
          harness.stepFrame()
        }
        assertEquals(19, controller.firstVisibleItemIndex)
        assertEquals(0, controller.firstVisibleItemScrollOffset)
        assertEquals(19, state.snappedIndex)

        // 第 59 帧同批次连续第二次离手不共用旧 60 帧超时
        userScrollEnabled = true
        harness.stepFrame()
        controller.animateTargetsRequested.clear()
        val firstTimeoutDragStart = controller.emitDragStart()
        controller.beginGestureScroll(index = 20, offset = 16)
        harness.stepFrame()
        controller.emitDragStop(firstTimeoutDragStart)
        controller.endGestureScroll()
        repeat(59) {
          harness.stepFrame()
        }
        assertTrue(controller.animateTargetsRequested.isEmpty())

        val secondTimeoutDragStart = controller.emitDragStart()
        controller.beginGestureScroll(index = 21, offset = 18)
        controller.emitDragStop(secondTimeoutDragStart)
        controller.endGestureScroll()
        yield()
        Snapshot.sendApplyNotifications()
        repeat(15) {
          harness.stepFrame()
        }
        assertTrue(controller.animateTargetsRequested.isEmpty())

        repeat(50) {
          harness.stepFrame()
        }
        assertEquals(21, state.snappedIndex)
        assertEquals(0, controller.firstVisibleItemScrollOffset)
      } finally {
        harness.dispose()
      }
    }
  }

  @Test
  fun testViewportMeasurePolicyIntrinsicsClippingAndMainCompositionSync() {
    val density = Density(density = 1f, fontScale = 1f)

    for (orientation in listOf(WheelOrientation.Vertical, WheelOrientation.Horizontal)) {
      val viewportState = WheelViewportState(
        orientation = orientation,
        density = density,
        itemSizePx = 48,
        initialUnfocusedCount = 2,
      )
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
      val focusMeasurable = FakeBoxMeasurable(
        intrinsicCrossSize = 72,
      )
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
        assertEquals(Constraints.fixed(80, 48), focusMeasurable.lastMeasuredConstraints)

        val tightList = ThrowingIntrinsicMeasurable(measuredWidth = 80, measuredHeight = 48)
        val tightResult = with(policy) {
          measureScope.measure(listOf(tightList, focusMeasurable), Constraints.fixed(80, 20))
        }
        assertEquals(80, tightResult.width)
        assertEquals(20, tightResult.height)
        assertEquals(0, resolvedCount)
        assertTrue(viewportState.shouldClip)
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
        assertEquals(Constraints.fixed(48, 64), focusMeasurable.lastMeasuredConstraints)
      }
    }

    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 5)
      val state = WheelPickerState(initialIndex = 5, scrollController = controller)
      var useSecondBranch by mutableStateOf(false)
      var observedCurrentInComposition = -1
      val snappedEmissions = mutableListOf<Int>()

      val harness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = controller,
        state = state,
      )
      try {
        harness.composeBranchSwitchingWheel(
          branchProvider = { useSecondBranch },
          onReadCurrentIndex = { observedCurrentInComposition = it },
          onSnapped = { snappedEmissions.add(it) },
        )
        harness.stepFrame()
        assertEquals(5, observedCurrentInComposition)
        assertEquals(listOf(5), snappedEmissions)

        useSecondBranch = true
        harness.stepFrame()
        assertEquals(5, state.currentIndex)
        assertEquals(5, state.snappedIndex)
        assertEquals(listOf(5), snappedEmissions)
      } finally {
        harness.dispose()
      }
    }
  }

  @Test
  fun testScrollPercentageVisualDirectionWithReverseLayoutAndRtl() {
    assertFalse(WheelOrientation.Vertical.isVisualDirectionReversed(reverseLayout = false, layoutDirection = LayoutDirection.Ltr))
    assertFalse(WheelOrientation.Vertical.isVisualDirectionReversed(reverseLayout = false, layoutDirection = LayoutDirection.Rtl))
    assertTrue(WheelOrientation.Vertical.isVisualDirectionReversed(reverseLayout = true, layoutDirection = LayoutDirection.Ltr))
    assertTrue(WheelOrientation.Vertical.isVisualDirectionReversed(reverseLayout = true, layoutDirection = LayoutDirection.Rtl))

    assertFalse(WheelOrientation.Horizontal.isVisualDirectionReversed(reverseLayout = false, layoutDirection = LayoutDirection.Ltr))
    assertTrue(WheelOrientation.Horizontal.isVisualDirectionReversed(reverseLayout = false, layoutDirection = LayoutDirection.Rtl))
    assertTrue(WheelOrientation.Horizontal.isVisualDirectionReversed(reverseLayout = true, layoutDirection = LayoutDirection.Ltr))
    assertFalse(WheelOrientation.Horizontal.isVisualDirectionReversed(reverseLayout = true, layoutDirection = LayoutDirection.Rtl))

    val controller = FakeWheelScrollController(initialIndex = 5)
    val state = WheelPickerState(initialIndex = 5, scrollController = controller)
    val owner = Any()
    state.updateAttachment(
      owner = owner,
      count = 10,
      unfocusedCount = 2,
      itemSizePx = 100,
      reverseVisualDirection = true,
    )
    controller.applyMeasure(count = 10, unfocusedCount = 2, itemSizePx = 100)

    val centerPercentage = state.scrollPercentageOf(5)!!
    assertEquals(0f, centerPercentage, 0.0001f)
    assertEquals(java.lang.Float.floatToRawIntBits(0f), java.lang.Float.floatToRawIntBits(centerPercentage))
    assertEquals(1f / 3f, state.scrollPercentageOf(4)!!, 0.0001f)
    assertEquals(-1f / 3f, state.scrollPercentageOf(6)!!, 0.0001f)
  }

  @Test
  fun testViewportStateStabilityAndLastItemResidualOffsetClamp() {
    val density = Density(2f)
    val viewportState = WheelViewportState(
      orientation = WheelOrientation.Vertical,
      density = density,
      itemSizePx = 40,
      initialUnfocusedCount = 2,
    )
    val paddingRef = viewportState.contentPadding
    assertEquals(1, viewportState.onMeasure(WheelOrientation.Vertical, density, maxMainAxisPx = 150, itemSizePx = 40, requestedUnfocusedCount = 2))
    assertEquals(20.dp, viewportState.contentPadding.calculateTopPadding())
    assertEquals(0, viewportState.resolveUnfocusedCount(itemSizePx = 60, requestedUnfocusedCount = 2))
    assertSame(paddingRef, viewportState.contentPadding)

    runBlocking {
      // 1. 尾部缩容使原中间可见项在新列表测量完成后确实成为末项且残留正偏移时，将末项偏移归零
      val tailShrinkController = FakeWheelScrollController(initialIndex = 2)
      val tailShrinkState = WheelPickerState(initialIndex = 2, scrollController = tailShrinkController)
      var tailItems by mutableStateOf((0..4).toList())
      val tailHarness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = tailShrinkController,
        state = tailShrinkState,
      )
      try {
        tailHarness.composeWheelWithChildParam(itemsProvider = { tailItems })
        tailHarness.stepFrame()
        assertEquals(2, tailShrinkState.snappedIndex)

        tailShrinkController.setPositionInScroll(index = 2, offset = 20)
        // 新列表尚未完成测量（totalItemsCount 仍为 5）时，updateAttachment 不提前调用 requestScrollToItem 覆盖锚点
        tailShrinkState.updateAttachment(
          owner = checkNotNull(tailHarness.latestOwner),
          count = 3,
          unfocusedCount = 2,
          itemSizePx = 48,
        )
        assertEquals(20, tailShrinkController.firstVisibleItemScrollOffset)

        tailItems = (0..2).toList()
        tailHarness.stepFrame()
        assertTrue(tailShrinkController.animateTargetsRequested.isEmpty())
        assertEquals(2, tailShrinkController.firstVisibleItemIndex)
        assertEquals(0, tailShrinkController.firstVisibleItemScrollOffset)
        assertEquals(2, tailShrinkState.snappedIndex)
      } finally {
        tailHarness.dispose()
      }

      // 2. 父容器挂起 onPreFling 期间删除头部条目缩容时，保留存活稳定 key 锚点，最终吸附在重定位后的索引
      val headShrinkController = FakeWheelScrollController(initialIndex = 8).apply {
        supportsNativeWindowTracking = true
        emulateRealLazyListAnimateTiming = true
      }
      val headShrinkState = WheelPickerState(initialIndex = 8, scrollController = headShrinkController)
      var headItems by mutableStateOf((0..13).toList())
      val headHarness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = headShrinkController,
        state = headShrinkState,
      )
      try {
        headHarness.composeWheelWithChildParam(
          itemsProvider = { headItems },
          userScrollEnabledProvider = { true },
        )
        headHarness.stepFrame()
        assertEquals(8, headShrinkState.snappedIndex)

        val dragStart = headShrinkController.emitDragStart()
        headShrinkController.beginGestureScroll(index = 8, offset = 18)
        headHarness.stepFrame()
        headShrinkController.emitDragStop(dragStart)
        headShrinkController.endGestureScroll()
        Snapshot.sendApplyNotifications()
        yield()

        // 删除开头 6 项：snappedIndex 立即夹紧至 7 避免越界，测量后稳定 key = 8 重定位到索引 2
        headItems = headItems.drop(6)
        headHarness.stepFrame()
        assertEquals(7, headShrinkState.snappedIndex)
        assertEquals(2, headShrinkController.firstVisibleItemIndex)
        assertEquals(18, headShrinkController.firstVisibleItemScrollOffset)
        assertEquals(2, headShrinkState.currentIndex)
        val savedDuringPreFling = with(WheelPickerState.Saver) {
          SaverScope { true }.save(headShrinkState)
        }
        assertEquals(2, savedDuringPreFling)

        headShrinkState.onFlingHandoffResolved(checkNotNull(headHarness.latestOwner))
        headHarness.stepFrame()
        headHarness.stepFrame()
        assertEquals(2, headShrinkController.firstVisibleItemIndex)
        assertEquals(0, headShrinkController.firstVisibleItemScrollOffset)
        assertEquals(2, headShrinkState.snappedIndex)
        assertEquals(8, headItems[headShrinkState.snappedIndex])

        // 3. 拖到旧索引 6 恰好零偏移对齐后松手挂起 onPreFling，删除前 6 项时先夹紧至 7 而非误发布 6，交接完成后再结算为 0
        headItems = (0..13).toList()
        headHarness.stepFrame()
        headShrinkState.scrollToIndex(8)
        headHarness.stepFrame()
        assertEquals(8, headShrinkState.snappedIndex)

        val alignedDragStart = headShrinkController.emitDragStart()
        headShrinkController.beginGestureScroll(index = 6, offset = 0)
        headHarness.stepFrame()
        headShrinkController.emitDragStop(alignedDragStart)
        headShrinkController.endGestureScroll()
        Snapshot.sendApplyNotifications()
        yield()
        assertEquals(8, headShrinkState.snappedIndex)

        headShrinkState.updateAttachment(
          owner = checkNotNull(headHarness.latestOwner),
          count = 8,
          unfocusedCount = 2,
          itemSizePx = 48,
          flingGuardEnabled = true,
        )
        assertEquals(7, headShrinkState.snappedIndex)

        headItems = headItems.drop(6)
        headHarness.stepFrame()
        assertEquals(7, headShrinkState.snappedIndex)
        assertEquals(0, headShrinkController.firstVisibleItemIndex)
        assertEquals(0, headShrinkController.firstVisibleItemScrollOffset)

        headShrinkState.onFlingHandoffResolved(checkNotNull(headHarness.latestOwner))
        headHarness.stepFrame()
        assertEquals(0, headShrinkState.snappedIndex)
        assertEquals(6, headItems[headShrinkState.snappedIndex])
      } finally {
        headHarness.dispose()
      }
    }
  }

  @Test
  fun testToggleVisibilityAndRemountClearsResidualLazyListKey() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 0).apply {
        supportsNativeWindowTracking = true
      }
      val state = WheelPickerState(initialIndex = 0, scrollController = controller)
      var visible by mutableStateOf(true)
      var items by mutableStateOf(listOf("A", "B", "C"))

      val harness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = controller,
        state = state,
      )
      try {
        harness.composeToggleableWheel(
          visibleProvider = { visible },
          countProvider = { items.size },
          keyProvider = {
            val snapshot = items
            val fn: (Int) -> Any = { idx -> snapshot[idx] }
            fn
          },
        )
        harness.stepFrame()
        assertEquals(0, state.snappedIndex)

        withContext(harness.clock) {
          state.scrollToIndex(2)
        }
        assertEquals(2, state.snappedIndex)

        // 卸载期间数据重排且请求跳到与卸载前相同的索引 2，重新挂载时不应被残留旧 key 拉回索引 0
        visible = false
        harness.stepFrame()
        assertEquals(-1, state.snappedIndex)

        items = listOf("C", "A", "B")
        state.scrollToIndex(2)
        visible = true
        harness.stepFrame()
        assertEquals(2, state.snappedIndex)
        assertEquals("B", items[state.snappedIndex])
      } finally {
        harness.dispose()
      }
    }
  }

  @Test
  fun testShrinkBeforeRecompositionScrollToIndexSyncsLiveCountWithoutOutOfBounds() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 8).apply {
        supportsNativeWindowTracking = true
      }
      val state = WheelPickerState(initialIndex = 8, scrollController = controller)
      val stateList = mutableStateListOf<String>().also { list ->
        repeat(12) { list.add("Item-$it") }
      }
      val renderedValues = mutableListOf<String>()

      val harness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = controller,
        state = state,
      )
      try {
        harness.composeLiveListWheel(
          count = { stateList.size },
          key = { stateList[it] },
          onRenderItem = { index -> renderedValues.add(stateList[index]) },
        )
        harness.stepFrame()
        assertEquals(8, state.snappedIndex)

        // 1. 同一操作中将 SnapshotStateList 从 12 项缩容至 1 项并立即在重组前调用 scrollToIndex(0)，不越界且立即结算 snappedIndex
        renderedValues.clear()
        stateList.removeRange(1, 12)
        state.scrollToIndex(0)
        assertEquals(0, state.snappedIndex)
        assertEquals(0, state.currentIndex)
        assertEquals(listOf("Item-0"), renderedValues)

        harness.stepFrame()
        assertEquals(0, state.snappedIndex)
        assertEquals("Item-0", stateList[state.snappedIndex])

        // 2. 重组前清空列表并立即调用 scrollToIndex(3)，转为预跳转记录目标并在后续恢复数据时定位到 3
        stateList.addAll((1..5).map { "Extra-$it" })
        harness.stepFrame()
        state.scrollToIndex(4)
        assertEquals(4, state.snappedIndex)

        stateList.clear()
        state.scrollToIndex(3)
        assertEquals(-1, state.snappedIndex)

        stateList.addAll((0..6).map { "Restored-$it" })
        harness.stepFrame()
        assertEquals(3, state.snappedIndex)
        assertEquals("Restored-3", stateList[state.snappedIndex])

        // 3. 动态 count 返回负数时立即抛出 IllegalArgumentException
        var negativeCountRejected = false
        val invalidState = WheelPickerState(initialIndex = 0, scrollController = FakeWheelScrollController(0))
        invalidState.updateAttachment(
          owner = Any(),
          count = 1,
          unfocusedCount = 2,
          itemSizePx = 48,
          countProvider = { -1 },
        )
        try {
          invalidState.scrollToIndex(0)
        } catch (_: IllegalArgumentException) {
          negativeCountRejected = true
        }
        assertTrue(negativeCountRejected)
      } finally {
        harness.dispose()
      }
    }
  }

  @Test
  fun testOwnerSwitchAndDetachDuringDragResetsDraggedState() {
    runBlocking {
      // 验证公开默认与带参构造函数正常创建及保存初始索引
      val defaultState = WheelPickerState()
      val savedDefault = with(WheelPickerState.Saver) {
        SaverScope { true }.save(defaultState)
      }
      assertEquals(0, savedDefault)

      val controller = FakeWheelScrollController(initialIndex = 1)
      val state = WheelPickerState(initialIndex = 1, scrollController = controller)
      val ownerA = Any()
      val ownerB = Any()
      val ownerC = Any()

      state.updateAttachment(
        owner = ownerA,
        count = 10,
        unfocusedCount = 2,
        itemSizePx = 48,
        flingGuardEnabled = true,
      )
      controller.applyMeasure(count = 10)

      val observerJobA = launch(start = CoroutineStart.UNDISPATCHED) {
        state.observeSnappedIndex(ownerA)
      }
      Snapshot.sendApplyNotifications()
      repeat(3) {
        yield()
      }
      assertEquals(1, state.snappedIndex)

      // ownerA 处于拖拽与非零偏移期间，同帧切换至 ownerB，且新观察协程先于旧观察协程 finally 执行
      controller.emitDragStart()
      controller.setPositionInScroll(index = 2, offset = 20)
      Snapshot.sendApplyNotifications()
      repeat(3) {
        yield()
      }
      assertEquals(1, state.snappedIndex)

      state.updateAttachment(
        owner = ownerB,
        count = 10,
        unfocusedCount = 2,
        itemSizePx = 48,
        flingGuardEnabled = true,
      )
      val observerJobB = launch(start = CoroutineStart.UNDISPATCHED) {
        state.observeSnappedIndex(ownerB)
      }
      observerJobA.cancel()
      observerJobA.join()
      controller.applyMeasure(count = 10)
      Snapshot.sendApplyNotifications()
      repeat(4) {
        yield()
      }

      // 切换后编程式跳转能正常结算 snappedIndex，非零偏移能正常触发自动吸附
      state.scrollToIndex(6)
      assertEquals(6, state.snappedIndex)

      controller.setPositionInScroll(index = 3, offset = 30)
      Snapshot.sendApplyNotifications()
      repeat(6) {
        yield()
      }
      assertEquals(0, controller.firstVisibleItemScrollOffset)
      assertEquals(4, state.snappedIndex)

      // 拖拽期间直接卸载 ownerB，后续重挂载 ownerC 时不残留拖拽阻塞
      controller.emitDragStart()
      Snapshot.sendApplyNotifications()
      repeat(2) {
        yield()
      }
      state.detach(ownerB)
      observerJobB.cancel()
      observerJobB.join()

      state.updateAttachment(
        owner = ownerC,
        count = 10,
        unfocusedCount = 2,
        itemSizePx = 48,
        flingGuardEnabled = true,
      )
      controller.applyMeasure(count = 10)
      state.scrollToIndex(8)
      assertEquals(8, state.snappedIndex)
    }
  }

  @Test
  fun testUnpositionedNonZeroOffsetDoesNotSpinAndSnapsOncePositioned() {
    runBlocking {
      // 1. 有帧时钟时，未摆放且存在非零偏移不得每帧唤醒空转，摆放完成后自动完成吸附
      val framedController = FakeWheelScrollController(initialIndex = 2).also {
        it.isPlaced = false
      }
      val framedState = WheelPickerState(initialIndex = 2, scrollController = framedController)
      val framedOwner = Any()
      val clock = BroadcastFrameClock()

      framedState.updateAttachment(
        owner = framedOwner,
        count = 10,
        unfocusedCount = 2,
        itemSizePx = 48,
      )
      framedController.applyMeasure(count = 10)
      framedController.setPositionInScroll(index = 2, offset = 30)
      assertFalse(framedController.isPositioned)

      val framedObserverJob = launch(clock, start = CoroutineStart.UNDISPATCHED) {
        framedState.observeSnappedIndex(framedOwner)
      }
      try {
        Snapshot.sendApplyNotifications()
        repeat(5) {
          yield()
        }
        assertFalse(clock.hasAwaiters)
        assertTrue(framedController.animateTargetsRequested.isEmpty())
        assertEquals(30, framedController.firstVisibleItemScrollOffset)

        framedController.isPlaced = true
        Snapshot.sendApplyNotifications()
        repeat(6) {
          yield()
        }
        assertEquals(listOf(3), framedController.animateTargetsRequested)
        assertEquals(0, framedController.firstVisibleItemScrollOffset)
        assertEquals(3, framedState.snappedIndex)
      } finally {
        framedObserverJob.cancel()
        framedObserverJob.join()
      }

      // 2. 无帧时钟时，未摆放且存在非零偏移不得陷入 yield 忙等循环，摆放完成后正常吸附
      val clocklessController = FakeWheelScrollController(initialIndex = 1).also {
        it.isPlaced = false
      }
      val clocklessState = WheelPickerState(initialIndex = 1, scrollController = clocklessController)
      val clocklessOwner = Any()

      clocklessState.updateAttachment(
        owner = clocklessOwner,
        count = 10,
        unfocusedCount = 2,
        itemSizePx = 48,
      )
      clocklessController.applyMeasure(count = 10)
      clocklessController.setPositionInScroll(index = 1, offset = 28)

      val clocklessObserverJob = launch(start = CoroutineStart.UNDISPATCHED) {
        clocklessState.observeSnappedIndex(clocklessOwner)
      }
      try {
        Snapshot.sendApplyNotifications()
        repeat(4) {
          yield()
        }
        clocklessController.isPositionedReadCount = 0
        repeat(20) {
          yield()
        }
        assertEquals(0, clocklessController.isPositionedReadCount)
        assertEquals(28, clocklessController.firstVisibleItemScrollOffset)

        clocklessController.isPlaced = true
        Snapshot.sendApplyNotifications()
        repeat(6) {
          yield()
        }
        assertEquals(0, clocklessController.firstVisibleItemScrollOffset)
        assertEquals(2, clocklessState.snappedIndex)
      } finally {
        clocklessObserverJob.cancel()
        clocklessObserverJob.join()
      }
    }
  }

  @Test
  fun testAwaitIdleDoesNotReevaluatePerScrollOffsetChange() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 0)
      val state = WheelPickerState(initialIndex = 0, scrollController = controller)
      val owner = Any()
      val frameClock = BroadcastFrameClock()

      state.updateAttachment(
        owner = owner,
        count = 10,
        unfocusedCount = 2,
        itemSizePx = 48,
      )
      controller.applyMeasure(count = 10)

      val observerJob = launch(frameClock, start = CoroutineStart.UNDISPATCHED) {
        state.observeSnappedIndex(owner)
      }
      try {
        Snapshot.sendApplyNotifications()
        yield()
        assertEquals(0, state.snappedIndex)

        // 先进入拖拽滚动并松手，让观察协程在帧边界等待窗口后因新滚动启动进入 awaitIdle
        controller.beginGestureScroll(index = 1, offset = 12)
        Snapshot.sendApplyNotifications()
        yield()

        controller.endGestureScroll()
        Snapshot.sendApplyNotifications()
        yield()

        // 帧边界到达前启动惯性滑动，随后推进一帧使观察协程挂起在 awaitIdle 内
        controller.beginGestureScroll(index = 2, offset = 16)
        frameClock.sendFrame(16_000_000L)
        Snapshot.sendApplyNotifications()
        yield()
        // 消费外层流挂起前的首次快照通知后重置计数
        controller.setPositionInScroll(index = 2, offset = 20)
        Snapshot.sendApplyNotifications()
        yield()

        controller.totalItemsCountReadCount = 0
        for (step in 1..10) {
          controller.setPositionInScroll(index = 2 + step / 3, offset = (step * 7) % 48)
          Snapshot.sendApplyNotifications()
          yield()
        }
        assertEquals(0, controller.totalItemsCountReadCount)

        // 惯性滑动停止后正常恢复自动吸附并结算 snappedIndex
        controller.setPositionInScroll(index = 5, offset = 30)
        controller.endGestureScroll()
        Snapshot.sendApplyNotifications()
        yield()
        frameClock.sendFrame(32_000_000L)
        repeat(4) {
          Snapshot.sendApplyNotifications()
          yield()
        }
        assertEquals(6, state.snappedIndex)
      } finally {
        observerJob.cancel()
        observerJob.join()
      }
    }
  }

  @Test
  fun testSameFrameMutateScrollAndRestoreOriginalCountSyncsState() {
    runBlocking {
      val controller = FakeWheelScrollController(initialIndex = 2).also {
        it.supportsNativeWindowTracking = true
      }
      val state = WheelPickerState(initialIndex = 2, scrollController = controller)
      val stateList = mutableStateListOf<String>().also { list ->
        repeat(10) { list.add("Init-$it") }
      }
      var countInvocationCount = 0

      val harness = WheelCompositionHarness(
        parentContext = coroutineContext,
        controller = controller,
        state = state,
      )
      try {
        harness.composeLiveListWheel(
          count = {
            countInvocationCount++
            stateList.size
          },
          key = { stateList[it] },
          onRenderItem = { index -> stateList[index] },
        )
        harness.stepFrame()
        assertEquals(2, state.snappedIndex)
        assertEquals(2, state.currentIndex)

        // 1. 同帧内 10 -> 0 -> scrollToIndex(7) -> 回填 10 项：重组测量后正常定位到 7，scrollPercentageOf 非空
        stateList.clear()
        state.scrollToIndex(7)
        assertEquals(-1, state.snappedIndex)
        repeat(10) { stateList.add("Refilled-$it") }

        harness.stepFrame()
        assertEquals(7, state.currentIndex)
        assertEquals(7, state.snappedIndex)
        assertEquals(0f, state.scrollPercentageOf(7))
        assertEquals("Refilled-7", stateList[state.snappedIndex])

        // 2. 同帧内 10 -> 20 -> scrollToIndex(17) -> 缩回 10 项：currentIndex 与 snappedIndex 均收敛至 9 不越界
        repeat(10) { stateList.add("Expanded-${it + 10}") }
        state.scrollToIndex(17)
        assertEquals(17, state.snappedIndex)
        stateList.removeRange(10, 20)

        harness.stepFrame()
        assertEquals(9, state.currentIndex)
        assertEquals(9, state.snappedIndex)
        assertEquals(0f, state.scrollPercentageOf(9))
        assertEquals("Refilled-9", stateList[state.snappedIndex])

        // 3. 条目数不变时连续更新滚动偏移（含滚动中与逐次静止快照发射），调用方的 count() 均不重复执行
        countInvocationCount = 0
        for (step in 0..9) {
          controller.setPositionInScroll(index = step, offset = 0)
          Snapshot.sendApplyNotifications()
          yield()
        }
        controller.beginGestureScroll(index = 8, offset = 4)
        for (step in 1..10) {
          controller.setPositionInScroll(index = 8 - step / 4, offset = (step * 4) % 48)
          Snapshot.sendApplyNotifications()
          yield()
        }
        controller.setPositionInScroll(index = 5, offset = 0)
        controller.endGestureScroll()
        harness.stepFrame()
        assertEquals(0, countInvocationCount)
        assertEquals(5, state.snappedIndex)

        // 4. 选中第 4 项（index = 3）后，同一回调清空或缩容列表立即读取公开状态即返回空态或夹紧值，回填后恢复至 3
        state.scrollToIndex(3)
        assertEquals(3, state.snappedIndex)

        stateList.removeRange(2, 10)
        assertEquals(1, state.currentIndex)
        assertEquals(1, state.snappedIndex)
        assertEquals("Refilled-1", stateList[state.snappedIndex])
        harness.stepFrame()

        stateList.clear()
        repeat(10) { stateList.add("Third-$it") }
        harness.stepFrame()
        state.scrollToIndex(3)
        assertEquals(3, state.snappedIndex)

        stateList.clear()
        assertEquals(-1, state.currentIndex)
        assertEquals(-1, state.snappedIndex)
        assertNull(state.scrollPercentageOf(3))

        harness.stepFrame()
        assertEquals(-1, state.snappedIndex)

        repeat(10) { stateList.add("Restored-$it") }
        harness.stepFrame()
        assertEquals(3, state.currentIndex)
        assertEquals(3, state.snappedIndex)
        assertEquals("Restored-3", stateList[state.snappedIndex])
      } finally {
        harness.dispose()
      }
    }
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

private class WheelCompositionHarness(
  parentContext: CoroutineContext,
  val controller: FakeWheelScrollController,
  val state: WheelPickerState,
) {
  val clock = BroadcastFrameClock()

  var latestOwner: Any? = null
    private set

  private val _recomposer = Recomposer(parentContext + clock)
  private val _scope = CoroutineScope(parentContext + clock)
  private val _recomposeJob = _scope.launch(start = CoroutineStart.UNDISPATCHED) {
    _recomposer.runRecomposeAndApplyChanges()
  }
  private var _composition: Composition? = null
  private var _frameTimeNanos = 0L
  private var _latestCount = 0
  private var _latestKey: ((Int) -> Any)? = null
  private var _measuredOwner: Any? = null
  private var _measuredCount = -1
  private var _measuredKey: ((Int) -> Any)? = null

  fun composeWheel(
    countProvider: () -> Int,
    keyProvider: () -> ((Int) -> Any)?,
  ) {
    setRootContent {
      WheelAttachmentNode(
        count = countProvider,
        key = keyProvider(),
      )
    }
  }

  fun composeLiveListWheel(
    count: () -> Int,
    key: ((Int) -> Any)?,
    onRenderItem: (Int) -> Unit,
  ) {
    setRootContent {
      WheelAttachmentNode(
        count = count,
        key = key,
        onRenderItem = onRenderItem,
      )
    }
  }

  fun composeWheelWithChildParam(
    itemsProvider: () -> List<Int>,
    userScrollEnabledProvider: () -> Boolean = { false },
  ) {
    setRootContent {
      val list = itemsProvider()
      WheelChildNode(
        items = list,
        userScrollEnabled = userScrollEnabledProvider(),
      )
    }
  }

  fun composeBranchSwitchingWheel(
    branchProvider: () -> Boolean,
    onReadCurrentIndex: (Int) -> Unit,
    onSnapped: (Int) -> Unit,
  ) {
    setRootContent {
      val second = branchProvider()
      state.SnappedIndex(onSnapped)
      if (!second) {
        WheelAttachmentNode(count = { 10 }, key = null)
      } else {
        WheelAttachmentNode(count = { 10 }, key = null)
      }
      onReadCurrentIndex(state.currentIndex)
    }
  }

  fun composeToggleableWheel(
    visibleProvider: () -> Boolean,
    countProvider: () -> Int,
    keyProvider: () -> ((Int) -> Any)?,
  ) {
    setRootContent {
      if (visibleProvider()) {
        WheelAttachmentNode(
          count = countProvider,
          key = keyProvider(),
        )
      }
    }
  }

  @Composable
  private fun WheelChildNode(
    items: List<Int>,
    userScrollEnabled: Boolean = false,
  ) {
    WheelAttachmentNode(
      count = { items.size },
      key = { items[it] },
      userScrollEnabled = userScrollEnabled,
    )
  }

  @Composable
  private fun WheelAttachmentNode(
    count: () -> Int,
    key: ((Int) -> Any)?,
    userScrollEnabled: Boolean = false,
    onRenderItem: ((Int) -> Unit)? = null,
  ) {
    val owner = remember(state) { Any() }
    val latestCountState = rememberUpdatedState(count)
    val latestKeyState = rememberUpdatedState(key)
    val latestRenderState = rememberUpdatedState(onRenderItem)
    val resolvedCountState = remember {
      derivedStateOf {
        val value = latestCountState.value()
        require(value >= 0) { "require count >= 0, but was $value" }
        value
      }
    }
    val resolvedCount = resolvedCountState.value
    state.attachLiveCountSync()
    val countProvider: () -> Int = remember(resolvedCountState) {
      { resolvedCountState.value }
    }
    rememberWheelPickerDisplayHolder(
      state = state,
      display = { },
      content = { },
    )
    SideEffect {
      latestOwner = owner
      _latestCount = resolvedCount
      _latestKey = latestKeyState.value
      controller.liveMeasureSpec = {
        Triple(resolvedCountState.value, latestKeyState.value, latestRenderState.value)
      }
      state.updateAttachment(
        owner = owner,
        count = resolvedCount,
        unfocusedCount = 2,
        itemSizePx = 48,
        flingGuardEnabled = userScrollEnabled,
        countProvider = countProvider,
      )
    }
    DisposableEffect(state, owner) {
      onDispose {
        if (latestOwner === owner) {
          controller.liveMeasureSpec = null
        }
        state.detach(owner)
      }
    }

    LaunchedEffect(state, owner) {
      state.observeSnappedIndex(owner)
    }
  }

  private fun setRootContent(content: @Composable () -> Unit) {
    val composition = Composition(EmptyApplier(), _recomposer)
    _composition = composition
    composition.setContent(content)
    Snapshot.sendApplyNotifications()
    _measuredOwner = latestOwner
    _measuredCount = _latestCount
    _measuredKey = _latestKey
    val spec = controller.liveMeasureSpec?.invoke()
    controller.applyMeasure(
      count = spec?.first ?: _latestCount,
      key = spec?.second ?: _latestKey,
      onRenderItem = spec?.third,
    )
  }

  suspend fun stepFrame() {
    Snapshot.sendApplyNotifications()
    repeat(2) {
      yield()
    }
    _frameTimeNanos += 16_000_000L
    clock.sendFrame(_frameTimeNanos)
    applyPendingMeasure()
    repeat(4) {
      yield()
    }
    applyPendingMeasure()
    repeat(4) {
      yield()
    }
  }

  private fun applyPendingMeasure() {
    val spec = controller.liveMeasureSpec?.invoke()
    val targetCount = spec?.first ?: _latestCount
    val targetKey = spec?.second ?: _latestKey
    if (
      _measuredOwner !== latestOwner ||
      _measuredCount != targetCount ||
      controller.totalItemsCount != targetCount ||
      _measuredKey !== targetKey
    ) {
      _measuredOwner = latestOwner
      _measuredCount = targetCount
      _measuredKey = targetKey
      controller.applyMeasure(
        count = targetCount,
        key = targetKey,
        onRenderItem = spec?.third,
      )
      Snapshot.sendApplyNotifications()
    }
  }

  suspend fun advanceUntilIdle(job: Job) {
    var steps = 0
    while (job.isActive && steps < 20) {
      stepFrame()
      steps++
    }
    job.join()
    stepFrame()
  }

  fun dispose() {
    controller.liveMeasureSpec = null
    _composition?.dispose()
    _recomposer.cancel()
    _recomposeJob.cancel()
  }
}

private class FakeWheelScrollController(
  initialIndex: Int,
) : WheelScrollController {
  private val _mutatorMutex = MutatorMutex()
  private val _interactionSource = MutableInteractionSource()
  private var _hadFirstNotEmptyLayout = false
  private var _hasCurrentMountLayout by mutableStateOf(false)
  private var _activeGate: CompletableDeferred<Unit>? = null
  private var _measuredKeys: List<Any?> = emptyList()
  private var _lastKnownFirstItemKey: Any? = null
  private val _measurementScopeInvalidator = mutableStateOf(Unit, neverEqualPolicy())
  private val _placementScopeInvalidator = mutableStateOf(Unit, neverEqualPolicy())
  private var _animationFrameTick by mutableLongStateOf(0L)
  private var _totalItemsCount by mutableIntStateOf(0)

  val animateTargetsRequested = mutableListOf<Int>()
  val completedAnimateTargets = mutableListOf<Int>()

  var liveMeasureSpec: (() -> Triple<Int, ((Int) -> Any)?, ((Int) -> Unit)?>)? = null
  var isPlaced by mutableStateOf(true)
  var pauseAnimations = false
  var emulateRealLazyListAnimateTiming = false
  var supportsNativeWindowTracking = false

  override var firstVisibleItemIndex by mutableIntStateOf(initialIndex)
    private set

  override var firstVisibleItemScrollOffset by mutableIntStateOf(0)
    private set

  override var isScrollInProgress by mutableStateOf(false)
    private set

  override var hasVisibleItems by mutableStateOf(false)
    private set

  var isPositionedReadCount = 0
  var totalItemsCountReadCount = 0

  override val isPositioned: Boolean
    get() {
      isPositionedReadCount++
      return isPlaced && _hasCurrentMountLayout
    }

  override val totalItemsCount: Int
    get() {
      totalItemsCountReadCount++
      return _totalItemsCount
    }

  override val interactionSource: InteractionSource
    get() {
      return _interactionSource
    }

  override fun onPositioned() {
    _hasCurrentMountLayout = true
  }

  override fun resetPositioned() {
    _hasCurrentMountLayout = false
  }

  fun applyMeasure(
    count: Int,
    unfocusedCount: Int = 2,
    itemSizePx: Int = 48,
    key: ((Int) -> Any)? = null,
    onRenderItem: ((Int) -> Unit)? = null,
  ) {
    _hasCurrentMountLayout = true
    val previousFirstIndex = firstVisibleItemIndex
    val previousFirstKey = _lastKnownFirstItemKey
    _totalItemsCount = count
    hasVisibleItems = count > 0
    _measuredKeys = if (count > 0 && key != null) {
      List(count) { index -> key(index) }
    } else {
      emptyList()
    }
    if (
      supportsNativeWindowTracking &&
      count > 0 &&
      key != null &&
      previousFirstKey != null
    ) {
      val minWin = (previousFirstIndex - 100).coerceAtLeast(0)
      val maxWin = (previousFirstIndex + 100).coerceAtMost(count - 1)
      for (candidate in minWin..maxWin) {
        if (_measuredKeys[candidate] == previousFirstKey) {
          firstVisibleItemIndex = candidate
          break
        }
      }
    }
    if (_hadFirstNotEmptyLayout || count > 0) {
      _hadFirstNotEmptyLayout = true
      if (count <= 0) {
        firstVisibleItemIndex = 0
        firstVisibleItemScrollOffset = 0
        _lastKnownFirstItemKey = null
      } else if (firstVisibleItemIndex >= count) {
        firstVisibleItemIndex = count - 1
        firstVisibleItemScrollOffset = 0
        _lastKnownFirstItemKey = _measuredKeys.getOrNull(firstVisibleItemIndex)
      } else {
        _lastKnownFirstItemKey = _measuredKeys.getOrNull(firstVisibleItemIndex)
      }
    }
    if (count > 0 && onRenderItem != null) {
      val lastVisible = (firstVisibleItemIndex + unfocusedCount * 2).coerceAtMost(count - 1)
      for (visibleIndex in firstVisibleItemIndex..lastVisible) {
        onRenderItem(visibleIndex)
      }
    }
    if (unfocusedCount < 0 || itemSizePx < 0) {
      error("Invalid test measure metrics")
    }
    _placementScopeInvalidator.value = Unit
  }

  fun setPositionInScroll(index: Int, offset: Int) {
    firstVisibleItemIndex = index
    firstVisibleItemScrollOffset = offset
    _lastKnownFirstItemKey = _measuredKeys.getOrNull(index)
  }

  fun beginGestureScroll(index: Int, offset: Int) {
    isScrollInProgress = true
    firstVisibleItemIndex = index
    firstVisibleItemScrollOffset = offset
    _lastKnownFirstItemKey = _measuredKeys.getOrNull(index)
  }

  fun endGestureScroll() {
    isScrollInProgress = false
  }

  suspend fun emitDragStart(): DragInteraction.Start {
    val start = DragInteraction.Start()
    _interactionSource.emit(start)
    return start
  }

  suspend fun emitDragStop(start: DragInteraction.Start) {
    _interactionSource.emit(DragInteraction.Stop(start))
  }

  suspend fun emitDragCancel(start: DragInteraction.Start) {
    _interactionSource.emit(DragInteraction.Cancel(start))
  }

  fun resumeActiveAnimation() {
    _activeGate?.complete(Unit)
  }

  override fun requestScrollToItem(index: Int, scrollOffset: Int) {
    firstVisibleItemIndex = index
    firstVisibleItemScrollOffset = scrollOffset
    _lastKnownFirstItemKey = null
    _measurementScopeInvalidator.value = Unit
  }

  override suspend fun scrollToItem(index: Int) {
    _mutatorMutex.mutate {
      isScrollInProgress = true
      try {
        val spec = liveMeasureSpec?.invoke()
        if (spec != null) {
          firstVisibleItemIndex = index.coerceAtLeast(0)
          firstVisibleItemScrollOffset = 0
          _lastKnownFirstItemKey = null
          applyMeasure(
            count = spec.first,
            key = spec.second,
            onRenderItem = spec.third,
          )
        } else if (totalItemsCount > 0) {
          firstVisibleItemIndex = index.coerceIn(0, totalItemsCount - 1)
          firstVisibleItemScrollOffset = 0
          _lastKnownFirstItemKey = _measuredKeys.getOrNull(firstVisibleItemIndex)
        }
      } finally {
        isScrollInProgress = false
      }
    }
  }

  override suspend fun animateScrollToItem(index: Int) {
    animateTargetsRequested.add(index)
    _mutatorMutex.mutate {
      val clamped = if (totalItemsCount > 0) {
        index.coerceIn(0, totalItemsCount - 1)
      } else {
        index
      }
      if (
        emulateRealLazyListAnimateTiming &&
        !pauseAnimations &&
        totalItemsCount > 0 &&
        firstVisibleItemIndex == clamped &&
        firstVisibleItemScrollOffset == 0
      ) {
        completedAnimateTargets.add(clamped)
        return@mutate
      }
      isScrollInProgress = true
      try {
        if (pauseAnimations) {
          val gate = CompletableDeferred<Unit>()
          _activeGate = gate
          gate.await()
        } else if (emulateRealLazyListAnimateTiming) {
          val frameClock = checkNotNull(currentCoroutineContext()[MonotonicFrameClock]) {
            "A MonotonicFrameClock is not available in this CoroutineContext"
          }
          frameClock.withFrameNanos { frameTimeNanos ->
            _animationFrameTick = frameTimeNanos
          }
        } else {
          yield()
        }
        if (totalItemsCount > 0) {
          val finalTarget = index.coerceIn(0, totalItemsCount - 1)
          firstVisibleItemIndex = finalTarget
          firstVisibleItemScrollOffset = 0
          _lastKnownFirstItemKey = _measuredKeys.getOrNull(finalTarget)
          completedAnimateTargets.add(finalTarget)
        }
      } finally {
        isScrollInProgress = false
      }
    }
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
