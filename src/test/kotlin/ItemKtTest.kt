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

        val item = Klaxon().parseArray<JsonItem>(json)?.first()!!
        val expected = Item(id=1, name="itemId name")

        assertThat(item).isEqualToComparingFieldByField(expected)
    }
}