import com.beust.klaxon.Klaxon

open class Item(
    open val id: Int,
    open val name: String
)

class JsonItem(
    val id: Int,
    val name: String
) {
    fun toModel() = Item(
        id = this.id,
        name = this.name
    )
}

const val ITEM_BASE_URL = "https://api.guildwars2.com/v2/items?ids=ITEM_ID&lang=en"

