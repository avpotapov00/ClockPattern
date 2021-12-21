package info.potapov.statistics

interface EventStatistics {

    fun incEvent(name: String)

    fun getEventStatisticByName(name: String): Double

    fun getAllEventStatistics(): Map<String, Double>

    fun printStatistics()

}