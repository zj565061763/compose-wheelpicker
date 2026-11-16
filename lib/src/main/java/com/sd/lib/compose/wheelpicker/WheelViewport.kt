package com.sd.lib.compose.wheelpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private const val MAX_UNBOUNDED_MAIN_AXIS_PX = 32_766

internal enum class WheelOrientation {
  Vertical,
  Horizontal,
  ;

  fun maxMainAxisSize(constraints: Constraints): Int {
    return when (this) {
      Vertical -> constraints.maxHeight
      Horizontal -> constraints.maxWidth
    }
  }

  fun isVisualDirectionReversed(
    reverseLayout: Boolean,
    layoutDirection: LayoutDirection,
  ): Boolean {
    return when (this) {
      Vertical -> reverseLayout
      Horizontal -> reverseLayout xor (layoutDirection == LayoutDirection.Rtl)
    }
  }
}

internal fun Modifier.itemMainAxisSize(
  orientation: WheelOrientation,
  size: Dp,
): Modifier {
  return when (orientation) {
    WheelOrientation.Vertical -> height(size)
    WheelOrientation.Horizontal -> width(size)
  }
}

@Stable
internal class WheelViewportState(
  orientation: WheelOrientation,
  density: Density,
  itemSizePx: Int,
  initialUnfocusedCount: Int,
) {
  private var _lastMaxMainAxisPx = Constraints.Infinity

  private val _paddingValues = WheelPaddingValues(
    orientation = orientation,
    paddingDp = calculatePaddingDp(
      density = density,
      itemSizePx = itemSizePx,
      unfocusedCount = calculateSafeUnfocusedCount(
        maxSizePx = Constraints.Infinity,
        itemSizePx = itemSizePx,
        unfocusedCount = initialUnfocusedCount,
      ),
    ),
  )

  val contentPadding: PaddingValues
    get() {
      return _paddingValues
    }

  var shouldClip by mutableStateOf(false)
    private set

  fun resolveUnfocusedCount(
    itemSizePx: Int,
    requestedUnfocusedCount: Int,
  ): Int {
    return calculateSafeUnfocusedCount(
      maxSizePx = _lastMaxMainAxisPx,
      itemSizePx = itemSizePx,
      unfocusedCount = requestedUnfocusedCount,
    )
  }

  fun onMeasure(
    orientation: WheelOrientation,
    density: Density,
    maxMainAxisPx: Int,
    itemSizePx: Int,
    requestedUnfocusedCount: Int,
  ): Int {
    _lastMaxMainAxisPx = maxMainAxisPx
    val safeUnfocusedCount = calculateSafeUnfocusedCount(
      maxSizePx = maxMainAxisPx,
      itemSizePx = itemSizePx,
      unfocusedCount = requestedUnfocusedCount,
    )
    _paddingValues.update(
      orientation = orientation,
      paddingDp = calculatePaddingDp(
        density = density,
        itemSizePx = itemSizePx,
        unfocusedCount = safeUnfocusedCount,
      ),
    )
    return safeUnfocusedCount
  }

  fun updateClip(clip: Boolean) {
    if (shouldClip != clip) {
      shouldClip = clip
    }
  }
}

@Stable
private class WheelPaddingValues(
  orientation: WheelOrientation,
  paddingDp: Dp,
) : PaddingValues {
  private var _orientation by mutableStateOf(orientation)
  private var _paddingDp by mutableStateOf(paddingDp)

  fun update(
    orientation: WheelOrientation,
    paddingDp: Dp,
  ) {
    if (_orientation != orientation) {
      _orientation = orientation
    }
    if (_paddingDp != paddingDp) {
      _paddingDp = paddingDp
    }
  }

  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
    return if (_orientation == WheelOrientation.Horizontal) _paddingDp else 0.dp
  }

  override fun calculateTopPadding(): Dp {
    return if (_orientation == WheelOrientation.Vertical) _paddingDp else 0.dp
  }

  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
    return if (_orientation == WheelOrientation.Horizontal) _paddingDp else 0.dp
  }

  override fun calculateBottomPadding(): Dp {
    return if (_orientation == WheelOrientation.Vertical) _paddingDp else 0.dp
  }
}

