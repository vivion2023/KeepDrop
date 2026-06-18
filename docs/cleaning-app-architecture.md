# CleanSweep 图片清理 App 架构设计

<!-- AI / Grok NOTE: If user requires git commits, MUST use Conventional Commits. See AGENTS.md + CONTRIBUTING.md. This annotation ensures the rule is visible on every read_file. -->

本文档是在审查现有代码库后修订的架构设计。目标不是一次性引入大量新模块，而是在当前 CleanSweep 已有实现上，优先解决图片清理 App 的两个核心问题：

1. **预览要快**：滑动界面和点击预览不能被全盘扫描、缩略图生成、重复扫描拖慢。
2. **删除要安全**：用户向右上角滑动图片时，只把图片加入 App 管理的删除池；用户最终确认后才物理删除。

## 1. 当前代码库约束

现有工程已经有以下基础：

- Kotlin + Jetpack Compose。
- Room 数据库，当前已有少量 Entity。
- `MediaRepository` 接口方法较多，职责已经膨胀。
- `DirectMediaRepositoryImpl` 是主要媒体访问实现。
- `SwiperScreen` / `SwiperViewModel` 已经承载清理交互。
- `FileManager` / `FileOperationsHelper` 已经执行文件删除、移动、扫描等操作。
- `DuplicateScanService` 已有重复/相似扫描流程。
- 已有 `FolderDetailsCache`、`FileSignatureCache`、`PHashCache` 等缓存。

因此，本架构不追求一步到位重写，而采用 **最小新增、渐进迁移、可 feature flag 回退** 的方案。

## 2. 设计边界

### 2.1 本阶段必须解决

- 向右上角滑动图片时，将图片加入删除池。
- 删除池持久化，App 重启后仍存在。
- 删除池支持查看、预览、恢复、最终删除。
- 最终删除走统一物理删除入口，处理 File、MediaStore、SAF 的权限差异。
- 滑动界面和点击预览使用屏幕尺寸预览图，不默认解码原图。
- 减少启动、文件夹选择、Swiper 首屏的全量扫描和全量排序。

### 2.2 本阶段暂不做

- 不引入完整复杂的 `MediaIndex` 大表作为第一阶段必需项。
- 不新增通用 `UndoRedoManager`。
- 不持久化 KEEP、SKIP 等无业务价值的动作。
- 不把 Android/media、SAF、普通文件夹拆成多个独立 Scanner 模块。
- 不在加入删除池时更新多个聚合表。
- 不把重复/相似扫描重构为全新流水线。

### 2.3 编译验证说明

Android/Kotlin 改动在提交前应通过 Gradle 编译验证（至少 `./gradlew :app:compileDebugKotlin`，或完整 assemble）。AI Agent 与开发者均应在可用环境中执行该验证后再提交。

若命令行 JDK 与 Android Studio 不一致导致构建失败，应先对齐 JDK/环境（例如使用 Android Studio 自带的 JBR 或项目 `gradle.properties` 中配置的 toolchain），不得以环境差异跳过编译验证。

Room schema 生成、安装运行和手势行为等更完整的验证，仍在 Android Studio 或真机/模拟器上由开发者完成。

## 3. 总体架构

精简后的架构如下：

```text
UI
  ├─ SessionSetupScreen
  ├─ CleanupSwiperScreen
  ├─ MediaPreviewScreen
  ├─ DeletePoolScreen
  └─ FinalDeleteDialog / FinalDeleteProgress

Application / Domain
  ├─ DeletePoolManager
  ├─ FinalDeleteUseCase
  ├─ PreviewPrefetcher
  └─ MediaAccessFacade

Data
  ├─ DeletePoolRepository
  ├─ DeletePoolDao
  ├─ Existing MediaRepository
  ├─ Existing DirectMediaRepositoryImpl
  └─ Existing FileOperationsHelper / FileManager

Storage Access
  ├─ MediaStoreAccess
  ├─ FilePathAccess
  └─ SafAccess
```

关键原则：

- **删除池先落地**，不等待完整索引层。
- **物理删除入口收敛**，但不立刻重写所有文件操作。
- **预览优化先做低风险改造**：统一 ImageLoader、指定 size、限制预加载数量。
- **扫描优化先限流和复用现有缓存**，再考虑索引表。

## 4. 用户核心流程

