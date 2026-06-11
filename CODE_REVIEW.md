# Tools 代码审核报告

> 审核日期: 2026-06-11 | 审核范围: 全部模块 (app, core, feature:media, feature:ebook, feature:lightlux)

---

## 严重问题 (Critical)

### 1. ConversionViewModel 缺少 `remember` 导致每次重组都创建新实例

**文件:** `app/src/main/java/com/robin/tools/MainActivity.kt:90`

**问题:** `ConversionViewModel(applicationContext)` 直接在 Compose lambda 中构造，没有用 `remember {}` 包裹。每次重组（例如转换进度更新触发 `uiState` 变化）都会创建一个全新的 ViewModel，导致：

- 转换进度状态丢失，UI 在转换过程中重置为 `Idle`
- 旧 ViewModel 的 `viewModelScope`、`EpubToPdfConverter`、WebView 都被抛弃但未释放
- 用户永远看不到转换结果

```kotlin
// 当前代码 (有 Bug)
EbookScreen(viewModel = ConversionViewModel(applicationContext), onBack = { ... })

// 修复
val ebookViewModel = remember { ConversionViewModel(applicationContext) }
EbookScreen(viewModel = ebookViewModel, onBack = { ... })
```

---

### 2. `startForeground` 失败被静默吞掉，服务在无保护状态下运行

**文件:** `feature/media/.../service/CompressionService.kt:98-109`

**问题:** `startForeground()` 的异常被 `catch (e: Exception)` 捕获后仅打日志，但 `startCompression()` 继续执行。这意味着：

- Android 8+ 会在约 1 分钟内杀死后台服务
- FFmpeg 压缩任务中途终止，无清理回调执行
- 用户无法收到任何通知

```kotlin
// 修复: 让 startForegroundService 返回是否成功
private fun startForegroundService(): Boolean {
    val notification = createNotification(0, 100, getString(R.string.notification_ready))
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to start foreground service", e)
        false
    }
}
// 调用方检查返回值，失败则 stopSelf()
```

---

### 3. 协程取消时 WebView 和临时目录清理被跳过

**文件:** `feature/ebook/.../converter/EpubToPdfConverter.kt:198-206`

**问题:** `finally` 块中使用了 `withContext(Dispatchers.Main)`，但这是一个挂起点。当协程被取消时（如用户导航离开），`withContext` 会抛出 `CancellationException`，导致：

- WebView 永远不会被 `destroy()`（原生内存泄漏）
- 临时解压目录不会被 `deleteRecursively()` 清除（磁盘空间泄漏）

```kotlin
// 修复: 使用 NonCancellable 确保清理始终执行
} finally {
    withContext(NonCancellable) {
        withContext(Dispatchers.Main) {
            webView?.destroy()
        }
        tempDir.deleteRecursively()
    }
}
```

---

## 重要问题 (Important)

### 4. SharedFlowBus 存在竞态条件 (TOCTOU)

**文件:** `core/.../event/SharedFlowBus.kt:14-19`

**问题:** `containsKey` + `put` 操作不是原子的。如果两个线程同时调用 `with(SomeEvent::class.java)`，后者的 `put` 会覆盖前者已创建的 `MutableSharedFlow`，导致先前的收集者永远收不到后续事件。

```kotlin
// 当前代码 (有竞态)
fun <T> with(objectKey: Class<T>): MutableSharedFlow<T> {
    if (!events.containsKey(objectKey)) {
        events[objectKey] = MutableSharedFlow(...)
    }
    return events[objectKey] as MutableSharedFlow<T>
}

// 修复: 使用原子操作 computeIfAbsent
fun <T> with(objectKey: Class<T>): MutableSharedFlow<T> {
    @Suppress("UNCHECKED_CAST")
    return events.computeIfAbsent(objectKey) {
        MutableSharedFlow(0, Int.MAX_VALUE, BufferOverflow.SUSPEND)
    } as MutableSharedFlow<T>
}
```

---

### 5. Bitmap 在异常路径上未回收

