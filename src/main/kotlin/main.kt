val db = DatabaseRepository("jdbc:sqlite:gw2materials.sqlite")
val api = ApiRepository()

fun main(args: Array<String>) {
    val repo = CachedRepository(db, api)

    val baseItem = repo.item(19684)!! // Mithril ingot

    val craftedItems = repo.craftedItemsUsing(baseItem, refreshFromApi = false)
    println("Got ${craftedItems.size} crafted items, querying listings")
    val baseItemListing = repo.listing(baseItem)!!

    println("${baseItem.name} sells for ${baseItemListing.highestBuyOrder} coins right now")

    val craftedItemListings = craftedItems.filter { craftedItem ->
        craftedItem.recipe!!.ingredients.size == 1
    }.map { craftedItem ->
        craftedItem to repo.listing(craftedItem)!!
    }.sortedByDescending { (craftedItem, listing) ->
        listing.highestBuyOrder / craftedItem.recipe!!.ingredients.first().amount
    }

    println("Base price per item: ${baseItemListing.highestBuyOrder} / ${baseItemListing.lowestSellOrder}")
    println()
    craftedItemListings.filter { (ci, listing) ->
        listing.withFees.highestBuyOrder / ci.recipe!!.ingredients.first().amount.toFloat() > baseItemListing.highestBuyOrder
    }.forEach { (craftedItem, listing) ->
        val pricePerBase = listing.withFees.highestBuyOrder / craftedItem.recipe!!.ingredients.first().amount.toFloat()
        val priceTotal = listing.withFees.highestBuyOrder
        val amountNeeded = craftedItem.recipe.ingredients.first().amount

        println("${craftedItem.name} sells for $pricePerBase (after fees) per base item, ($amountNeeded needed, $priceTotal total buy value)")
    }
}