### 4.1 进入滑动清理页

```text
用户选择文件夹
  -> 继续复用现有 getMediaFromBuckets() 加载媒体
  -> 短期优化为首批尽快返回，避免等待全部扫描/排序
  -> CleanupSwiperScreen 显示当前图片
  -> PreviewPrefetcher 预加载后续 3 到 5 张
```

短期内不要求 `SwiperScreen` 立刻切换到全新 `media_index`。先在现有 repository 上做性能改造：

- MediaStore 查询结果优先返回。
- File fallback 后台补齐。
- 避免首屏前对所有文件 `File.lastModified()` 排序。
- 避免为了尺寸探测解码原图。

### 4.2 向右上角滑动删除

删除手势定义：

```text
用户将图片向右上角滑动
  -> 加入删除池
  -> 当前图片从清理队列移除
  -> 文件不移动、不删除
```

流程：

```text
CleanupSwiperViewModel.onSwipeToDeletePool(mediaItem)
  -> 立即更新 UI 队列，进入下一张
  -> DeletePoolManager.add(mediaItem)
  -> DeletePoolRepository 插入 delete_pool_entry
  -> 如果 DB 写入失败，回滚 UI 或提示用户
```

性能目标：

- UI 响应目标：小于 50ms，采用乐观 UI 更新。
- DB 落库目标：小于 100ms。
- 单次入池只做一次轻量 DB transaction，不更新多个聚合表。

### 4.3 点击图片预览

```text
用户点击滑动界面中的图片
  -> 打开 MediaPreviewScreen
  -> 复用当前 preview 图
  -> 支持缩放、详情、视频播放
  -> 只有用户明显放大或查看细节时才加载原图
```

预览页不触发全盘扫描，不触发重复扫描，不进行批量缩略图预热。

### 4.4 删除池管理

```text
DeletePoolScreen
  -> 分页读取 delete_pool_entry where status = IN_POOL
  -> 显示缩略图网格
  -> 支持点击预览
  -> 支持恢复选中/恢复全部
  -> 支持最终删除选中/最终删除全部
```

恢复图片时：

```text
DeletePoolManager.restore(entryIds)
  -> 删除对应 IN_POOL entry，或标记为 archived 后隐藏
  -> 清理队列查询自动重新包含该媒体
```

优先采用 **删除 entry** 的方式，避免 `RESTORED` 成为长期业务状态。

### 4.5 最终删除

```text
用户点击最终删除
  -> 展示确认弹窗
  -> FinalDeleteUseCase 锁定当前待删除 entries
  -> entry 状态从 IN_POOL 改为 DELETING
  -> 按来源调用物理删除
  -> 更新为 DELETED / FAILED / NEEDS_PERMISSION
```

最终删除支持部分成功，不因为单个文件失败中断整个批次。

## 5. 删除池数据模型

### 5.1 状态精简

`delete_pool_entry` 只保留 5 种业务状态：

```text
IN_POOL
DELETING
DELETED
FAILED
NEEDS_PERMISSION
```

说明：

- 恢复：删除 entry 或归档隐藏，不使用长期 `RESTORED` 状态。
- 文件已不存在：记录为 `DELETED`，同时 `result_code = ALREADY_GONE`。
- 文件发生变化：记录为 `FAILED`，同时 `result_code = FILE_CHANGED`，由用户决定是否重试。
- 权限不足：记录为 `NEEDS_PERMISSION`，等待系统确认或重新授权。

### 5.2 第一阶段表结构

第一阶段尚未有完整 `media_index`，删除池需要保存足够的删除定位信息，但字段必须克制。

```text
delete_pool_entry
```

字段建议：

```text
entry_id TEXT PRIMARY KEY
media_key TEXT NOT NULL

locator_type TEXT NOT NULL       -- FILE_PATH, MEDIASTORE_URI, SAF_URI
uri TEXT                         -- content/file/document uri
file_path TEXT                   -- file path fallback, nullable

media_type TEXT NOT NULL         -- IMAGE, VIDEO
size_snapshot INTEGER NOT NULL
modified_snapshot INTEGER NOT NULL

status TEXT NOT NULL
result_code TEXT
error_message TEXT

added_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
delete_started_at INTEGER
delete_finished_at INTEGER
```

索引：

