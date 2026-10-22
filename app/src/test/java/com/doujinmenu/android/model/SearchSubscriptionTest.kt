package com.doujinmenu.android.model

import org.junit.Assert.*
import org.junit.Test

class SearchSubscriptionTest {
    @Test fun onlyTagsCanEnableMonitoring() {
        SearchSubscription(name="Saved",query="title words").validate()
        listOf("words", "artist:example words", "-tag:example", "sort:newest artist:example", "unknown:example").forEach {
            assertThrows(IllegalArgumentException::class.java) { SearchSubscription(name="Saved",query=it,enabled=true).validate() }
        }
        SearchSubscription(name="Tags",query="artist:example -tag:excluded language:korean",enabled=true).validate()
    }
    @Test fun firstSuccessfulCheckIsOnlyABaselineAndIdsAreDeduplicated() {
        assertEquals(emptyList<Long>(),subscriptionNewIds(listOf(3,2,1),null))
        assertEquals(listOf(5L,4L),subscriptionNewIds(listOf(5,4,5,3,2),3))
    }
    @Test fun languagesAreAlternativesAndExclusionsSurvive() {
        val value=SearchSubscription(name="Tags",query="artist:example -language:english",languages=setOf("korean","japanese"),enabled=true)
        assertEquals(2,value.queries.size)
        assertTrue(value.queries.all { "-language:english" in it })
        assertEquals(value,SearchSubscription.fromJson(value.json()))
        assertEquals(value.conditionKey,value.copy(query="-language:english artist:example").conditionKey)
    }
}
