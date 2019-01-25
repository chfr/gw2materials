val db = DatabaseRepository("jdbc:sqlite:gw2materials.sqlite")
val api = RateLimitedApiRepository(NetworkJsonRetriever())
val repo = CachedRepository(db, api)

fun main(args: Array<String>) {
    val ids = listOf(
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
    )
    ids.forEach { id ->
        val item = repo.item(id)
        item.whenNotNull {
            checkProfitability(it)
        }
    }

    println("Updating stub items...")
    repo.updateStubItems()
}

fun checkProfitability(item: Item) {
    val craftedItems = repo.craftedItemsUsing(item)
    val itemListing = repo.listing(item)!!
    println("Base price per ${item.name}: ${itemListing.highestBuyOrder} / ${itemListing.lowestSellOrder}")

    val craftedItemListings = craftedItems.filter { craftedItem ->
        craftedItem.recipe!!.ingredients.size == 1
    }
    val listings = repo.listings(craftedItemListings.map { it.id }).associateBy { it.itemId }
    val listingByItem = craftedItemListings.associate { it to listings[it.id] }

    listingByItem.filter { (_, listing) ->
        listing != null
    }.filter { (ci, listing) ->
        listing!!.withFees().highestBuyOrder / ci.recipe!!.ingredients.first().amount.toFloat() > itemListing.highestBuyOrder
    }.forEach { (craftedItem, listing) ->
        val pricePerBase = listing!!.withFees().highestBuyOrder / craftedItem.recipe!!.ingredients.first().amount.toFloat()
        val priceTotal = listing.withFees().highestBuyOrder
        val amountNeeded = craftedItem.recipe.ingredients.first().amount

        println("${craftedItem.name} sells for $pricePerBase (after fees) per base item, ($amountNeeded needed, $priceTotal total buy value)")
    }
}