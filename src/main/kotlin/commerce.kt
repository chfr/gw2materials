data class Listing(
    val itemId: Int,
    val highestBuyOrder: Int,
    val highestSellOrder: Int
) {
    val withFees by lazy {
        this.copy(
            highestBuyOrder = (highestBuyOrder * 0.85).toInt(),
            highestSellOrder = (highestSellOrder * 0.85).toInt()
        )
    }
}