**文件:** `feature/media/.../delegate/ImageCompressionDelegate.kt:73-92`

**问题:** 当 `bitmap.compress()` 或 `FileOutputStream` 操作抛出异常时，`bitmap.recycle()` 被跳过。大图（最大 1920x1920）的 native 内存不会被及时释放，可能导致 OOM 级联。

```kotlin
// 修复: 在 catch 块中也回收 bitmap
} catch (e: Exception) {
    bitmap.recycle()
    outputFile.delete()
    Result.failure(e)
}
```

---

### 6. UnPeekLiveData 使用脆弱的反射 Hack

**文件:** `core/.../callback/livedata/UnPeekLiveData.kt:23-64`

**问题:** 通过反射访问 `LiveData` 的包私有字段 (`mObservers`, `mVersion`, `ObserverWrapper.mLastVersion`)。风险：

- 这些是 `androidx.lifecycle` 的内部实现细节，随时可能变更
- Android 14+ 的反射限制可能导致此代码静默失败
- `catch (e: Exception) { e.printStackTrace() }` 吞掉所有异常，导致行为悄然退化为普通 `MutableLiveData`（事件变粘性）

```kotlin
// 建议: 替换为非反射的单事件模式
class SingleEvent<T> {
    private val _events = Channel<T>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    fun send(event: T) { _events.trySend(event) }
}
```

---

## 安全问题 (Security)

### 7. 硬编码 HTTP URL 用于网络连通性检测

**文件:** `core/.../network/NetworkUtil.java:22`

**问题:** `public static String url = "http://www.baidu.com"` 使用明文 HTTP。在 Android API 28+ 上，默认禁止明文流量，此连接将静默失败。即使能连上，也易受中间人攻击。

**修复:** 改为 `https://www.baidu.com`，或更好——改用 `ConnectivityManager.getActiveNetwork()` / `NetworkCapabilities` API 检测网络状态，无需外部 HTTP 请求。

---

### 8. WebView 未显式禁用 `allowFileAccess`

**文件:** `feature/ebook/.../converter/EpubToPdfConverter.kt:368-387`

**问题:** WebView 默认 `allowFileAccess = true`。虽然已正确禁用 JavaScript，但恶意 EPUB 中的 HTML 可通过 `<img>`、CSS `url()`、`<link>` 等引用任意 `file://` URL。应遵循纵深防御原则显式禁用。

```kotlin
// 在 createWebView() 中添加:
wv.settings.allowFileAccess = false
wv.settings.allowContentAccess = false  // 同样建议禁用
```

---

### 9. FileProvider 路径配置过于宽泛

**文件:** `feature/ebook/src/main/res/xml/file_paths.xml:3`

**问题:** `<cache-path name="cache" path="." />` 暴露了整个 `cacheDir`。任何由应用写入 `cacheDir` 的文件都可以通过 FileProvider 被外部应用访问。

```xml
<!-- 修复: 限制到特定子目录 -->
<cache-path name="pdf_output" path="pdf_output/" />
```

---

### 10. 网络日志拦截器默认 Level.ALL 且无条件安装

**文件:**
- `core/.../network/NetworkApi.kt:52`
- `core/.../network/interceptor/logging/LogInterceptor.kt:19-20`

**问题:** `LogInterceptor` 默认日志级别为 `Level.ALL`，且无条件添加到 OkHttpClient。虽然 `openLog = false` 控制了实际输出，但如果被误设为 `true`，完整的请求/响应头和 Body 将被记录。更重要的是 `LogInterceptor` 在每次请求时都将完整响应 Body 读入内存（`source.request(Long.MAX_VALUE)`），即使日志关闭，也会浪费内存。

**修复:** 改为仅在 Debug 构建中添加拦截器，并将 `printLevel` 默认设为 `Level.NONE`。

---

## 遗留风险 (从上次审核保留)

### 11. FFmpeg 二进制直接入库

**文件:** `feature/media/libs/ffmpeg-kit.aar`

**问题:** AAR 直接提交到仓库，体积大且不支持所有 ABI。

