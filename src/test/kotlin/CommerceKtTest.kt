import com.beust.klaxon.Klaxon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CommerceKtTest {

    @Test
    fun listingsFromJson() {
        val json = """
            {
              "id": 19684,
              "buys": [
                {
                  "listings": 2,
                  "unit_price": 166,
                  "quantity": 378
                },
                {
                  "listings": 1,
                  "unit_price": 127,
                  "quantity": 239
                },
                {
                  "listings": 45,
                  "unit_price": 125,
                  "quantity": 11075
                }
              ],
              "sells": [
                {
                  "listings": 1,
                  "unit_price": 168,
                  "quantity": 250
                },
                {
                  "listings": 7,
                  "unit_price": 169,
                  "quantity": 1654
                },
                {
                  "listings": 24,
                  "unit_price": 170,
                  "quantity": 4924
                }
              ]
            }
        """.trimIndent()

        val result = Klaxon().converter(ListingConverter()).parse<Listing>(json)!!
        val expected = Listing(
            timestamp = result.timestamp,
            itemId = 19684,
            highestBuyOrder = 166,
            lowestSellOrder = 168
        )

        assertThat(result).isEqualToComparingFieldByField(expected)
    }

    @Test
    fun printableCoins() {
        data class Case(val coins: Int, val expected: String)

        val cases = listOf(
            Case(0, "0c"),
            Case(1, "1c"),
            Case(100, "1s"),
            Case(101, "1s1c"),
            Case(10000, "1g"),
            Case(10001, "1g1c"),
            Case(10101, "1g1s1c"),
            Case(-0, "0c"),
            Case(-1, "-1c"),
            Case(-100, "-1s"),
            Case(-101, "-1s1c"),
            Case(-10000, "-1g"),
            Case(-10001, "-1g1c"),
            Case(-10101, "-1g1s1c")
        )

        cases.forEach { case ->
            assertThat(
                printableCoins(case.coins)
            ).describedAs(
                "${case.coins} coins should be formatted as ${case.expected}"
            ).isEqualTo(
                case.expected
            )
        }
    }
}