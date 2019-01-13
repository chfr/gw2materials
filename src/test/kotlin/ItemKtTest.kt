import com.beust.klaxon.Klaxon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ItemKtTest {

    @Test
    fun getByJson() {
        val json = """
            {
                "id": 1,
                "name": "itemId name"
            }
        """.trimIndent()

        val item = Klaxon().parse<JsonItem>(json)!!
        val expected = Item(id=1, name="itemId name", recipe = null)

        assertThat(item).isEqualToComparingFieldByField(expected)
    }
}