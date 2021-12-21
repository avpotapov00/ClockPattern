package info.potapov.statistics

import info.potapov.clock.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import java.util.concurrent.atomic.AtomicReference

class MockClock(
    initial: Instant
) : Clock {

    private var nextTimeReturn = AtomicReference(initial)

    override val now: Instant
        get() = nextTimeReturn.get()

    infix fun plusHours(hours: Int) {
        nextTimeReturn.updateAndGet { it.plus(hours, DateTimeUnit.HOUR) }
    }

    infix fun plusMinutes(minutes: Int) {
        nextTimeReturn.updateAndGet { it.plus(minutes, DateTimeUnit.MINUTE) }
    }

}