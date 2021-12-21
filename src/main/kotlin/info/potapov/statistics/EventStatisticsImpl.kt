package info.potapov.statistics

import info.potapov.clock.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class EventStatisticsImpl(
    private val clock: Clock
) : EventStatistics {

    private val mapLock = ReentrantReadWriteLock()

    private val eventMap: MutableMap<String, EventBucket> = HashMap()

    private var emptyBucketsCount = AtomicInteger(0)

    override fun incEvent(name: String) {
        val currentTime = clock.now

        mapLock.read {

            if (!eventMap.containsKey(name)) {
                mapLock.write {
                    if (!eventMap.containsKey(name)) {
                        eventMap[name] = EventBucket(currentTime)
                        return
                    }
                }
            }

            val bucket = eventMap[name] ?: error("Internal error")
            bucket.incEvent(currentTime)
        }

        sanitize(currentTime)
    }

    override fun getEventStatisticByName(name: String): Double {
        val currentTime = clock.now

        mapLock.read {
            sanitize(currentTime)

            val bucket = eventMap[name] ?: return 0.0

            return bucket.freshEventsCount(currentTime.minus(1, DateTimeUnit.HOUR)) / MINUTES_IN_HOUR
        }
    }

    override fun getAllEventStatistics(): Map<String, Double> {
        val currentTime = clock.now

        mapLock.read {
            sanitize(currentTime)

            return eventMap.mapValues { it.value.freshEventsCount(currentTime.minus(1, DateTimeUnit.HOUR)) }
                .filterValues { it != 0 }
                .mapValues { it.value / MINUTES_IN_HOUR }
        }
    }

    override fun printStatistics() {
        val statistics = getAllEventStatistics()
        statistics.forEach { (name, rps) ->
            println("$name -> $rps")
        }
    }

    private fun sanitize(currentTime: Instant) {
        if (emptyBucketsCount.get() > SANITIZE_THRESHOLD) {
            mapLock.write {

                eventMap.forEach { (key, bucket) ->
                    if (bucket.freshEventsCount(currentTime) == 0) {
                        eventMap.remove(key)
                    }
                }

                emptyBucketsCount.set(0)
            }
        }
    }

    inner class EventBucket(
        initial: Instant
    ) {

        private var queue: Queue<Instant> = ArrayDeque()

        init {
            queue.offer(initial)
        }

        @Synchronized
        fun incEvent(time: Instant) {
            queue.offer(time)

            if (queue.size == 1) {
                emptyBucketsCount.decrementAndGet()
            }
        }

        @Synchronized
        fun freshEventsCount(currentTime: Instant): Int {
            while (queue.isNotEmpty() && queue.peek() < currentTime) {
                queue.poll()
            }

            if (queue.isEmpty()) {
                emptyBucketsCount.incrementAndGet()
            }

            return queue.size
        }

    }


    companion object {
        private const val MINUTES_IN_HOUR = 60.0
        private const val SANITIZE_THRESHOLD = 100
    }

}