# AGENTS.md

## 项目概览

- `compose-wheelpicker` 是基于 Jetpack Compose 的垂直与水平滚轮选择器库，Maven 坐标为 `io.github.zj565061763.android:compose-wheelpicker`。
- 仓库包含发布库 `:lib`（`com.sd.lib.compose.wheelpicker`）与演示应用 `:app`（`com.sd.demo.compose.wheelpicker`）两个模块。
- `:lib` 仅依赖 `androidx.compose.foundation`，不引入 `material3` 等上层设计系统依赖。

| 项目 | 配置 / 路径 | 说明 |
|---|---|---|
| 构建工具链 | Gradle `8.11.1`、AGP `8.7.3`、Kotlin `1.9.25` | Java / Kotlin 编译目标均为 `1.8`，Compose BOM 为 `2025.07.00`（Compiler `1.5.15`） |
| SDK 版本 | `compileSdk = 35`、`minSdk = 21`、`targetSdk = 35`（`:app`） | 统一由 `gradle/libs.versions.toml` 与模块脚本管理 |
| `:lib` 源码 | `lib/src/main/java/com/sd/lib/compose/wheelpicker/` | 全部可复用公开 API 与内部实现 |
| `:lib` 发布 | `lib/build.gradle.kts`、`lib/gradle.properties` | `com.vanniktech.maven.publish` 单变体 `release` 发布（含源码与 Javadoc JAR） |
| `:app` 示例 | `app/src/main/java/com/sd/demo/compose/wheelpicker/` | 演示默认滚轮、自定义展示与焦点、日期联动、反向排列与跳转 |

## 常用命令

所有命令在仓库根目录执行：

```bash
# 构建发布 AAR 与示例 Debug APK
./gradlew :lib:assembleRelease :app:assembleDebug

# 运行库模块全部本地 JVM 单元测试
./gradlew :lib:testDebugUnitTest

# 运行单个 JVM 单元测试方法
./gradlew :lib:testDebugUnitTest \
  --tests "com.sd.lib.compose.wheelpicker.WheelPickerTest.testNativeKeyTrackingSyncsSnappedIndexOnInsertDeleteAndReorder"

# 在指定模拟器上运行库模块仪器测试（同时连接真机时必须指定 ANDROID_SERIAL）
ANDROID_SERIAL=emulator-5554 ./gradlew :lib:connectedDebugAndroidTest

# 运行单个仪器测试方法
ANDROID_SERIAL=emulator-5554 ./gradlew :lib:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="com.sd.lib.compose.wheelpicker.WheelPickerInstrumentedTest#testModalBottomSheetAndMultiFrameOnPreFlingNestedScrollSafety"

# 执行 Android Lint 静态检查
./gradlew lint
```

## 核心架构与行为不变量

| 文件 | 职责 |
|---|---|
| `WheelPicker.kt` | `VerticalWheelPicker` / `HorizontalWheelPicker` 入口、参数校验、`NestedScrollConnection` 离手衔接及 `LazyColumn` / `LazyRow` 组装 |
| `WheelViewport.kt` | 主轴视口尺寸计算、父容器受限时 `unfocusedCount` 自动缩小、固有尺寸测量、裁剪判定与偏移比例计算 |
| `WheelPickerState.kt` | 公开状态门面、`rememberWheelPickerState`、`SnappedIndex` 及 `Saver` 保存恢复 |
| `WheelPositionStore.kt` | 挂载/卸载会话、手势与离手标记、中心与吸附索引同步计算及缩容边界夹紧 |
| `WheelScrollExecutor.kt` | 按索引编程式滚动、并发抢占取消、异常兜底复位及自动吸附动作执行 |
| `WheelSnapObserver.kt` | `DragInteraction` 跟踪、离手 fling 保护窗口与静止非零偏移自动吸附观察 |
| `WheelPickerDisplay.kt` | `WheelPickerDisplayScope` 与默认缩放透明度展示效果 `DefaultWheelPickerDisplay` |
| `WheelPickerDefault.kt` | 默认中心焦点上下/左右分割线指示器 `WheelPickerFocusVertical` / `WheelPickerFocusHorizontal` |
| `WheelScrollController.kt` | `LazyListState` 滚动控制抽象，供本地 JVM 测试替身接入 |

修改 `:lib` 时必须保持以下不变量：

