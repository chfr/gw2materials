import java.time.LocalDateTime

data class Listing(
    val timestamp: LocalDateTime,
    val itemId: Int,
    val highestBuyOrder: Int,
    val lowestSellOrder: Int
) {
    val withFees by lazy {
        this.copy(
            highestBuyOrder = (highestBuyOrder * 0.85).toInt(),
            lowestSellOrder = (lowestSellOrder * 0.85).toInt()
        )
    }
}

