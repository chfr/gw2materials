import com.beust.klaxon.Converter
import com.beust.klaxon.JsonObject
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import java.io.FileNotFoundException
import java.net.URL
import java.time.LocalDateTime

const val LISTING_BASE_URL = "https://api.guildwars2.com/v2/commerce/listings/ITEM_ID"
const val LISTINGS_BASE_URL = "https://api.guildwars2.com/v2/commerce/listings?ids=ITEM_IDS"
const val RECIPES_BASE_URL = "https://api.guildwars2.com/v2/recipes/search?input=ITEM_ID"
const val RECIPE_BASE_URL = "https://api.guildwars2.com/v2/recipes/ITEM_ID"

interface JsonRetriever {
    fun getJson(url: String): String
}

class NetworkJsonRetriever : JsonRetriever {
    override fun getJson(url: String): String {
        return URL(url).readText()
    }
}

class RateLimitedApiRepository(
    private val retriever: JsonRetriever
) : GuildWars2Repository {
    private val maxRequestsPerMinute = 600
    private val requestHistory = mutableListOf<LocalDateTime>()

    private fun getJson(endpoint: String): String {
        return if (canRequestFromApi()) {
            requestHistory.add(LocalDateTime.now())
            retriever.getJson(endpoint)
        } else {
            println("Hit API rate limit (-10 % safety margin), waiting to request... ")
            Thread.sleep(61 * 1000)
            println("Resuming requests after waiting")
            getJson(endpoint)
        }
    }

    private fun canRequestFromApi(): Boolean {
        val now = LocalDateTime.now()
        val startTime = now.minusMinutes(1)
        val requestsInLastMinute = requestHistory.filter { it > startTime }.size

        return requestsInLastMinute < (maxRequestsPerMinute * 0.9) // 10% safety margin
    }

    override fun item(itemId: Int): Item {
        val url = ITEM_BASE_URL.replace("ITEM_ID", itemId.toString())
        return Klaxon().parseArray<JsonItem>(getJson(url))?.first()!!.toModel()
    }

    override fun items(itemIds: List<Int>): List<Item> {
        if (itemIds.isEmpty())
            return emptyList()
        return itemIds.chunked(200).flatMap { ids ->
            val url = ITEM_BASE_URL.replace("ITEM_ID", ids.joinToString(","))
            Klaxon().parseArray<JsonItem>(getJson(url))?.map { it.toModel() } ?: emptyList()
        }
    }

    override fun craftedItemsUsing(item: Item): List<Item> {
        val url = RECIPES_BASE_URL.replace("ITEM_ID", item.id.toString())
        val json = getJson(url)

        val recipeIds = Klaxon().parseArray<Int>(json)!!

        val result = recipeIds.map { recipeId ->
            val recipeUrl = RECIPE_BASE_URL.replace("ITEM_ID", recipeId.toString())
            val recipeJson = getJson(recipeUrl)

            Klaxon().converter(CraftedItemConverter()).parse<Item>(recipeJson)!!
        }

        return result
    }

    override fun listing(item: Item): Listing? {
        val url = LISTING_BASE_URL.replace("ITEM_ID", item.id.toString())
        return try {
            Klaxon().converter(ListingConverter()).parse<Listing>(getJson(url))!!
        } catch (e: FileNotFoundException) {
            Listing(timestamp = LocalDateTime.now(), itemId = item.id, lowestSellOrder = 0, highestBuyOrder = 0, static = false)
        }
    }

    override fun listings(itemIds: List<Int>): List<Listing> {
        if (itemIds.isEmpty())
            return emptyList()
        val url = LISTINGS_BASE_URL.replace("ITEM_IDS", itemIds.joinToString(","))
        return try {
            Klaxon().converter(ListingConverter()).parseArray(getJson(url))!!
        } catch (e: FileNotFoundException) {
            emptyList()
        }
    }
}

class ListingConverter : Converter {
    override fun canConvert(cls: Class<*>) = cls == Listing::class.java

    override fun fromJson(jv: JsonValue): Any? {
        if (jv.type != JsonObject::class.java) return null

        val obj = jv.obj as JsonObject

        val buy = obj.array<JsonObject>("buys")?.firstOrNull()?.int("unit_price") ?: 0
        val sell = obj.array<JsonObject>("sells")?.firstOrNull()?.int("unit_price") ?: 0

        return Listing(
            timestamp = LocalDateTime.now(),
            itemId = obj.int("id")!!,
            highestBuyOrder = buy,
            lowestSellOrder = sell,
            static = false
        )
    }

    override fun toJson(value: Any): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}


class CraftedItemConverter : Converter {
    override fun canConvert(cls: Class<*>) = cls == Item::class.java

    override fun fromJson(jv: JsonValue): Any? {
        if (jv.type != JsonObject::class.java) return null

        val obj = jv.obj as JsonObject
        val ingredients = obj.array<JsonObject>("ingredients")!!.map {
            Ingredient(
                itemId = it.int("item_id")!!,
                amount = it.int("count")!!
            )
        }

        val itemId = jv.objInt("output_item_id")
        return CachedRepository(db, api).item(itemId).whenNotNull {
            it.copy(
                recipe = Recipe(ingredients)
            )
        } ?: Item(
            id = itemId,
            name = "don't fucking know",
            recipe = Recipe(ingredients)
        )
    }

    override fun toJson(value: Any): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}