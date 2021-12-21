package info.potapov.clock

import kotlinx.datetime.Instant

class RealTimeClock : Clock {

    override val now: Instant
        get() = kotlinx.datetime.Clock.System.now()

}

