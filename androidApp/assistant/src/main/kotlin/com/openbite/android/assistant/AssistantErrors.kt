package com.openbite.android.assistant

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.openbite.core.domain.AssistantErrorKind
import com.openbite.core.domain.AssistantException

/** 返回给助手的错误：哪一类、说明文字。纯数据，JVM 单测能直接测（AppFunctionException 构造要用 Android 的 Bundle）。 */
internal data class AppFunctionError(val type: Type, val message: String?) {
    enum class Type { INVALID_ARGUMENT, NOT_FOUND, UNKNOWN }
}

/**
 * AssistantApi 的错误 → 给助手的错误。参数错和找不到把说明原样给助手；
 * 其他异常（读写数据库失败等）只给一句笼统的话，不把内部细节带出去。
 */
internal fun Exception.toAppFunctionError(): AppFunctionError = when (this) {
    is AssistantException -> when (kind) {
        AssistantErrorKind.INVALID_ARGUMENT -> AppFunctionError(AppFunctionError.Type.INVALID_ARGUMENT, message)
        AssistantErrorKind.NOT_FOUND -> AppFunctionError(AppFunctionError.Type.NOT_FOUND, message)
    }
    else -> AppFunctionError(AppFunctionError.Type.UNKNOWN, UNKNOWN_ERROR_MESSAGE)
}

internal fun AppFunctionError.toException(): AppFunctionException = when (type) {
    AppFunctionError.Type.INVALID_ARGUMENT -> AppFunctionInvalidArgumentException(message)
    AppFunctionError.Type.NOT_FOUND -> AppFunctionElementNotFoundException(message)
    AppFunctionError.Type.UNKNOWN -> AppFunctionAppUnknownException(message)
}

internal const val UNKNOWN_ERROR_MESSAGE = "OpenBite could not read or write its data."