@Composable
internal fun WheelViewportBox(
  modifier: Modifier = Modifier,
  orientation: WheelOrientation,
  itemSizePx: Int,
  unfocusedCount: Int,
  viewportState: WheelViewportState,
  onResolvedUnfocusedCount: (Int) -> Unit,
  listContent: @Composable () -> Unit,
  focusContent: @Composable () -> Unit,
) {
  val density = LocalDensity.current
  val onResolvedState = rememberUpdatedState(onResolvedUnfocusedCount)
  val measurePolicy = remember(orientation, density, itemSizePx, unfocusedCount, viewportState) {
    createWheelViewportMeasurePolicy(
      orientation = orientation,
      itemSizePx = itemSizePx,
      unfocusedCount = unfocusedCount,
      viewportState = viewportState,
      onResolvedUnfocusedCount = { onResolvedState.value(it) },
    )
  }

  Layout(
    modifier = modifier.graphicsLayer {
      clip = viewportState.shouldClip
    },
    content = {
      listContent()
      Box(contentAlignment = Alignment.Center) {
        focusContent()
      }
    },
    measurePolicy = measurePolicy,
  )
}

internal fun createWheelViewportMeasurePolicy(
  orientation: WheelOrientation,
  itemSizePx: Int,
  unfocusedCount: Int,
  viewportState: WheelViewportState,
  onResolvedUnfocusedCount: (Int) -> Unit,
): MeasurePolicy {
  return object : MeasurePolicy {
    override fun MeasureScope.measure(
      measurables: List<Measurable>,
      constraints: Constraints,
    ): MeasureResult {
      val maxMainAxisPx = orientation.maxMainAxisSize(constraints)
      val safeUnfocusedCount = viewportState.onMeasure(
        orientation = orientation,
        density = this,
        maxMainAxisPx = maxMainAxisPx,
        itemSizePx = itemSizePx,
        requestedUnfocusedCount = unfocusedCount,
      )
      onResolvedUnfocusedCount(safeUnfocusedCount)

      val viewportMainAxisPx = itemSizePx * (safeUnfocusedCount * 2 + 1)
      val listConstraints = when (orientation) {
        WheelOrientation.Vertical -> Constraints.fitPrioritizingHeight(
          minWidth = constraints.minWidth,
          maxWidth = constraints.maxWidth,
          minHeight = viewportMainAxisPx,
          maxHeight = viewportMainAxisPx,
        )

        WheelOrientation.Horizontal -> Constraints.fitPrioritizingWidth(
          minWidth = viewportMainAxisPx,
          maxWidth = viewportMainAxisPx,
          minHeight = constraints.minHeight,
          maxHeight = constraints.maxHeight,
        )
      }

      val listPlaceable = measurables[0].measure(listConstraints)
      val layoutWidth: Int
      val layoutHeight: Int
      val focusConstraints: Constraints
      when (orientation) {
        WheelOrientation.Vertical -> {
          layoutWidth = listPlaceable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
          layoutHeight = viewportMainAxisPx.coerceIn(constraints.minHeight, constraints.maxHeight)
          focusConstraints = Constraints.fitPrioritizingHeight(
            minWidth = layoutWidth,
            maxWidth = layoutWidth,
            minHeight = itemSizePx,
            maxHeight = itemSizePx,
          )
        }

        WheelOrientation.Horizontal -> {
          layoutWidth = viewportMainAxisPx.coerceIn(constraints.minWidth, constraints.maxWidth)
          layoutHeight = listPlaceable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
          focusConstraints = Constraints.fitPrioritizingWidth(
            minWidth = itemSizePx,
            maxWidth = itemSizePx,
            minHeight = layoutHeight,
            maxHeight = layoutHeight,
          )
        }
      }

      val focusPlaceable = measurables[1].measure(focusConstraints)
      viewportState.updateClip(
        clip = layoutWidth < listPlaceable.width ||
          layoutHeight < listPlaceable.height ||
          layoutWidth < focusPlaceable.width ||
          layoutHeight < focusPlaceable.height,
      )

      val listX = (layoutWidth - listPlaceable.width) / 2
      val listY = (layoutHeight - listPlaceable.height) / 2
      val focusX = (layoutWidth - focusPlaceable.width) / 2
      val focusY = (layoutHeight - focusPlaceable.height) / 2

      return layout(layoutWidth, layoutHeight) {
        listPlaceable.placeRelative(x = listX, y = listY)
        focusPlaceable.placeRelative(x = focusX, y = focusY)
      }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
      measurables: List<IntrinsicMeasurable>,
      height: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> measurables.getOrNull(1)?.minIntrinsicWidth(itemSizePx) ?: 0
        WheelOrientation.Horizontal -> calculateIntrinsicMainAxisPx(itemSizePx = itemSizePx, unfocusedCount = unfocusedCount)
      }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
      measurables: List<IntrinsicMeasurable>,
      height: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> measurables.getOrNull(1)?.maxIntrinsicWidth(itemSizePx) ?: 0
        WheelOrientation.Horizontal -> calculateIntrinsicMainAxisPx(itemSizePx = itemSizePx, unfocusedCount = unfocusedCount)
      }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
      measurables: List<IntrinsicMeasurable>,
      width: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> calculateIntrinsicMainAxisPx(itemSizePx = itemSizePx, unfocusedCount = unfocusedCount)
        WheelOrientation.Horizontal -> measurables.getOrNull(1)?.minIntrinsicHeight(itemSizePx) ?: 0
      }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
      measurables: List<IntrinsicMeasurable>,
      width: Int,
    ): Int {
      return when (orientation) {
        WheelOrientation.Vertical -> calculateIntrinsicMainAxisPx(itemSizePx = itemSizePx, unfocusedCount = unfocusedCount)
        WheelOrientation.Horizontal -> measurables.getOrNull(1)?.maxIntrinsicHeight(itemSizePx) ?: 0
      }
    }
  }
}