```text
unique(media_key, status) for active IN_POOL entries
index(status, added_at)
index(media_key)
```

不在 entry 中保存：

- `display_name`
- `mime_type`
- `width`
- `height`
- `folder_path`
- `thumbnail_cache_key`

这些字段优先从现有 `MediaItem`、文件名、URI、MediaStore 或后续轻量索引中读取。删除池第一阶段以“能删除、能预览、能恢复”为目标，不做重字段快照。

### 5.3 可选删除批次表

如果最终删除需要展示批次历史，可增加轻量批次表：

```text
delete_batch
```

字段：

```text
batch_id TEXT PRIMARY KEY
created_at INTEGER NOT NULL
started_at INTEGER
finished_at INTEGER
status TEXT NOT NULL             -- RUNNING, COMPLETED, PARTIAL_FAILED
total_count INTEGER NOT NULL
success_count INTEGER NOT NULL
failed_count INTEGER NOT NULL
permission_count INTEGER NOT NULL
```

第一阶段可以不做独立 `delete_operation_journal` 表，直接在 `delete_pool_entry` 上记录删除结果。只有当需要崩溃后恢复更复杂的批量任务时，再引入 journal。

## 6. media_index 策略

### 6.1 不作为第一阶段硬依赖

完整 `media_index` 大表不应在删除池之前引入。原因：

- 当前 `DirectMediaRepositoryImpl` 仍是主要媒体来源。
- 现有 `FolderDetailsCache`、hash cache、scan result cache 已经存在。
- 一次性把所有 UI 切到 `media_index` 风险高。

第一阶段删除池可以直接基于当前 `MediaItem` 入池。

### 6.2 后续轻量 media_index

如果后续需要索引层，建议使用轻量表，而不是 35+ 字段大表：

```text
media_index
```

字段建议：

```text
media_key TEXT PRIMARY KEY
source_type TEXT NOT NULL        -- MEDIASTORE, FILE, SAF
uri TEXT NOT NULL
file_path TEXT
display_name TEXT
media_type TEXT NOT NULL
mime_type TEXT
size INTEGER NOT NULL
date_modified INTEGER NOT NULL
folder_key TEXT
last_seen_at INTEGER NOT NULL
is_deleted INTEGER NOT NULL DEFAULT 0
```

不放入 media_index 的内容：

- `pending_delete`：通过 `delete_pool_entry` JOIN 或 subquery 判断。
- `exact_hash_state` / `phash_state`：继续使用现有 hash cache 表。
- `thumbnail_state`：交给 Coil/MediaStore 缩略图缓存，不进入核心媒体行。
- 多个 bucket/folder 冗余字段：只保留 `folder_key`，映射关系由 folder 表或查询层处理。

### 6.3 删除池与 media_index 的关系

后续有 `media_index` 后，删除池 entry 可进一步精简为：

```text
entry_id
media_key
size_snapshot
modified_snapshot
status
result_code
timestamps
```

删除池列表通过 JOIN 获取展示信息。

## 7. MediaRepository 拆分策略

现有 `MediaRepository` 方法过多，不应继续在旁边叠加大量 repository。拆分应按兼容优先原则进行。

### 7.1 保留旧接口，新增小接口

短期保留：

```text
MediaRepository
DirectMediaRepositoryImpl
```

新增小接口：

```kotlin
interface DeletePoolRepository
interface PhysicalDeleteExecutor
interface PreviewDataSource
```

其中：

- `DeletePoolRepository` 只负责删除池 DB。
- `PhysicalDeleteExecutor` 包装现有 `deleteMedia()`、`FileManager`、`ContentResolver.delete()`。
- `PreviewDataSource` 只负责把 `MediaItem` 或 delete entry 转成可加载的 URI/model。

### 7.2 长期拆分方向

当删除池稳定后，再逐步拆分 `MediaRepository`：

```text
MediaQueryRepository      -- 查媒体列表
FolderRepository          -- 查文件夹
MediaMutationRepository   -- 移动、重命名、物理删除
MediaScanRepository       -- 扫描、索引、MediaStore 同步
```

拆分时旧接口保留为 facade，内部委托到新接口，避免一次性修改所有调用点。

## 8. 扫描架构精简

### 8.1 只保留两个扫描入口

不拆 4 个独立 Scanner，先保留两个概念入口：

```text
MediaStoreScanner
TreeScanner
```