**建议:** 使用 Maven 依赖 `com.arthenica:ffmpeg-kit-full` 或配置 ABI splits。

---

### 12. EbookConverter 使用隐藏 API

**文件:** `feature/ebook/.../PrintDocumentAdapterHelper.java`

**问题:** 访问 Android 隐藏 API (`PrintDocumentAdapter.LayoutCallback`, `PrintDocumentAdapter.WriteCallback`)，可能在不同 Android 版本或 OEM 定制系统上崩溃。

**建议:** 使用 try-catch 包裹并提供回退方案（如直接使用 `PdfDocument`）。

---

### 13. CompressionManager 单例的复合操作非原子

**文件:** `feature/media/.../data/CompressionManager.kt`

**问题:** `startTask()` + `updateState()` 应为原子操作，但当前不是。

**建议:** 使用 `Mutex` 或 `StateFlow` 的原子更新防止服务与 UI 间的竞态。

---

### 14. Room 未配置 Schema 导出和迁移策略

**文件:** `feature/lightlux/.../data/AppDatabase.kt`

**问题:** `exportSchema = false` 且无迁移策略。

**建议:** 启用 schema 导出，生产环境添加迁移策略，开发期可用 `fallbackToDestructiveMigration()`。

---

## 审核通过项 (No Issues)

| 类别 | 结果 |
|------|------|
| 硬编码密钥 / API Key / Token | 未发现。`MyInterceptor.kt` 中的 token 处理代码已被注释掉 |
| Room SQL 注入 | 安全。所有 `@Query` 使用绑定参数，无原始 SQL 拼接 |
| Intent 注入 | 安全。`CompressionService` 为 `exported=false`，PendingIntent 使用 `FLAG_IMMUTABLE` |
| 世界可读写文件 | 安全。使用 `context.cacheDir` 和 `context.getExternalFilesDir`（应用私有目录） |
| JavaScript 接口暴露 | 安全。无 `addJavascriptInterface` 调用，WebView 已禁用 JavaScript |
| 权限过度声明 | 合理。存储权限正确使用 `maxSdkVersion` 限定 |
| 加密实现 | 无自定义加密。TLS 由系统/OkHttp 处理 |
| 数据备份泄露 | 安全。备份规则为空，Room 数据不会被备份 |
| ContentProvider 导出 | 安全。`Ktx` ContentProvider 为 `exported=false` |

---

## 优先级排序

| 优先级 | 编号 | 问题 | 影响 |
|--------|------|------|------|
| **P0 立即修复** | #1 | ConversionViewModel 缺少 remember | 电子书功能完全不可用 |
| **P0 立即修复** | #3 | 协程取消时清理被跳过 | WebView 内存泄漏 + 磁盘空间泄漏 |
| **P1 尽快修复** | #2 | startForeground 失败静默吞掉 | 特定设备上压缩会静默失败 |
| **P1 尽快修复** | #8 | WebView allowFileAccess 未禁用 | 恶意 EPUB 可读取本地文件 |
| **P1 尽快修复** | #9 | FileProvider 路径过宽 | 可能暴露应用缓存中的敏感文件 |
| **P2 计划修复** | #4 | SharedFlowBus 竞态条件 | 并发场景下事件丢失 |
| **P2 计划修复** | #5 | Bitmap 异常路径未回收 | 高负载时可能 OOM |
| **P2 计划修复** | #7 | HTTP 网络检测 URL | 功能在 API 28+ 上失效 |
| **P2 计划修复** | #13 | CompressionManager 非原子操作 | 并发场景状态不一致 |
| **P2 计划修复** | #14 | Room 无迁移策略 | 数据库升级丢失数据 |
| **P3 技术债务** | #6 | UnPeekLiveData 反射 Hack | Android 14+ 上可能静默退化 |
| **P3 技术债务** | #10 | 日志拦截器无条件安装 | 轻微性能和内存浪费 |
| **P3 技术债务** | #11 | FFmpeg AAR 入库 | 仓库体积大 |
| **P3 技术债务** | #12 | 隐藏 API 使用 | 兼容性风险 |