internal fun calculateSafeUnfocusedCount(
  maxSizePx: Int,
  itemSizePx: Int,
  unfocusedCount: Int,
): Int {
  if (itemSizePx <= 0 || unfocusedCount <= 0) {
    return 0
  }
  val effectiveMaxPx = if (maxSizePx == Constraints.Infinity) {
    MAX_UNBOUNDED_MAIN_AXIS_PX.coerceAtLeast(itemSizePx)
  } else {
    maxSizePx
  }
  if (effectiveMaxPx <= itemSizePx) {
    return 0
  }
  val maxBySpace = (effectiveMaxPx - itemSizePx) / 2 / itemSizePx
  return minOf(unfocusedCount, maxBySpace)
}

internal fun calculateSnappedIndex(
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  count: Int,
  itemSizePx: Int,
): Int {
  if (count <= 0) {
    return -1
  }
  val rounded = if (firstVisibleItemScrollOffset > itemSizePx / 2) {
    firstVisibleItemIndex + 1
  } else {
    firstVisibleItemIndex
  }
  return rounded.coerceIn(0, count - 1)
}

internal fun calculateScrollPercentage(
  index: Int,
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  unfocusedCount: Int,
  itemSizePx: Int,
  reverseDirection: Boolean = false,
): Float? {
  if (itemSizePx <= 0 || unfocusedCount < 0) {
    return null
  }
  val total = (unfocusedCount.toLong() + 1L) * itemSizePx.toLong()
  if (total <= 0L) {
    return null
  }

  val minDiffIndex = -(unfocusedCount.toLong() + 1L)
  val maxDiffIndex = unfocusedCount.toLong() + 2L
  val diffIndex = (index.toLong() - firstVisibleItemIndex.toLong()).coerceIn(minDiffIndex, maxDiffIndex)
  val diff = diffIndex * itemSizePx.toLong() - firstVisibleItemScrollOffset.toLong()
  val percentage = (diff.toFloat() / total.toFloat()).coerceIn(-1f, 1f)
  if (percentage == 0f) {
    return 0f
  }
  return if (reverseDirection) -percentage else percentage
}

private fun calculateIntrinsicMainAxisPx(
  itemSizePx: Int,
  unfocusedCount: Int,
): Int {
  val safeCount = calculateSafeUnfocusedCount(
    maxSizePx = Constraints.Infinity,
    itemSizePx = itemSizePx,
    unfocusedCount = unfocusedCount,
  )
  return itemSizePx * (safeCount * 2 + 1)
}

private fun calculatePaddingDp(
  density: Density,
  itemSizePx: Int,
  unfocusedCount: Int,
): Dp {
  return with(density) { (itemSizePx * unfocusedCount).toDp() }
}