- 参数校验：`count >= 0`、`itemSize` 为大于 `0.dp` 的有限值、`unfocusedCount >= 0`，非法参数立即抛 `IllegalArgumentException`。
- 主轴自适应与裁剪：`itemSizePx` 限制在 `1..262_142`（Compose `Constraints` 单轴上限），主轴尺寸为 `itemSizePx * (safeUnfocusedCount * 2 + 1)`，父容器空间不足时自动缩小 `safeUnfocusedCount`，仍小于子项时启用裁剪。
- 交叉轴固有尺寸：只查询 `focus` 槽位，严禁查询底层 `LazyList` 节点（会触发 Compose 异常）。
- 单实例与空态语义：同一个 `WheelPickerState` 同时只能绑定一个滚轮，未挂载或 `count == 0` 时 `currentIndex` 与 `snappedIndex` 均为 `-1`、`scrollPercentageOf` 返回 `null`。
- 视觉方向符号：RTL 布局下的水平滚轮与 `reverseLayout = true` 需保持 `scrollPercentageOf` 视觉方向符号一致。
- 离手衔接保护：禁用默认 `overscrollEffect`，松手或触摸取消时通过 `isAwaitingFlingHandoff` 配合 `NestedScrollConnection`（含 60 帧超时兜底）等待嵌套滚动父容器完成 `onPreFling`，不得提前启动自动吸附截停 fling。
- 静止自动吸附：停止在非零像素偏移时自动归位至最近条目并更新 `snappedIndex`。
- 预跳转与无死锁：未挂载、空数据或首次布局前调用 `scrollToIndex` / `animateScrollToIndex` 不得挂起死锁，待后续非空挂载时限制在 `0..<count` 内生效。
- 缩容边界收敛：列表缩容导致 `snappedIndex >= count` 时立即夹紧至 `count - 1`，原中间项变为新末项且残留正偏移时将偏移归零。

## 设计取舍与代码审查约定

状态管理只对齐官方 `LazyColumn` / `LazyListState` 的常规行为边界，以下取舍均属有意设计，代码审查不得作为缺陷或功能缺失报告：

- `key` 仅做底层透传：`VerticalWheelPicker` 与 `HorizontalWheelPicker` 的 `key` 参数仅透传给底层 `LazyList`（附带 `IndexOutOfBoundsException` 瞬时越界兜底），由 `LazyListState` 原生最近邻窗口（约 ±100 项）保持可见锚点，再由 `WheelSnapObserver` 在静止时同步 `snappedIndex`。
- 不提供按 `key` 跳转 API：不提供 `scrollToKey` / `animateScrollToKey`，调用方需自行在数据源查找索引后调用 `scrollToIndex` / `animateScrollToIndex`。
- 不做跨窗口重排追踪：不在状态层维护独立 `key` 缓存、全表扫描或快照探测（如 `keyProbeIndex`），单次重排跨度超出 `LazyList` 原生最近邻窗口（约 ±100 项）时按原索引位置回退。
- 不支持逐项独立 `MutableState` 暗中突变：不支持在稳定 `key` 闭包内用每个条目独立的 `MutableState` 原地改值却不触发列表重组，数据变更需通过不可变列表重组或 `SnapshotStateList` 结构修改驱动。
- 不跨空列表或重挂载记忆旧 `key`：列表变空（`count = 0`）或组件卸载重挂载后统一按最近已知索引恢复并限制在新 `0..<count` 范围内，重新挂载时重置底层残留锚点。
- 不在动画飞行途中按 `key` 改道：`animateScrollToIndex` 执行期间若发生数据增删重排，不中途取消并追踪旧 `key`，始终滚向调用时锁定的目标索引。
- 不替外部无帧时钟协程代借帧时钟：`animateScrollToIndex` 与官方 `LazyListState.animateScrollToItem` 一致，需在含 `MonotonicFrameClock` 的 Compose 协程作用域（如 `LaunchedEffect`、`rememberCoroutineScope`）中调用，不为缺少帧时钟的外部协程跨协程借用或等待帧时钟。
- 不做同帧抢跑越界等待：修改数据状态后未等重组就在同一帧调用越界 `scrollToIndex` 时，按调用当下已知的 `count` 直接限制边界，不做跨帧挂起等待；动态扩容后的跳转应在数据生效后的 `LaunchedEffect` 中调用。
- 不做半格偏移删首项补偿：手势停留在非零像素偏移期间并发删除当前首项时，交由 `LazyList` 原生布局与松手后的自动吸附处理。

## 测试与仓库规范

- 纯算法、状态生命周期、并发抢占与自动吸附优先放入本地 JVM 测试 `WheelPickerTest`（使用轻量 Fake，不引入 Mockito）；涉及真实 Compose 测量、嵌套滚动手势、触摸打断与 RTL 布局的行为放入 `WheelPickerInstrumentedTest`。
- 未经用户明确要求，不主动修改 `README.md`。
- `app/template.jks` 仅用于示例签名，不要提交 `local.properties`、生产密钥或 `build/` 产物。

| 测试类 | 覆盖范围 |
|---|---|
| `WheelPickerTest` | `unfocusedCount` 防溢出收缩、`snappedIndex` 与视觉偏移比例计算、挂载/卸载/空列表过渡与 `Saver`、未挂载预跳转、并发抢占与异常后状态自愈、重复取消后自动吸附自愈、`onPreFling` 挂起保护、缩容夹紧与末项残余偏移归零、常规 `key` 增删重排同步 |
| `WheelPickerInstrumentedTest` | 垂直/水平固有尺寸与紧约束裁剪、`ModalBottomSheet` 多帧 `onPreFling` 嵌套滚动与触摸取消安全、手势中途打断动画及后续头插保持、RTL 水平滚轮视觉偏移与焦点指示器、手势非零偏移缩容夹紧与重挂载跳转 |
