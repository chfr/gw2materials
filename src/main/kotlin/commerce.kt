import java.time.LocalDateTime

data class Listing(
    val timestamp: LocalDateTime,
    val itemId: Int,
    val highestBuyOrder: Int,
    val lowestSellOrder: Int
) {
    fun withFees() = this.copy(
        highestBuyOrder = (highestBuyOrder * 0.85).toInt(),
        lowestSellOrder = (lowestSellOrder * 0.85).toInt()
    )
}

