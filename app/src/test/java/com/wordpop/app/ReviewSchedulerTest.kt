package com.wordpop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    @Test
    fun firstGoodReviewIsDueTomorrow() {
        val next = ReviewScheduler.next(null, ReviewRating.Good, now)
        assertEquals(1, next.intervalDays)
        assertEquals(now + day, next.dueAt)
        assertEquals(1, next.repetitions)
    }

    @Test
    fun firstEasyReviewGetsLongerInterval() {
        val next = ReviewScheduler.next(null, ReviewRating.Easy, now)
        assertEquals(4, next.intervalDays)
        assertEquals(now + 4 * day, next.dueAt)
    }

    @Test
    fun againSchedulesShortRetryAndResetsRepetitions() {
        val previous = ReviewScheduler.next(null, ReviewRating.Good, now)
        val next = ReviewScheduler.next(previous, ReviewRating.Again, now + day)
        assertEquals(0, next.intervalDays)
        assertEquals(now + day + 10 * 60 * 1000L, next.dueAt)
        assertEquals(0, next.repetitions)
        assertEquals(1, next.lapses)
    }

    @Test
    fun secondGoodReviewMovesToThreeDays() {
        val first = ReviewScheduler.next(null, ReviewRating.Good, now)
        val second = ReviewScheduler.next(first, ReviewRating.Good, now + day)
        assertEquals(3, second.intervalDays)
        assertEquals(2, second.repetitions)
    }

    @Test
    fun easeNeverDropsBelowMinimum() {
        var state: ReviewState? = null
        repeat(20) {
            state = ReviewScheduler.next(state, ReviewRating.Again, now + it * 1000L)
        }
        assertTrue(state!!.ease >= 1.3)
    }
}
