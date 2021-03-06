import java.time.temporal.ChronoUnit

val db = DatabaseRepository("jdbc:sqlite:gw2materials.sqlite")
val api = RateLimitedApiRepository(NetworkJsonRetriever())
val repo = CachedRepository(db, api)

val disallowedNamePrefixes = listOf(
    "Box of",
    "Satchel of"
)

fun main(args: Array<String>) {
    println("Creating static items...")
    createStatics()

    val ids = listOf(
//        19680 // Copper ingot
        19680, // Copper ingot
        19683, // Iron ingot
        19687, // Silver ingot
        19682, // Gold ingot
        19686, // Platinum ingot
        19684, // Mithril ingot
        19685, // Orichalcum ingot

        19720, // Bolt of Jute
        19740, // Bolt of Wool
        19742, // Bolt of Cotton
        19744, // Bolt of Linen
        19747, // Bolt of Silk

        19738, // Stretched Rawhide Leather Square
        19733, // Cured Thin Leather Square
        19734, // Cured Coarse Leather Square
        19736, // Cured Rugged Leather Square
        19735, // Cured Thick Leather Square
        19737, // Cured Hardened Leather Square

        19710, // Green Wood Plank
        19713, // Soft Wood Plank
        19714, // Seasoned Wood Plank
        19711, // Hard Wood Plank
        19709, // Elder Wood Plank
        19712 // Ancient Wood Plank
//        24356 // Large Fang
    )
    ids.forEach { id ->
        val item = repo.item(id)
        item.whenNotNull {
            checkProfitability(it)
        }
    }

    println("Updating stub items...")
    repo.updateStubItems()
    val timeSpent = ChronoUnit.SECONDS.between(repo.apiRequests().first(), repo.apiRequests().last())
    val rate = repo.apiRequests().size / timeSpent.toFloat()
    println("Issued ${repo.apiRequests().size} requests in $timeSpent seconds ($rate req/s)")
}

fun createStatics() {
    Listing.statics.forEach { stubListing ->
        val item = repo.item(stubListing.itemId)
        item.whenNotNull {
            db.storeListing(stubListing)
        }
    }
}

fun checkProfitability(item: Item) {
    val craftedItems = repo.craftedItemsUsing(item).filterNot { craftedItem ->
        disallowedNamePrefixes.any { prefix ->
            craftedItem.name.startsWith(prefix)
        }
    }
    val itemListing = repo.listing(item)!!
    println("Base price per ${item.name}: ${itemListing.highestBuyOrder} / ${itemListing.lowestSellOrder}")

    val craftedItemListings = craftedItems.filter { craftedItem ->
        craftedItem.recipe!!.ingredients.size <= 4
    }
    val listingByCraftedItemId = repo.listings(craftedItemListings.map { it.id }).associateBy { it.itemId }
    val listingByItem = craftedItemListings.associate { it to listingByCraftedItemId[it.id] }

    listingByItem.keys.filter {
        it.recipe != null
    }.flatMap {
        it.recipe!!.ingredients
    }.map {
        it.itemId
    }.run {
        // "pre-load" ingredient listings
        repo.listings(this)
    }

    listingByItem.filter { (_, listing) ->
        listing != null
    }.map { (ci, listing) ->
        ci to listing!!.withFees()
    }.filter { (ci, listing) ->
        ingredientCost(ci.recipe) < listing.highestBuyOrder
    }.forEach { (craftedItem, listing) ->
        val ingredientCost = ingredientCost(craftedItem.recipe)
        val profit = printableCoins(listing.highestBuyOrder - ingredientCost)
        println("\t ${craftedItem.name} sells for ${listing.highestBuyOrder}, ingredients cost $ingredientCost. Profit: $profit")
        craftedItem.recipe!!.ingredients.forEach { ingredient ->
            val ingredientItem = repo.item(ingredient.itemId) ?: Item(ingredient.itemId, "Ingredient ${ingredient.itemId}", null)
            val ingredientPrice = repo.listing(ingredientItem)?.lowestSellOrder ?: Int.MAX_VALUE
            println("\t\t${ingredientItem.name} x${ingredient.amount} costs $ingredientPrice each (${ingredientPrice * ingredient.amount} total)")
        }
    }
}