`TreeScanner` 统一处理：

- 普通文件路径目录。
- `/storage/emulated/0/Android/media`。
- SAF tree，通过 DocumentFile backend 适配。

Android/media 不需要单独模块，只是 TreeScanner 的一个 root 类型：

```text
root_type = ANDROID_MEDIA
```

### 8.2 去重与合并策略

同一文件可能被 MediaStore 和 TreeScanner 同时发现。合并规则必须明确：

```text
1. 如果 MediaStore 和 File path 指向同一路径，合并为一条媒体记录。
2. MediaStore 元数据优先用于 display、width、height、mimeType。
3. File path 元数据只作为存在性、可写性和未索引文件 fallback。
4. 无法确认相同的 content URI 和 SAF document，不强行合并。
```

media key 生成策略：

```text
MediaStore: ms:{volume}:{id}
File path : file:{normalizedPath}
SAF      : saf:{treeId}:{documentId}
```

如果后续发现 `file:{path}` 与 `ms:{volume}:{id}` 是同一文件，可以建立 alias 表，而不是修改主键。

### 8.3 调度不要过度复杂

不引入 7 个优先级。先用 3 个优先级：

```text
HIGH   当前屏幕需要：当前图片、下一批媒体、用户打开的目录
NORMAL 用户主动刷新、当前文件夹补齐
LOW    全局扫描、缩略图预热、hash 计算
```

当用户在滑动清理页活跃时：

- LOW 任务暂停或降并发。
- HIGH 任务不排队等待全局扫描。
- 重复/相似扫描不与预览争抢线程。

## 9. 预览性能设计

### 9.1 三级加载

```text
Thumbnail: 删除池网格、小图
Preview  : 滑动界面、点击预览初始图，按屏幕尺寸加载
Full     : 用户放大查看细节时才加载
```

### 9.2 具体要求

- 全项目统一同一个 Coil `ImageLoader`。
- 预加载必须指定 size。
- 不用 `Size.ORIGINAL` 获取图片宽高。
- 图片宽高优先用 MediaStore 字段，缺失时用 `inJustDecodeBounds`。
- Swiper 只预加载后续 3 到 5 张。
- 用户快速滑动时取消远离当前位置的加载任务。
- 视频只预加载封面帧，点击播放时再初始化播放器。

### 9.3 与后台任务隔离

预览优先级高于：

- 全盘扫描。
- 缩略图全局预热。
- exact hash。
- pHash。
- 视频相似度抽帧。

## 10. 最终删除执行

### 10.1 PhysicalDeleteExecutor

新增一个小而明确的接口：

```kotlin
interface PhysicalDeleteExecutor {
    suspend fun delete(entry: DeletePoolEntry): PhysicalDeleteResult
}
```

内部按 locator 类型分发：

```text
FILE_PATH      -> File.delete()
MEDIASTORE_URI -> ContentResolver.delete() 或 MediaStore.createDeleteRequest()
SAF_URI        -> DocumentFile.delete()
```

### 10.2 删除前校验

最终删除前只校验必要字段：

```text
size_snapshot
modified_snapshot
```

策略：

- 文件不存在：视为删除完成，`status = DELETED`，`result_code = ALREADY_GONE`。
- size 或 modified 改变：`status = FAILED`，`result_code = FILE_CHANGED`，提示用户重试或重新加入删除池。
- 权限不足：`status = NEEDS_PERMISSION`。

### 10.3 批量删除

批量删除流程：

```text
FinalDeleteUseCase.start(entryIds)
  -> transaction: IN_POOL -> DELETING
  -> 分批执行 PhysicalDeleteExecutor.delete()
  -> 成功项更新 DELETED
  -> 权限项更新 NEEDS_PERMISSION
  -> 失败项更新 FAILED
  -> 批量触发 MediaScanner，而不是每删一张触发
```

如果需要 Android 11+ 系统确认：

```text
收集需要确认的 MediaStore URI
  -> createDeleteRequest()
  -> 用户确认后再更新状态
```

## 11. 权限与降级路径

### 11.1 权限模式

