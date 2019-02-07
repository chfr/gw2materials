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

fun ingredientCost(recipe: Recipe?) = when (recipe) {
    null -> Int.MAX_VALUE
    else -> recipe.ingredients.sumBy { ingredient ->
        val costPer = repo.listing(Item(ingredient.itemId, "", null))?.lowestSellOrder
        when (costPer) {
            null -> Int.MAX_VALUE
            else -> ingredient.amount * costPer
        }
    }
}