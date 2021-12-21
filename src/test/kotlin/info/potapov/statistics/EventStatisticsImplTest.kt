package info.potapov.statistics

import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.Phaser
import kotlin.concurrent.thread

private typealias Statistics = Map<String, Double>

internal class EventStatisticsImplTest {

    @Test
    fun `should calculate simple statistics in one hour`() {
        val clock = MockClock(Clock.System.now())

        val eventStatistics = EventStatisticsImpl(clock)

        for (i in 1 until 4) {
            val eventName = "event_$i"
            (0 until i).forEach { _ -> eventStatistics.incEvent(eventName) }
        }

        val expectedStatistics = mapOf(
            "event_1" to rpsForHour(1),
            "event_2" to rpsForHour(2),
            "event_3" to rpsForHour(3)
        )

        assertStatisticsEquals(expectedStatistics, eventStatistics)
    }

    @Test
    fun `should remove old events types from statistics`() {
        val clock = MockClock(Clock.System.now())

        val eventStatistics = EventStatisticsImpl(clock)

        eventStatistics.incEvent("old_event_1")
        eventStatistics.incEvent("old_event_2")
        eventStatistics.incEvent("old_event_3")

        clock plusHours 2

        for (i in 1 until 4) {
            val eventName = "event_$i"
            (0 until i).forEach { _ -> eventStatistics.incEvent(eventName) }
        }

        val expectedStatistics = mapOf(
            "event_1" to rpsForHour(1),
            "event_2" to rpsForHour(2),
            "event_3" to rpsForHour(3)
        )

        assertStatisticsEquals(expectedStatistics, eventStatistics)
    }

    @Test
    fun `should remove old events from statistics`() {
        val clock = MockClock(Clock.System.now())

        val eventStatistics = EventStatisticsImpl(clock)

        eventStatistics.incEvent("event_1")
        eventStatistics.incEvent("event_2")
        eventStatistics.incEvent("event_3")

        clock plusHours 2

        for (i in 1 until 4) {
            val eventName = "event_$i"
            (0 until i).forEach { _ -> eventStatistics.incEvent(eventName) }
        }

        val expectedStatistics = mapOf(
            "event_1" to rpsForHour(1),
            "event_2" to rpsForHour(2),
            "event_3" to rpsForHour(3)
        )

        assertStatisticsEquals(expectedStatistics, eventStatistics)
    }

    @Test
    fun `should remove old events on fractional hour`() {
        val clock = MockClock(Clock.System.now())

        val eventStatistics = EventStatisticsImpl(clock)

        eventStatistics.incEvent("event_1")

        clock plusMinutes 40

        eventStatistics.incEvent("event_2")
        eventStatistics.incEvent("event_3")

        clock plusHours 1

        eventStatistics.incEvent("event_1")
        eventStatistics.incEvent("event_2")
        eventStatistics.incEvent("event_3")

        val expectedStatistics = mapOf(
            "event_1" to rpsForHour(1),
            "event_2" to rpsForHour(2),
            "event_3" to rpsForHour(2),
        )

        assertStatisticsEquals(expectedStatistics, eventStatistics)
    }

    @Test
    fun `should calculate statistic after many hours`() {
        val clock = MockClock(Clock.System.now())
        val eventStatistics = EventStatisticsImpl(clock)

        eventStatistics.incEvent("event_1")
        clock.plusMinutes(10)

        eventStatistics.incEvent("event_1")
        clock.plusMinutes(55)

        assertStatisticsEquals(mapOf("event_1" to rpsForHour(1)), eventStatistics)

        repeat(30) {
            eventStatistics.incEvent("event_2")
        }

        assertStatisticsEquals(
            mapOf(
                "event_1" to rpsForHour(1),
                "event_2" to rpsForHour(30)
            ), eventStatistics
        )

        clock plusMinutes 30
        repeat(3) { eventStatistics.incEvent("event_3") }
        clock.plusMinutes(25)

        assertStatisticsEquals(
            mapOf(
                "event_3" to rpsForHour(3),
                "event_2" to rpsForHour(30)
            ), eventStatistics
        )
    }

    @Test
    fun kek() {
        val phaser = Phaser(4)

        val clock = MockClock(Clock.System.now())
        val eventStatistics = EventStatisticsImpl(clock)

        (0 until 4).map { threadId ->
            thread {

                eventStatistics.incEvent("common_event")
                repeat(2) { eventStatistics.incEvent("event_$threadId") }

                phaser.arriveAndAwaitAdvance()

                assertStatisticsEquals(
                    mapOf(
                        "common_event" to rpsForHour(4),
                        "event_0" to rpsForHour(2),
                        "event_1" to rpsForHour(2),
                        "event_2" to rpsForHour(2),
                        "event_3" to rpsForHour(2)
                    ),
                    eventStatistics
                )

                clock plusMinutes 18

                repeat(3) { eventStatistics.incEvent("event_$threadId") }
                repeat(4) { eventStatistics.incEvent("common_event") }

            }
        }.forEach { it.join() }

        assertStatisticsEquals(
            mapOf(
                "common_event" to rpsForHour(16),
                "event_0" to rpsForHour(3),
                "event_1" to rpsForHour(3),
                "event_2" to rpsForHour(3),
                "event_3" to rpsForHour(3)
            ),
            eventStatistics
        )
    }

    private fun assertStatisticsEquals(expected: Statistics, eventStatistics: EventStatistics) {
        val actual = eventStatistics.getAllEventStatistics()

        assertEquals(expected.size, actual.size)

        expected.forEach { (name, rps) ->
            val actualValue = eventStatistics.getEventStatisticByName(name)
            assertEquals(rps, actualValue, DOUBLE_COMPARISON_TOLERANCE)

            val actualRps = actual[name] ?: error("Event $name not found in actual result")
            assertEquals(rps, actualRps, DOUBLE_COMPARISON_TOLERANCE)
        }
    }


    private fun rpsForHour(count: Int): Double {
        return count / 60.0
    }

}

private const val DOUBLE_COMPARISON_TOLERANCE = 1e-6