```text
FULL_FILE_ACCESS       -- MANAGE_EXTERNAL_STORAGE，可扫/删普通路径
MEDIA_READ_ONLY        -- READ_MEDIA_IMAGES/VIDEO，只能读 MediaStore，删除需系统确认
PARTIAL_MEDIA_ACCESS   -- Android 14 部分照片访问，只显示授权媒体
SAF_TREE_ACCESS        -- 用户授权的目录，可在该 tree 内读写
NO_ACCESS              -- 只能展示权限说明
```

### 11.2 功能矩阵

| 权限模式             | 文件夹选择            | 预览   | 加入删除池 | 最终删除                   |
| -------------------- | --------------------- | ------ | ---------- | -------------------------- |
| FULL_FILE_ACCESS     | 全量路径 + MediaStore | 支持   | 支持       | File/MediaStore 直接或确认 |
| MEDIA_READ_ONLY      | MediaStore 相册       | 支持   | 支持       | MediaStore delete request  |
| PARTIAL_MEDIA_ACCESS | 仅授权媒体集合        | 支持   | 支持       | delete request，可能受限   |
| SAF_TREE_ACCESS      | 授权目录              | 支持   | 支持       | DocumentFile.delete        |
| NO_ACCESS            | 不展示媒体            | 不支持 | 不支持     | 不支持                     |

### 11.3 UI 降级

- 用户拒绝 All Files Access，但授予媒体权限：进入“有限媒体管理模式”。
- Android 14 只授权部分照片：文件夹选择页改为“已授权照片集合”，不承诺全盘扫描。
- 只有 SAF 授权：只展示用户授权目录。
- 删除需要系统确认时，最终删除页提示“有 N 个项目需要系统确认”。

## 12. 错误传播与用户通知

### 12.1 删除池错误

删除池和最终删除错误必须可见：

- 单个入池失败：Snackbar，当前图片恢复到队列。
- 最终删除部分失败：结果页列出失败数量和原因。
- 需要权限：显示“继续授权删除”按钮。
- 文件已变化：显示“文件已变化，是否重新确认删除”。

### 12.2 后台任务错误

后台扫描、校验、预热失败不应频繁打扰用户。

策略：

- 当前屏幕相关失败：UI 内显示轻提示。
- 大量文件不一致：通知或对话框提示用户刷新。
- 低优先级预热失败：静默记录，下次重试。
- 重复扫描失败：在重复扫描页面显示失败状态。

### 12.3 列表更新策略

- 用户正在看某张图时，不因后台扫描直接跳走。
- 当前图被外部删除：保留当前页面并显示“文件已不存在”，用户确认后跳下一张。
- 列表中非当前项变化：静默从 Paging/列表中移除或更新。
- 删除池 reconciliation 发现条目已不存在：标为已删除并在删除池页面提示。

## 13. 数据库迁移策略

当前数据库已有 version 3。迁移必须渐进，不使用 destructive migration，除非仅限 debug 构建。

### 13.1 v3 -> v4：删除池 MVP

新增：

```text
delete_pool_entry
```

可选新增：

```text
delete_batch
```

不修改已有表，不迁移已有缓存。

### 13.2 v4 -> v5：物理删除结果增强

如第一阶段没有 `delete_batch`，此版本加入。

可选增加字段：

```text
delete_pool_entry.delete_started_at
delete_pool_entry.delete_finished_at
delete_pool_entry.result_code
```

### 13.3 v5 -> v6：轻量 media_index（可选）

新增：

```text
media_index
```

不从旧缓存强制全量迁移。首次运行时后台逐步填充。

### 13.4 旧缓存兼容

- `FolderDetailsCache` 保留，继续服务文件夹选择。
- `FileSignatureCache` / `PHashCache` 保留，继续服务重复扫描。
- 旧缓存不迁移到 `media_index`，避免启动时长时间 migration。
- 如果缓存结构失效，通过后台重建，不阻塞启动。

## 14. Feature flag 策略

新增功能必须可灰度、可回退。

建议 flag：

```text
delete_pool_enabled
final_delete_executor_enabled
limited_media_permission_enabled
media_index_enabled
tree_scanner_enabled
```

落地顺序：

1. `delete_pool_enabled`：Swiper 右上角滑动加入删除池。
2. `final_delete_executor_enabled`：最终删除走新 executor。
3. `limited_media_permission_enabled`：媒体权限降级模式。
4. `media_index_enabled`：列表改从轻量索引读取。
5. `tree_scanner_enabled`：统一扫描器替换旧全盘扫描。

