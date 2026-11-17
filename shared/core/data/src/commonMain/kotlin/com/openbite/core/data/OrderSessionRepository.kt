package com.openbite.core.data

import com.openbite.core.model.OrderMenu
import com.openbite.core.model.TasteProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 一次点单的全部状态：菜单（可能还在识别）、每道菜点了几份、这次临时改的口味（null 表示用档案）。 */
data class OrderSessionData(
    val menu: OrderMenu,
    val quantities: Map<String, Int> = emptyMap(),
    val tasteOverride: TasteProfile? = null,
)

/**
 * 点单会话的内存数据源。识别页、点单页、小票页共用同一个会话（边识别边点单）。
 * 只在内存里，进程被杀就没了；页面拿不到会话时自己退回上一页。
 */
interface OrderSessionRepository {
    /** 新建会话，返回可读写的状态。 */
    fun create(id: String, initial: OrderMenu): MutableStateFlow<OrderSessionData>

    operator fun get(id: String): MutableStateFlow<OrderSessionData>?

    fun remove(id: String)

    /** “重新识别”命令。识别页订阅并执行；没有识别页在听时返回 false。 */
    fun requestRetry(id: String): Boolean

    fun retryRequests(id: String): Flow<Unit>
}

class InMemoryOrderSessionRepository : OrderSessionRepository {
    private val sessions = mutableMapOf<String, MutableStateFlow<OrderSessionData>>()
    private val retries = mutableMapOf<String, MutableSharedFlow<Unit>>()

    override fun create(id: String, initial: OrderMenu): MutableStateFlow<OrderSessionData> =
        MutableStateFlow(OrderSessionData(initial)).also {
            sessions[id] = it
            retries[id] = MutableSharedFlow(extraBufferCapacity = 1)
        }

    override fun get(id: String): MutableStateFlow<OrderSessionData>? = sessions[id]

    override fun remove(id: String) {
        sessions.remove(id)
        retries.remove(id)
    }

    override fun requestRetry(id: String): Boolean {
        val channel = retries[id] ?: return false
        return channel.subscriptionCount.value > 0 && channel.tryEmit(Unit)
    }

    override fun retryRequests(id: String): Flow<Unit> = retries[id]?.asSharedFlow() ?: kotlinx.coroutines.flow.emptyFlow()
}
