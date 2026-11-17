package com.openbite.core.model

/** 两类提醒的开关。 */
data class ReminderSettings(val mealFollowUp: Boolean = true, val milestones: Boolean = true)
