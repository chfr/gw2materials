import java.time.LocalDateTime

data class Listing(
    val timestamp: LocalDateTime,
    val itemId: Int,
    val highestBuyOrder: Int,
    val lowestSellOrder: Int,
    val static: Boolean
) {
    fun withFees() = this.copy(
        highestBuyOrder = (highestBuyOrder * 0.85).toInt(),
        lowestSellOrder = (lowestSellOrder * 0.85).toInt()
    )

    companion object {
        private fun stubListing(itemId: Int, lowestSellOrder: Int) = Listing(
            timestamp = LocalDateTime.now(),
            itemId = itemId,
            highestBuyOrder = 0,
            lowestSellOrder = lowestSellOrder,
            static = true
        )
        val statics = listOf(
            stubListing(19792, 8),
            stubListing(19789, 16),
            stubListing(19794, 24),
            stubListing(19793, 32),
            stubListing(19791, 48),
            stubListing(19790, 64),
            stubListing(19704, 8),
            stubListing(19750, 16),
            stubListing(19924, 48),
            stubListing(12156, 8),
            stubListing(76839, 56),
            stubListing(46747, 150)
        )
    }
}


fun printableCoins(coins: Int): String {
    val negative = coins < 0
    if (coins == 0)
        return "0c"

    fun ifNotZero(amount: Int, suffix: String) = if (amount > 0) "$amount$suffix" else ""
    var remaining = coins

    if (negative)
        remaining *= -1


    val gold = (remaining - (remaining % 10_000)) / 10_000
    remaining -= gold * 10_000
    val silver = (remaining - (remaining) % 100) / 100
    remaining -= silver * 100
    val copper = remaining

    val prefix = if (negative) "-" else ""

    return prefix + ifNotZero(gold, "g") + ifNotZero(silver, "s") + ifNotZero(copper, "c")
}

fun ingredientCost(recipe: Recipe?): Int {
    if (recipe == null)
        return Int.MAX_VALUE

    val amountById = recipe.ingredients.associate { it.itemId to it.amount }
    val listingById = repo.listings(recipe.ingredients.map { it.itemId }).associateBy { it.itemId }

    return if (amountById.keys.subtract(listingById.keys).isNotEmpty()) {
        Int.MAX_VALUE
    } else {
        recipe.ingredients.sumBy { ingredient ->
            amountById.getValue(ingredient.itemId) * listingById.getValue(ingredient.itemId).lowestSellOrder
        }
    }
}
