package com.openbite.feature.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SearchResultsTest {
    @Test
    fun debouncesAndOnlySearchesTheLastQuery() = runTest {
        val queries = MutableStateFlow("")
        val searched = mutableListOf<String>()
        val out = mutableListOf<SearchResults>()
        val job = launch {
            searchResults(queries, { q -> searched += q; SearchResults.Places(emptyList()) }, debounceMillis = 250).toList(out)
        }
        runCurrent()
        queries.value = "み"
        advanceTimeBy(100)
        queries.value = "みな"
        advanceTimeBy(100)
        queries.value = " みなと "
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("みなと"), searched)
        queries.value = ""
        runCurrent()
        assertEquals(SearchResults.Idle, out.last())
        assertEquals(true, out.contains(SearchResults.Searching))
        assertEquals(true, SearchResults.Places(emptyList()).isEmptyResult)
        job.cancel()
    }
}
