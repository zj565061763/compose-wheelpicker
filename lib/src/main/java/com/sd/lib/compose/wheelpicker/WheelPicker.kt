package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import java.io.Serializable

/**
 * 垂直滚轮选择器
 *
 * 外层按固有尺寸测量宽度时，需通过[modifier]或[focus]指定宽度。
 *
 * @param modifier 修饰符
 * @param count 条目总数，要求 >= 0
 * @param state 滚轮状态
 * @param key 条目唯一标识，行为与[LazyColumn]一致，用于在常规数据增删或移动时保持当前选中项
 * @param itemHeight 单个条目高度，要求为大于 0.dp 的有限值
 * @param unfocusedCount 中心焦点上下两侧各显示的未选中条目数量，要求 >= 0；空间不足时自动缩小
 * @param userScrollEnabled 是否允许用户手势滚动
 * @param reverseLayout 是否反向排列条目
 * @param focus 中心焦点区域的装饰内容，同时提供滚轮宽度的固有尺寸
 * @param display 条目展示容器，可通过[WheelPickerDisplayScope.state]获取滚动偏移自定义视觉效果
 * @param content 单个条目内容
 */
@Composable
fun VerticalWheelPicker(
  modifier: Modifier = Modifier,
  count: Int,
  state: WheelPickerState = rememberWheelPickerState(),
  key: ((index: Int) -> Any)? = null,
  itemHeight: Dp = 48.dp,
  unfocusedCount: Int = 2,
  userScrollEnabled: Boolean = true,
  reverseLayout: Boolean = false,
  focus: @Composable () -> Unit = { WheelPickerFocusVertical() },
  display: @Composable WheelPickerDisplayScope.(index: Int) -> Unit = { DefaultWheelPickerDisplay(it) },
  content: @Composable (index: Int) -> Unit,
) {
  WheelPicker(
    modifier = modifier,
    orientation = WheelOrientation.Vertical,
    count = count,
    state = state,
    key = key,
    itemSize = itemHeight,
    unfocusedCount = unfocusedCount,
    userScrollEnabled = userScrollEnabled,
    reverseLayout = reverseLayout,
    focus = focus,
    display = display,
    content = content,
  )
}

/**
 * 水平滚轮选择器
 *
 * 外层按固有尺寸测量高度时，需通过[modifier]或[focus]指定高度。
 *
 * @param modifier 修饰符
 * @param count 条目总数，要求 >= 0
 * @param state 滚轮状态
 * @param key 条目唯一标识，行为与[LazyRow]一致，用于在常规数据增删或移动时保持当前选中项
 * @param itemWidth 单个条目宽度，要求为大于 0.dp 的有限值
 * @param unfocusedCount 中心焦点左右两侧各显示的未选中条目数量，要求 >= 0；空间不足时自动缩小
 * @param userScrollEnabled 是否允许用户手势滚动
 * @param reverseLayout 是否反向排列条目
 * @param focus 中心焦点区域的装饰内容，同时提供滚轮高度的固有尺寸
 * @param display 条目展示容器，可通过[WheelPickerDisplayScope.state]获取滚动偏移自定义视觉效果
 * @param content 单个条目内容
 */
@Composable
fun HorizontalWheelPicker(
  modifier: Modifier = Modifier,
  count: Int,
  state: WheelPickerState = rememberWheelPickerState(),
  key: ((index: Int) -> Any)? = null,
  itemWidth: Dp = 48.dp,
  unfocusedCount: Int = 2,
  userScrollEnabled: Boolean = true,
  reverseLayout: Boolean = false,
  focus: @Composable () -> Unit = { WheelPickerFocusHorizontal() },
  display: @Composable WheelPickerDisplayScope.(index: Int) -> Unit = { DefaultWheelPickerDisplay(it) },
  content: @Composable (index: Int) -> Unit,
) {
  WheelPicker(
    modifier = modifier,
    orientation = WheelOrientation.Horizontal,
    count = count,
    state = state,
    key = key,
    itemSize = itemWidth,
    unfocusedCount = unfocusedCount,
    userScrollEnabled = userScrollEnabled,
    reverseLayout = reverseLayout,
    focus = focus,
    display = display,
    content = content,
  )
}

