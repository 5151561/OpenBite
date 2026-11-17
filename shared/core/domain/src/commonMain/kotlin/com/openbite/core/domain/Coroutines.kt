package com.openbite.core.domain

import kotlinx.coroutines.CoroutineScope

/**
 * 应用级协程作用域：页面和 ViewModel 已经没了也要做完的事（比如删掉没存进去的临时照片）。
 * 生产环境由 DI 提供 `SupervisorJob() + Dispatchers.Default`，测试里传 TestScope。
 */
class ApplicationScope(scope: CoroutineScope) : CoroutineScope by scope
