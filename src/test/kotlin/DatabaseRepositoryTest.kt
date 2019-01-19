import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DatabaseTest {
    private val dbPath = "jdbc:sqlite:testdb.sqlite"
    val db by lazy {
        DatabaseRepository(dbPath)
    }

    @BeforeEach
    fun setUp() {
        db.connection.setSavepoint()
    }

    @AfterEach
    fun individualTestTearDown() {
        db.connection.rollback()
    }

    @AfterAll
    fun tearDown() {
        db.close()
        val dbPath = Paths.get("").toAbsolutePath().resolve(dbPath.split(":").last()).toString()
        val deleted = File(dbPath).delete()
        assertThat(deleted).describedAs("Database deletion successful").isTrue()
    }

    fun rows(countQuery: String) = with(db.connection.prepareStatement(countQuery)) {
        assertThat(
            countQuery.replace(Regex("[\t\n ]+"), " ").toLowerCase()
        ).describedAs(
            "countQuery must start with SELECT COUNT"
        ).startsWith("select count")

        val rs = this.executeQuery()
        rs.getInt(1)
    }
}

internal class DatabaseRepositoryTest : DatabaseTest() {
    @Test
    fun `test item without recipe`() {
        val item = Item(
            id = 123,
            name = "whatever",
            recipe = null
        )

        assertThat(db.item(item.id)).isNull()

        db.storeItem(item)

        assertThat(db.item(item.id)).isEqualToComparingFieldByField(item)
        assertThat(rows("SELECT COUNT(*) FROM Item")).isEqualTo(1)
    }

    @Test
    fun `test item with recipe`() {
        val item = Item(
            id = 123,
            name = "whatever",
            recipe = Recipe(
                ingredients = listOf(
                    Ingredient(234, 2),
                    Ingredient(345, 3)
                )
            )
        )

        assertThat(db.item(item.id)).isNull()

        db.storeItem(item)

        assertThat(db.item(item.id)).isEqualToIgnoringGivenFields(item, "recipe")

        val craftedItemsUsing = db.craftedItemsUsing(Item(234, "whatever", null))
        assertThat(craftedItemsUsing).hasSize(1)
        val craftedItem = craftedItemsUsing.first()
        assertThat(craftedItem).isEqualToComparingFieldByField(item)

        assertThat(rows("SELECT COUNT(*) FROM Item")).isEqualTo(3)
        assertThat(rows("SELECT COUNT(*) FROM Recipe")).isEqualTo(1)
        assertThat(rows("SELECT COUNT(*) FROM Ingredient")).isEqualTo(2)
    }

    @Test
    fun `test storing the same item doesn't create a new one`() {
        val item = Item(
            id = 123,
            name = "whatever",
            recipe = null
        )

        assertThat(db.item(item.id)).isNull()

        val firstRef = db.storeItem(item)
        assertThat(db.item(item.id)).isEqualToComparingFieldByField(item)
        val secondRef = db.storeItem(item)
        assertThat(db.item(item.id)).isEqualToComparingFieldByField(item)

        assertThat(firstRef).isEqualTo(secondRef)
    }

    @Test
    fun `test item name TBD does not overwrite proper name`() {
        val item = Item(
            id = 123,
            name = "whatever",
            recipe = null
        )

        assertThat(db.item(item.id)).isNull()

        db.storeItem(item)

        assertThat(db.item(item.id)).isEqualToComparingFieldByField(item)
        assertThat(rows("SELECT COUNT(*) FROM Item")).isEqualTo(1)

        val newItem = item.copy(name = "TBD")
        db.storeItem(newItem)

        assertThat(db.item(item.id)).isEqualToComparingFieldByField(item)
        assertThat(rows("SELECT COUNT(*) FROM Item")).isEqualTo(1)
    }

    @Test
    fun `test multiple recipes`() {
        val firstItem = Item(1, "ingredient 1", null)
        val secondItem = Item(2, "ingredient 2", null)
        val thirdItem = Item(3, "ingredient 3", null)

        val firstCraftedItem = Item(
            4,
            "crafted 1",
            Recipe(
                listOf(
                    Ingredient(firstItem.id, 1337),
                    Ingredient(secondItem.id, 666)
                )
            )
        )

        val secondCraftedItem = Item(
            5,
            "crafted 2",
            Recipe(
                listOf(
                    Ingredient(secondItem.id, 11),
                    Ingredient(thirdItem.id, 22)
                )
            )
        )

        db.storeItem(firstCraftedItem)
        assertThat(rows("SELECT COUNT(*) FROM Item")).isEqualTo(3)
        assertThat(rows("SELECT COUNT(*) FROM Recipe")).isEqualTo(1)
        assertThat(rows("SELECT COUNT(*) FROM Ingredient")).isEqualTo(2)

        db.storeItem(secondCraftedItem)
        assertThat(rows("SELECT COUNT(*) FROM Item")).isEqualTo(5)
        assertThat(rows("SELECT COUNT(*) FROM Recipe")).isEqualTo(2)
        assertThat(rows("SELECT COUNT(*) FROM Ingredient")).isEqualTo(4)
    }

    @Test
    fun `test non-existent listing`() {
        val item = Item(123, "whatever name", null)

        val actual = db.listing(item)
        assertThat(actual).isEqualTo(null)
    }

    @Test
    fun `test stored listing`() {
        val item = Item(123, "whatever name", null)
        val ts = LocalDateTime.of(2019, 4, 2, 13, 37, 0)
        val expected = Listing(ts, item.id, 1337, 666)

        db.storeListing(expected)

        val actual = db.listing(item)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `test updating stored listing`() {
        val firstTimestamp = LocalDateTime.of(2019, 4, 2, 13, 37, 0)
        val secondTimestamp = LocalDateTime.of(2019, 4, 3, 13, 37, 0)
        val item = Item(123, "whatever name", null)
        val first = Listing(firstTimestamp, item.id, 1337, 666)

        db.storeListing(first)

        val firstFromDb = db.listing(item)!!
        assertThat(firstFromDb).isEqualTo(first)

        val second = Listing(secondTimestamp, item.id, 1337, 666)

        db.storeListing(second)

        val secondFromDb = db.listing(item)!!
        assertThat(secondFromDb).isEqualTo(second)

        assertThat(secondFromDb.timestamp).isAfter(firstFromDb.timestamp)
    }
}