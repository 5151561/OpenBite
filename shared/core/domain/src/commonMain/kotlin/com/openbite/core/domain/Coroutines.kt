package com.openbite.core.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * 应用级协程作用域：页面和 ViewModel 已经没了也要做完的事（比如删掉没存进去的临时照片）。
 * 生产环境由 DI 提供 `SupervisorJob() + Dispatchers.Default`，测试里传 TestScope。
 */
class ApplicationScope(scope: CoroutineScope) : CoroutineScope by scope

/** 用例里纯 CPU 的活（zip 压缩/解压、大 JSON、Base64、流式解析）切到 [default]，不占主线程。测试里换成 TestDispatcher。 */
class AppDispatchers(val default: CoroutineDispatcher = Dispatchers.Default)
