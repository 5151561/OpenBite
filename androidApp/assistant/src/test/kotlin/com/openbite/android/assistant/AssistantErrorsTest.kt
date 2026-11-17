package com.openbite.android.assistant

import com.openbite.core.domain.AssistantErrorKind
import com.openbite.core.domain.AssistantException
import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantErrorsTest {
    @Test
    fun assistantErrorsKeepTheirMessage() {
        assertEquals(
            AppFunctionError(AppFunctionError.Type.INVALID_ARGUMENT, "daysAhead must be between 1 and 366."),
            AssistantException(AssistantErrorKind.INVALID_ARGUMENT, "daysAhead must be between 1 and 366.").toAppFunctionError(),
        )
        assertEquals(
            AppFunctionError(AppFunctionError.Type.NOT_FOUND, "No place with id x."),
            AssistantException(AssistantErrorKind.NOT_FOUND, "No place with id x.").toAppFunctionError(),
        )
    }

    @Test
    fun otherErrorsDoNotLeakDetails() {
        val error = IllegalStateException("SQLITE_FULL /data/user/0/com.openbite/databases/openbite.db").toAppFunctionError()
        assertEquals(AppFunctionError(AppFunctionError.Type.UNKNOWN, UNKNOWN_ERROR_MESSAGE), error)
    }
}