如果新路径失败，短期可以回退到旧的 `MediaRepository.deleteMedia()` 物理删除实现，但 UI 语义必须保持“先入池，最终确认后删除”。

## 15. 性能目标修订

### 15.1 可验证指标

| 场景                     | 目标                |
| ------------------------ | ------------------- |
| App 打开后显示缓存文件夹 | 500ms - 1500ms      |
| 清理页首图显示           | 500ms - 1500ms      |
| 向右上角滑动后的 UI 响应 | < 50ms，乐观更新    |
| 删除池 entry 落库        | < 100ms             |
| 删除池首屏显示           | 500ms - 1000ms      |
| 最终删除                 | 显示进度，不阻塞 UI |

### 15.2 埋点

必须先加轻量 timing log：

```text
performSinglePassFileSystemScan
findViableTargetFolders
getMediaFromBuckets
createMediaItemFromFile
ThumbnailPrewarmer.prewarm
DeletePoolManager.add
FinalDeleteUseCase.start
PhysicalDeleteExecutor.deleteBatch
```

记录：

- 扫描目录数。
- 扫描文件数。
- MediaStore 返回数。
- 首批媒体返回耗时。
- 缩略图加载耗时。
- 删除池 DB 写入耗时。
- 最终删除成功/失败/需权限数量。

## 16. 分阶段落地计划

### 阶段 1：删除池 MVP

目标：用户向右上角滑动只加入删除池。

改动：

- 新增 `delete_pool_entry` 表和 DAO。
- 新增 `DeletePoolRepository`。
- 新增 `DeletePoolManager`。
- Swiper 删除手势改为 `DeletePoolManager.add()`。
- 删除池内媒体从当前清理队列排除。
- 新增删除池入口和基础列表。
- 支持恢复。

不做：

- 不做完整 media_index。
- 不做复杂 operation journal。
- 不做全新 scanner。

### 阶段 2：最终删除

目标：用户在删除池最终确认后物理删除。

改动：

- 新增 `FinalDeleteUseCase`。
- 新增 `PhysicalDeleteExecutor`。
- 复用 `FileManager` / `FileOperationsHelper` / `ContentResolver.delete()`。
- 支持 `MediaStore.createDeleteRequest()`。
- 支持失败项和需权限项展示。
- 批量 MediaScanner，避免逐个扫描。

### 阶段 3：预览性能优化

目标：滑动和点击预览更快。

改动：

- 统一 Coil `ImageLoader`。
- 所有预览请求指定 size。
- `PreviewPrefetcher` 只预加载后续 3 到 5 张。
- 禁止 `Size.ORIGINAL` 用于尺寸探测。
- 视频预览懒加载。

### 阶段 4：扫描性能优化

目标：减少全盘扫描对 UI 的影响。

改动：

- 缓存命中时不立即全盘刷新。
- 停止启动期全盘 target folder scan。
- `getMediaFromBuckets()` 优先返回 MediaStore 结果。
- File fallback 后台补齐。
- Android/media 纳入 TreeScanner root，但不单独建复杂模块。

### 阶段 5：轻量 media_index（可选）

目标：如果现有 repository 优化仍不够，再引入轻量索引。

改动：

- 新增轻量 `media_index`。
- 文件夹/清理列表逐步改为 Room + Paging。
- 删除池 entry 从保存 locator 逐步改为引用 `media_key`。
- 旧缓存保留，逐步下线实时扫描路径。

### 阶段 6：重复/相似清理接入删除池

目标：重复扫描不直接删除，统一进入删除池。

改动：

- Duplicate screen 的删除动作改为批量加入删除池。
- hash cache 继续沿用现有表。
- scoped scan 不删除 scope 外 hash cache。

## 17. 最终目标

CleanSweep 最终应从：

```text
实时扫描文件 -> 构建列表 -> 滑动 -> 直接物理删除
```

演进为：

```text
复用缓存/索引 -> 快速预览 -> 向右上角滑动加入删除池 -> 删除池管理 -> 最终确认后批量物理删除
```

但落地顺序必须务实：

```text
先删除池
再最终删除 executor
再预览性能
再扫描性能
最后才考虑轻量 media_index
```

这样既能满足图片清理 App 的核心业务，又不会在当前代码库上一次性叠加过多新模块和高风险迁移。
