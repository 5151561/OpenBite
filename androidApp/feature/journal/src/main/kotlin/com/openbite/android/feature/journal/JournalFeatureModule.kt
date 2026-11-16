package com.openbite.android.feature.journal

import com.openbite.feature.journal.JournalViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val journalFeatureModule = module {
    viewModelOf(::JournalViewModel)
}
