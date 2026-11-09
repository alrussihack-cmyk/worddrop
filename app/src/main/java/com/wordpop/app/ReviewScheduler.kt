package com.wordpop.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReviewRating { Again, Hard, Good, Easy }

data class ReviewState(
    val intervalDays: Int,
    val ease: Double,
    val repetitions: Int,
    val dueAt: Long,
    val lastReviewedAt: Long,
    val lapses: Int
)

object ReviewScheduler {
    const val NEW_CARDS_PER_DAY = 5
    const val MAX_DUE_PER_SESSION = 20
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val AGAIN_DELAY_MS = 10L * 60L * 1000L

    fun dayKey(time: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))

    fun next(previous: ReviewState?, rating: ReviewRating, now: Long): ReviewState {
        val old = previous ?: ReviewState(
            intervalDays = 0,
            ease = 2.5,
            repetitions = 0,
            dueAt = now,
            lastReviewedAt = 0L,
            lapses = 0
        )

        val ease = when (rating) {
            ReviewRating.Again -> (old.ease - 0.2).coerceAtLeast(1.3)
            ReviewRating.Hard -> (old.ease - 0.15).coerceAtLeast(1.3)
            ReviewRating.Good -> old.ease
            ReviewRating.Easy -> (old.ease + 0.15).coerceAtMost(3.0)
        }

        val interval = when (rating) {
            ReviewRating.Again -> 0
            ReviewRating.Hard -> when (old.repetitions) {
                0 -> 1
                1 -> 2
                else -> (old.intervalDays * 1.2).toInt().coerceAtLeast(old.intervalDays + 1)
            }
            ReviewRating.Good -> when (old.repetitions) {
                0 -> 1
                1 -> 3
                else -> (old.intervalDays * old.ease).toInt().coerceAtLeast(old.intervalDays + 1)
            }
            ReviewRating.Easy -> when (old.repetitions) {
                0 -> 4
                1 -> 7
                else -> (old.intervalDays * old.ease * 1.3).toInt().coerceAtLeast(old.intervalDays + 1)
            }
        }

        val dueAt = if (rating == ReviewRating.Again) {
            now + AGAIN_DELAY_MS
        } else {
            now + interval.coerceAtLeast(1) * DAY_MS
        }

        return ReviewState(
            intervalDays = interval,
            ease = ease,
            repetitions = if (rating == ReviewRating.Again) 0 else old.repetitions + 1,
            dueAt = dueAt,
            lastReviewedAt = now,
            lapses = old.lapses + if (rating == ReviewRating.Again) 1 else 0
        )
    }

    fun decode(raw: String): Map<Int, ReviewState> {
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<Int, ReviewState>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next().toIntOrNull() ?: continue
                val item = root.optJSONObject(id.toString()) ?: continue
                result[id] = ReviewState(
                    intervalDays = item.optInt("intervalDays", 0).coerceAtLeast(0),
                    ease = item.optDouble("ease", 2.5).coerceIn(1.3, 3.0),
                    repetitions = item.optInt("repetitions", 0).coerceAtLeast(0),
                    dueAt = item.optLong("dueAt", 0L),
                    lastReviewedAt = item.optLong("lastReviewedAt", 0L),
                    lapses = item.optInt("lapses", 0).coerceAtLeast(0)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encode(cards: Map<Int, ReviewState>): String {
        val root = JSONObject()
        cards.toSortedMap().forEach { (id, state) ->
            root.put(
                id.toString(),
                JSONObject()
                    .put("intervalDays", state.intervalDays)
                    .put("ease", state.ease)
                    .put("repetitions", state.repetitions)
                    .put("dueAt", state.dueAt)
                    .put("lastReviewedAt", state.lastReviewedAt)
                    .put("lapses", state.lapses)
            )
        }
        return root.toString()
    }
}
