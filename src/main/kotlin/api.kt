import com.beust.klaxon.Converter
import com.beust.klaxon.JsonObject
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import java.io.FileNotFoundException
import java.net.URL
import java.time.LocalDateTime

const val LISTING_BASE_URL = "https://api.guildwars2.com/v2/commerce/listings/ITEM_ID"
const val RECIPES_BASE_URL = "https://api.guildwars2.com/v2/recipes/search?input=ITEM_ID"
const val RECIPE_BASE_URL = "https://api.guildwars2.com/v2/recipes/ITEM_ID"

private var REQUEST_COUNT = 0

fun getJson(endpoint: String): String {
    REQUEST_COUNT += 1

    if (REQUEST_COUNT % 5 == 0)
        println("Issued $REQUEST_COUNT requests...")

    return URL(endpoint).readText()
}

class ApiRepository {
    fun item(itemId: Int): Item {
        val url = ITEM_BASE_URL.replace("ITEM_ID", itemId.toString())
        return Klaxon().parseArray<JsonItem>(getJson(url))?.first()!!.toModel()
    }

    fun craftedItemsUsing(item: Item): List<Item> {
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

    fun listing(item: Item): Listing? {
        val url = LISTING_BASE_URL.replace("ITEM_ID", item.id.toString())
        return try {
            Klaxon().converter(ListingConverter()).parse<Listing>(getJson(url))!!
        } catch (e: FileNotFoundException) {
            Listing(timestamp = LocalDateTime.now(), itemId = item.id, lowestSellOrder = 0, highestBuyOrder = 0)
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
            lowestSellOrder = sell
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