import com.beust.klaxon.Klaxon

data class Item(
    val id: Int,
    val name: String,
    val recipe: Recipe?
)

class JsonItem(
    val id: Int,
    val name: String,
    val recipe: Recipe? = null
) {
    fun toModel() = Item(
        id = this.id,
        name = this.name,
        recipe = this.recipe
    )
}

const val ITEM_BASE_URL = "https://api.guildwars2.com/v2/items?ids=ITEM_ID&lang=en"