@Composable
private fun WheelPicker(
  modifier: Modifier = Modifier,
  orientation: WheelOrientation,
  count: Int,
  state: WheelPickerState,
  key: ((index: Int) -> Any)?,
  itemSize: Dp,
  unfocusedCount: Int,
  userScrollEnabled: Boolean,
  reverseLayout: Boolean,
  focus: @Composable () -> Unit,
  display: @Composable WheelPickerDisplayScope.(index: Int) -> Unit,
  content: @Composable (index: Int) -> Unit,
) {
  require(count >= 0) { "require count >= 0, but was $count" }
  require(itemSize.value.isFinite() && itemSize > 0.dp) { "require finite itemSize > 0.dp, but was $itemSize" }
  require(unfocusedCount >= 0) { "require unfocusedCount >= 0, but was $unfocusedCount" }

  val owner = remember(state) { Any() }
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val itemSizePx = remember(density, itemSize) {
    resolveWheelItemSizePx(density = density, itemSize = itemSize)
  }
  val viewportState = remember(orientation) {
    WheelViewportState(
      orientation = orientation,
      density = density,
      itemSizePx = itemSizePx,
      initialUnfocusedCount = unfocusedCount,
    )
  }
  val resolvedUnfocusedCount = viewportState.resolveUnfocusedCount(
    itemSizePx = itemSizePx,
    requestedUnfocusedCount = unfocusedCount,
  )
  val reverseVisualDirection = orientation.isVisualDirectionReversed(
    reverseLayout = reverseLayout,
    layoutDirection = layoutDirection,
  )

  val scrollEnabled = userScrollEnabled && (count > 0)
  val flingGuardConnection = remember(state, owner) {
    object : NestedScrollConnection {
      override suspend fun onPreFling(available: Velocity): Velocity {
        state.onFlingHandoffResolved(owner)
        return Velocity.Zero
      }

      override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        state.onFlingHandoffResolved(owner)
        return Velocity.Zero
      }
    }
  }

  SideEffect {
    state.updateAttachment(
      owner = owner,
      count = count,
      unfocusedCount = resolvedUnfocusedCount,
      itemSizePx = itemSizePx,
      reverseVisualDirection = reverseVisualDirection,
      flingGuardEnabled = scrollEnabled,
    )
  }

  DisposableEffect(state, owner) {
    onDispose {
      state.detach(owner)
    }
  }

  LaunchedEffect(state, owner) {
    state.observeSnappedIndex(owner)
  }

  val displayHolder = rememberWheelPickerDisplayHolder(
    state = state,
    display = display,
    content = content,
  )
  val flingBehavior = rememberSnapFlingBehavior(lazyListState = state.lazyListState)
  val safeLazyKey: ((Int) -> Any)? = remember(key) {
    if (key == null) {
      null
    } else {
      { index ->
        try {
          key(index)
        } catch (_: IndexOutOfBoundsException) {
          RemovedWheelItemKey(index)
        }
      }
    }
  }
  val itemModifier = remember(orientation, itemSizePx) {
    Modifier.itemMainAxisSize(orientation = orientation, sizePx = itemSizePx)
  }
  val lazyListContent: LazyListScope.() -> Unit = remember(
    count,
    safeLazyKey,
    itemModifier,
    displayHolder,
  ) {
    {
      if (count > 0) {
        items(
          count = count,
          key = safeLazyKey,
        ) { index ->
          Box(
            modifier = itemModifier,
            contentAlignment = Alignment.Center,
          ) {
            displayHolder.Display(index)
          }
        }
      }
    }
  }

  WheelViewportBox(
    modifier = modifier,
    orientation = orientation,
    itemSizePx = itemSizePx,
    unfocusedCount = unfocusedCount,
    viewportState = viewportState,
    onResolvedUnfocusedCount = { safeUnfocusedCount ->
      state.updateViewportUnfocusedCount(
        owner = owner,
        unfocusedCount = safeUnfocusedCount,
      )
    },
    listContent = {
      WheelLazyList(
        modifier = Modifier.nestedScroll(flingGuardConnection),
        orientation = orientation,
        state = state.lazyListState,
        contentPadding = viewportState.contentPadding,
        reverseLayout = reverseLayout,
        flingBehavior = flingBehavior,
        userScrollEnabled = scrollEnabled,
        content = lazyListContent,
      )
    },
    focusContent = focus,
  )
}

@Composable
private fun WheelLazyList(
  modifier: Modifier = Modifier,
  orientation: WheelOrientation,
  state: LazyListState,
  contentPadding: PaddingValues,
  reverseLayout: Boolean,
  flingBehavior: FlingBehavior,
  userScrollEnabled: Boolean,
  content: LazyListScope.() -> Unit,
) {
  when (orientation) {
    WheelOrientation.Vertical -> {
      LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        horizontalAlignment = Alignment.CenterHorizontally,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = null,
        content = content,
      )
    }

    WheelOrientation.Horizontal -> {
      LazyRow(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalAlignment = Alignment.CenterVertically,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = null,
        content = content,
      )
    }
  }
}

private data class RemovedWheelItemKey(
  val index: Int,
) : Serializable
