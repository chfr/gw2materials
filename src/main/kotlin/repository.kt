import java.sql.Connection
import java.sql.DriverManager

interface GuildWars2ApiRepository {
    fun item(itemId: Int): Item?
    fun craftedItemsUsing(item: Item): List<CraftedItem>
    fun listing(item: Item): Listing?
}

class CachedRepository: GuildWars2ApiRepository {
    private val MAX_AGE = 120  // seconds

    override fun item(itemId: Int): Item? {
        return db.item(itemId) ?: api.item(itemId).also { item ->
            item.whenNotNull { db.storeItem(it) }
        }
    }

    override fun craftedItemsUsing(item: Item): List<CraftedItem> {
        return api.craftedItemsUsing(item)
    }

    override fun listing(item: Item): Listing? {
        return api.listing(item)
    }

    companion object {
        private val db = DatabaseRepository()
        private val api = ApiRepository()
    }
}

class DatabaseRepository {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:gw2materials.sqlite")!!

    init {
        createTables()
    }

    private fun createTables() {
        val statement = connection.createStatement()
        statement.execute("""
            CREATE TABLE IF NOT EXISTS
            Item (
                pk INTEGER PRIMARY KEY AUTOINCREMENT,
                id INTEGER NOT NULL UNIQUE,
                name VARCHAR NOT NULL,
                ts INTEGER DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )

        statement.execute("""
            CREATE TABLE IF NOT EXISTS
            Recipe (
                pk INTEGER PRIMARY KEY AUTOINCREMENT
            )
            """.trimIndent()
        )

        statement.execute("""
            CREATE TABLE IF NOT EXISTS
            Ingredient (
                pk INTEGER PRIMARY KEY AUTOINCREMENT,
                item_ref INTEGER NOT NULL,
                recipe_ref INTEGER NOT NULL,
                count INTEGER NOT NULL,
                FOREIGN KEY(item_ref) REFERENCES Item(pk),
                FOREIGN KEY(recipe_ref) REFERENCES Recipe(pk)
            )
            """.trimIndent()
        )

        statement.execute("""
            CREATE TABLE IF NOT EXISTS
            CraftedItem (
                pk INTEGER PRIMARY KEY AUTOINCREMENT,
                item_ref INTEGER NOT NULL,
                recipe_ref INTEGER NOT NULL,
                FOREIGN KEY(item_ref) REFERENCES Item(pk),
                FOREIGN KEY(recipe_ref) REFERENCES Recipe(pk)
            )
            """.trimIndent()
        )
    }

    fun item(itemId: Int): Item? {
        val statement = connection.prepareStatement("""
            SELECT
                item.id,
                item.name
            FROM
                Item item
            WHERE
                item.id = ?
        """.trimIndent())!!

        statement.setInt(1, itemId)
        val rs = statement.executeQuery()!!

        return if (!rs.next()) {
            null
        } else {
            Item(
                id = rs.getInt("id"),
                name = rs.getString("name")
            )
        }
    }

    fun storeItem(item: Item) {
        val statement =  connection.prepareStatement("""
            INSERT INTO
                Item (id, name)
            VALUES
                (?, ?)
            ON CONFLICT(id) DO UPDATE SET ts = CURRENT_TIMESTAMP
        """.trimIndent())

        statement.setInt(1, item.id)
        statement.setString(2, item.name)
        statement.executeUpdate()
    }

//    fun craftedItemsUsing(item: Item): List<CraftedItem> {
//        val statement = connection.prepareStatement("""
//            SELECT
//                *
//            FROM
//                CraftedItem ci
//                JOIN Recipe recipe ON ci.recipe_ref = recipe.pk
//                JOIN Ingredient ingredient ON ingredient.recipe_ref = recipe.pk
//                JOIN Item ingredient_item ON ingredient.item_ref = ingredient_item.pk
//                JOIN Item crafted_item ON ci.item_ref = crafted_item.pk
//            WHERE
//                ingredient_item.id = ?
//        """.trimIndent())!!
//
//        statement.setInt(1, item.id)
//        val rs = statement.executeQuery()
//
//        var result = mutableListOf<CraftedItem>()
//        while (rs.next()) {
//
//        }
//    }
}