import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDateTime


class StubJsonRetriever(
    private val json: String
) : JsonRetriever {
    override fun getJson(url: String) = json
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ApiTest {
    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun individualTestTearDown() {
    }

    @AfterAll
    fun tearDown() {
    }
}


internal class ApiRepositoryTest : ApiTest() {
    @Test
    fun `test single item`() {
        val json = """
            [
              {
                "name": "Mithril Ingot",
                "description": "Refined from Ore.",
                "type": "CraftingMaterial",
                "level": 0,
                "rarity": "Basic",
                "vendor_value": 8,
                "game_types": [
                  "Activity",
                  "Wvw",
                  "Dungeon",
                  "Pve"
                ],
                "flags": [],
                "restrictions": [],
                "id": 19684,
                "chat_link": "[&AgHkTAAA]",
                "icon": "https://render.guildwars2.com/file/7B0701F4092237431EDC72340BC89AA126EA4EF0/65913.png"
              }
            ]
        """.trimIndent()

        val retriever = StubJsonRetriever(json)
        val item = RateLimitedApiRepository(retriever).item(123)

        val expected = Item(
            id = 19684,
            name = "Mithril Ingot",
            recipe = null
        )
        assertThat(item).isEqualTo(expected)
    }

    @Test
    fun `test multiple items`() {
        val json = """
            [
              {
                "name": "Mithril Ingot",
                "description": "Refined from Ore.",
                "type": "CraftingMaterial",
                "level": 0,
                "rarity": "Basic",
                "vendor_value": 8,
                "game_types": [
                  "Activity",
                  "Wvw",
                  "Dungeon",
                  "Pve"
                ],
                "flags": [],
                "restrictions": [],
                "id": 19684,
                "chat_link": "[&AgHkTAAA]",
                "icon": "https://render.guildwars2.com/file/7B0701F4092237431EDC72340BC89AA126EA4EF0/65913.png"
              },
              {
                "name": "Orichalcum Ingot",
                "description": "Refined from Ore.",
                "type": "CraftingMaterial",
                "level": 0,
                "rarity": "Basic",
                "vendor_value": 8,
                "game_types": [
                  "Activity",
                  "Wvw",
                  "Dungeon",
                  "Pve"
                ],
                "flags": [],
                "restrictions": [],
                "id": 19685,
                "chat_link": "[&AgHlTAAA]",
                "icon": "https://render.guildwars2.com/file/D1941454313ACCB234906840E1FB401D49091B96/220460.png"
              }
            ]
        """.trimIndent()

        val retriever = StubJsonRetriever(json)
        val item = RateLimitedApiRepository(retriever).items(emptyList())

        val expected = listOf(
            Item(id = 19684, name = "Mithril Ingot", recipe = null),
            Item(id = 19685, name = "Orichalcum Ingot", recipe = null)
        )
        assertThat(item).isEqualTo(expected)
    }

    @Test
    fun `test single listing`() {
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

        val retriever = StubJsonRetriever(json)
        val result = RateLimitedApiRepository(retriever).listing(Item(1, "", null))

        val expected = Listing(
            timestamp = result!!.timestamp,
            itemId = 19684,
            highestBuyOrder = 166,
            lowestSellOrder = 168,
            static = false
        )

        assertThat(result).isEqualToComparingFieldByField(expected)
    }

    @Test
    fun `test multiple listings`() {
        val json = """
            [
              {
                "id": 19684,
                "buys": [
                  {
                    "listings": 1,
                    "unit_price": 171,
                    "quantity": 17
                  },
                  {
                    "listings": 10,
                    "unit_price": 170,
                    "quantity": 2496
                  }
                ],
                "sells": [
                  {
                    "listings": 5,
                    "unit_price": 204,
                    "quantity": 773
                  },
                  {
                    "listings": 9,
                    "unit_price": 205,
                    "quantity": 1403
                  }
                ]
              },
              {
                "id": 19685,
                "buys": [
                  {
                    "listings": 20,
                    "unit_price": 158,
                    "quantity": 4972
                  },
                  {
                    "listings": 14,
                    "unit_price": 157,
                    "quantity": 3250
                  }
                ],
                "sells": [
                  {
                    "listings": 1,
                    "unit_price": 205,
                    "quantity": 243
                  },
                  {
                    "listings": 1,
                    "unit_price": 206,
                    "quantity": 250
                  }
                ]
              }
            ]
        """.trimIndent()

        val retriever = StubJsonRetriever(json)
        val result = RateLimitedApiRepository(retriever).listings(listOf(1, 2))

        val expected = listOf(
            Listing(
                timestamp = LocalDateTime.now(), // ignored when comparing
                itemId = 19684,
                highestBuyOrder = 171,
                lowestSellOrder = 204,
                static = false
            ),
            Listing(
                timestamp = LocalDateTime.now(), // ignored when comparing
                itemId = 19685,
                highestBuyOrder = 158,
                lowestSellOrder = 205,
                static = false
            )
        )

        assertThat(result).isNotEmpty()

        result.forEachIndexed { index, listing ->
            assertThat(listing).isEqualToIgnoringGivenFields(expected[index], "timestamp")
        }
    }
}