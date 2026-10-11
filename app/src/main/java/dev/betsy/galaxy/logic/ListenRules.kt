package dev.betsy.galaxy.logic

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Same credit rule as Betsy `calculate_listen_credits`. */
fun calculateListenCredits(
    duration: Double,
    eligible: Double,
    forwardSeek: Double,
    perMinute: Int = 2,
    bonusPercent: Int = 25,
    penaltyPercent: Int = 100,
): Int {
    val safeDuration = max(1.0, duration)
    val heard = max(0.0, min(safeDuration, eligible))
    val skipped = max(0.0, min(safeDuration, forwardSeek))
    var base = heard / 60.0 * max(0, perMinute)
    val deduction = skipped / 60.0 * max(0, perMinute) * max(0, penaltyPercent) / 100.0
    if (heard >= safeDuration * 0.98 && skipped <= 0.5) {
        base *= 1.0 + max(0, bonusPercent) / 100.0
    }
    return max(0, floor(base - deduction + 1e-9).toInt())
}

data class ListenRules(
    val heartbeatSeconds: Int = 12,
    val minListenSeconds: Int = 30,
    val minListenPercent: Int = 70,
    val creditsPerMinute: Int = 2,
    val fullListenBonusPercent: Int = 25,
    val forwardSeekPenaltyPercent: Int = 100,
    val dailyCreditCap: Int = 200,
    val repeatCooldownDays: Int = 7,
)
