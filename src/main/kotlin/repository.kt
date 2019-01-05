import java.sql.Connection
import java.sql.DriverManager

interface GuildWars2ApiRepository {
    fun item(itemId: Int): Item?
    fun craftedItemsUsing(item: Item, refreshFromApi: Boolean): List<CraftedItem>
    fun listing(item: Item): Listing?
}

class CachedRepository(
    private val db: DatabaseRepository,
    private val api: ApiRepository
): GuildWars2ApiRepository {
    private val MAX_AGE = 120  // seconds

    override fun item(itemId: Int): Item? {
        return db.item(itemId) ?: api.item(itemId).also { item ->
            item.whenNotNull { db.storeItem(it) }
        }
    }

    override fun craftedItemsUsing(item: Item, refreshFromApi: Boolean): List<CraftedItem> {
        if (refreshFromApi) {
            val craftedItems = api.craftedItemsUsing(item)
            craftedItems.forEach {
                db.storeCraftedItem(it)
            }
            return craftedItems
        }
        return db.craftedItemsUsing(item)
    }

    override fun listing(item: Item): Listing? {
        return api.listing(item)
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
            ON CONFLICT(id) DO UPDATE SET ts = CURRENT_TIMESTAMP, name = excluded.name
        """.trimIndent())

        statement.setInt(1, item.id)
        statement.setString(2, item.name)
        statement.executeUpdate()
    }

    fun craftedItemsUsing(item: Item): List<CraftedItem> {
        data class Row(
            val craftedId: Int,
            val craftedName: String,
            val ingredientId: Int,
            val ingredientName: String,
            val ingredientCount: Int
        )

        val statement = connection.prepareStatement("""
            SELECT
                c_item.id as 'crafted_id',
                c_item.name as 'crafted_name',
                i_item.id as 'ingredient_id',
                i_item.name as 'ingredient_name',
                ingredient.count as 'count'
            FROM
                Recipe recipe
                JOIN CraftedItem crafted_item ON crafted_item.recipe_ref = recipe.pk
                JOIN Ingredient ingredient ON ingredient.recipe_ref = recipe.pk
                JOIN Item i_item ON i_item.pk = ingredient.item_ref
                JOIN Item c_item ON c_item.pk = crafted_item.item_ref
            WHERE
                recipe.pk IN
                (
                SELECT
                    recipe.pk
                FROM
                    Recipe recipe
                    JOIN CraftedItem crafted_item ON crafted_item.recipe_ref = recipe.pk
                    JOIN Ingredient ingredient ON ingredient.recipe_ref = recipe.pk
                    JOIN Item item ON item.pk = ingredient.item_ref
                WHERE
                    item.id = ?
                )
        """.trimIndent())!!

        statement.setInt(1, item.id)
        val rs = statement.executeQuery()

        val rows = rs.toList {
            Row(
                craftedId = it.getInt("crafted_id"),
                craftedName = it.getString("crafted_name"),
                ingredientId = it.getInt("ingredient_id"),
                ingredientName = it.getString("ingredient_name"),
                ingredientCount = it.getInt("count")
            )
        }

        val ingredientsByCraftedItem = rows.groupBy {
            it.craftedId
        }

        return ingredientsByCraftedItem.values.map {
            CraftedItem(
                Item(
                    id = it.first().craftedId,
                    name = it.first().craftedName
                ),
                recipe = Recipe(
                    ingredients = it.map { row ->
                        Ingredient(itemId = row.ingredientId, amount = row.ingredientCount)
                    }
                )
            )
        }
    }

    fun storeCraftedItem(craftedItem: CraftedItem) {
        craftedItem.recipe.ingredients.filter {
            item(it.itemId) == null
        }.forEach {
            storeItem(Item(it.itemId, "TBD"))
        }

        val recipeKey = createRecipe()
        craftedItem.recipe.ingredients.forEach {
            createIngredient(recipeKey, it.itemId, it.amount)
        }

        item(craftedItem.item.id) ?: storeItem(craftedItem.item)
        createCraftedItem(craftedItem.item.id, recipeKey)
    }



    private fun createRecipe(): Int {
        var statement =  connection.prepareStatement("INSERT INTO Recipe (pk) VALUES (null)")
        statement.executeUpdate()
        statement = connection.prepareStatement("SELECT pk FROM Recipe ORDER BY pk DESC LIMIT 1")
        return statement.executeQuery()!!.getInt("pk")
    }

    private fun createIngredient(recipeKey: Int, itemId: Int, amount: Int) {
        val statement =  connection.prepareStatement("""
            INSERT INTO
                Ingredient (
                    recipe_ref,
                    item_ref,
                    count
                )
                SELECT
                    ?,
                    item.pk,
                    ?
                FROM
                    Item item
                WHERE
                    item.id = ?
            """.trimIndent()
        )
        statement.setInt(1, recipeKey)
        statement.setInt(2, amount)
        statement.setInt(3, itemId)
        statement.executeUpdate()
    }

    private fun createCraftedItem(itemId: Int, recipeKey: Int) {
        val statement = connection.prepareStatement("""
            INSERT INTO CraftedItem (
                item_ref, recipe_ref
            )
            SELECT
                item.pk,
                ?
            FROM
                Item item
            WHERE
                item.id = ?
        """.trimIndent())
        statement.setInt(1, recipeKey)
        statement.setInt(2, itemId)
        statement.executeUpdate()
    }
}