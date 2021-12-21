package info.potapov.clock

import kotlinx.datetime.Instant

interface Clock {

    val now: Instant

}