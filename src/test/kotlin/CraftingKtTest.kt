import com.beust.klaxon.Klaxon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CraftingKtTest {

    @Test
    fun parseRecipeJson() {
        val json = """
            {
                  "type": "RefinementEctoplasm",
                  "output_item_id": 46742,
                  "output_item_count": 1,
                  "min_rating": 450,
                  "time_to_craft_ms": 5000,
                  "disciplines": [
                    "Leatherworker",
                    "Armorsmith",
                    "Tailor",
                    "Artificer",
                    "Weaponsmith",
                    "Huntsman"
                  ],
                  "flags": [
                    "AutoLearned"
                  ],
                  "ingredients": [
                    {
                      "item_id": 19684,
                      "count": 50
                    },
                    {
                      "item_id": 19721,
                      "count": 1
                    },
                    {
                      "item_id": 46747,
                      "count": 10
                    }
                  ],
                  "id": 7319,
                  "chat_link": "[&CZccAAA=]"
                }
        """.trimIndent()

        val result = Klaxon().converter(CraftedItemConverter()).parse<CraftedItem>(json)!!
        val expected = JsonCraftedItem(
            id = 46742,
            name = "",
            recipe = Recipe(
                ingredients = listOf(
                    Ingredient(itemId = 19684, amount = 50),
                    Ingredient(itemId = 19721, amount = 1),
                    Ingredient(itemId = 46747, amount = 10)
                )
            )
        )

        assertThat(result).isEqualToComparingFieldByField(expected)

    }
}