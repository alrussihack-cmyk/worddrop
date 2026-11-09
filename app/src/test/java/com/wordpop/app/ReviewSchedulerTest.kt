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
    @Test
    fun hardAfterFirstGoodSchedulesTwoDays() {
        val first = ReviewScheduler.next(null, ReviewRating.Good, now)
        val second = ReviewScheduler.next(first, ReviewRating.Hard, now + day)
        assertEquals(2, second.intervalDays)
        assertEquals(now + day + 2 * day, second.dueAt)
    }

    @Test
    fun easyAfterFirstGoodSchedulesSevenDays() {
        val first = ReviewScheduler.next(null, ReviewRating.Good, now)
        val second = ReviewScheduler.next(first, ReviewRating.Easy, now + day)
        assertEquals(7, second.intervalDays)
        assertEquals(now + day + 7 * day, second.dueAt)
    }

    @Test
    fun easyRatingNeverRaisesEaseAboveMaximum() {
        var state: ReviewState? = null
        repeat(12) {
            state = ReviewScheduler.next(state, ReviewRating.Easy, now + it * day)
        }
        assertEquals(3.0, state!!.ease, 0.0)
    }

    @Test
    fun reviewHistoryRoundTripsThroughJson() {
        val expected = mapOf(
            42 to ReviewState(
                intervalDays = 7,
                ease = 2.65,
                repetitions = 4,
                dueAt = now + 7 * day,
                lastReviewedAt = now,
                lapses = 1
            )
        )
        assertEquals(expected, ReviewScheduler.decode(ReviewScheduler.encode(expected)))
    }

    @Test
    fun invalidReviewJsonReturnsEmptyHistory() {
        assertTrue(ReviewScheduler.decode("not-json").isEmpty())
    }

    @Test
    fun reviewDayKeyUsesStableIsoDateShape() {
        assertTrue(ReviewScheduler.dayKey(now).matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun dailyNewCardLimitIsFive() {
        assertEquals(5, ReviewScheduler.NEW_CARDS_PER_DAY)
    }

}
