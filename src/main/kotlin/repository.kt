import java.sql.Connection
import java.sql.DriverManager

interface GuildWars2ApiRepository {
    fun item(itemId: Int): Item?
    fun craftedItemsUsing(item: Item, refreshFromApi: Boolean): List<Item>
    fun listing(item: Item): Listing?
}

class CachedRepository(
    private val db: DatabaseRepository,
    private val api: ApiRepository
) : GuildWars2ApiRepository {
    private val MAX_AGE = 120  // seconds

    override fun item(itemId: Int): Item? {
        return db.item(itemId) ?: api.item(itemId).also { item ->
            item.whenNotNull { db.storeItem(it) }
        }
    }

    override fun craftedItemsUsing(item: Item, refreshFromApi: Boolean): List<Item> {
        if (refreshFromApi) {
            val craftedItems = api.craftedItemsUsing(item)
            craftedItems.forEach {
                db.storeItem(it)
            }
            return craftedItems
        }
        return db.craftedItemsUsing(item)
    }

    override fun listing(item: Item): Listing? {
        return api.listing(item)
    }
}

class DatabaseRepository(
    path: String
) {
    val connection: Connection = DriverManager.getConnection(path)!!

    init {
        createTables()
    }

    private fun createTables() {
        val statement = connection.createStatement()
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS
            Item (
                pk INTEGER PRIMARY KEY,
                ts INTEGER DEFAULT CURRENT_TIMESTAMP,
                id INTEGER UNIQUE NOT NULL,
                name TEXT NOT NULL
            );
            """.trimIndent()
        )

        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS Ingredient(
                pk INTEGER PRIMARY KEY,
                recipe_ref INTEGER NOT NULL,
                item_ref INTEGER NOT NULL,
                count INTEGER NOT NULL,
                UNIQUE(recipe_ref, item_ref)
                FOREIGN KEY(item_ref) REFERENCES Item(pk),
                FOREIGN KEY(recipe_ref) REFERENCES Recipe(pk)
            );
            """.trimIndent()
        )

        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS
            Recipe(
                pk INTEGER PRIMARY KEY,
                output INTEGER NOT NULL,
                ingredients_hash INTEGER NOT NULL,
                UNIQUE(output, ingredients_hash)
                FOREIGN KEY(output) REFERENCES Item(pk)
            );
            """.trimIndent()
        )
    }

    fun item(itemId: Int): Item? {
        val statement = connection.prepareStatement(
            """
            SELECT
                item.id,
                item.name
            FROM
                Item item
            WHERE
                item.id = ?
        """.trimIndent()
        )!!

        statement.setInt(1, itemId)
        val rs = statement.executeQuery()!!

        return if (!rs.next()) {
            null
        } else {
            Item(
                id = rs.getInt("id"),
                name = rs.getString("name"),
                recipe = null
            )
        }
    }

    fun storeItem(item: Item): Int {
        val statement = connection.prepareStatement(
            """
            INSERT INTO
                Item (id, name)
            VALUES
                (?, ?)
            ON CONFLICT(id) DO UPDATE SET ts = CURRENT_TIMESTAMP, name = excluded.name
        """.trimIndent()
        )

        statement.setInt(1, item.id)
        statement.setString(2, item.name)
        statement.executeUpdate()

        val itemRef = with(connection.prepareStatement("SELECT pk FROM Item WHERE id = ?")) {
            this.setInt(1, item.id)
            this.executeQuery()!!.getInt("pk")
        }

        item.recipe.whenNotNull { recipe ->
            val recipeRef = with(connection.prepareStatement(
                """
                    INSERT INTO
                        Recipe (output, ingredients_hash)
                    VALUES
                        (?, ?)
                    ON CONFLICT(output, ingredients_hash) DO NOTHING
                """.trimIndent()
            )) {
                this.setInt(1, itemRef)
                this.setString(2, item.recipe!!.ingredientsHash)
                this.executeUpdate()

                with(connection.prepareStatement("SELECT pk FROM Recipe ORDER BY pk DESC LIMIT 1")) {
                    this.executeQuery()!!.getInt("pk")
                }
            }

            val refAndCount = item.recipe!!.ingredients.map { ingredient ->
                Pair(storeItem(Item(ingredient.itemId, "TBD", null)), ingredient.amount)
            }

            val sql = """
                INSERT INTO
                    Ingredient (recipe_ref, item_ref, count)
                VALUES
                    (?, ?, ?)
                ON CONFLICT (recipe_ref, item_ref) DO NOTHING
            """.trimIndent()

            with(connection.prepareStatement(sql)) {
                refAndCount.map { (ref, count) ->
                    this.setInt(1, recipeRef)
                    this.setInt(2, ref)
                    this.setInt(3, count)
                    this.executeUpdate()
                }
            }
        }

        return itemRef
    }

    fun craftedItemsUsing(item: Item): List<Item> {
        data class Row(
            val craftedId: Int,
            val craftedName: String,
            val ingredientId: Int,
            val ingredientName: String,
            val ingredientCount: Int
        )

        val statement = connection.prepareStatement(
            """
            SELECT
                output.id as 'crafted_id',
                output.name as 'crafted_name',
                ingredient_item.id as 'ingredient_id',
                ingredient_item.name as 'ingredient_name',
                ingredient.count as 'count'
            FROM
                Recipe recipe
                JOIN Ingredient ingredient ON ingredient.recipe_ref = recipe.pk
                JOIN Item ingredient_item ON ingredient.item_ref = ingredient_item.pk
                JOIN Item output ON recipe.output = output.pk
            WHERE
                recipe.pk IN (
                    SELECT
                        recipe.pk
                    FROM
                        Ingredient ingredient
                        JOIN Item ingredient_item ON ingredient.item_ref = ingredient_item.pk
                        JOIN Recipe recipe ON ingredient.recipe_ref = recipe.pk
                    WHERE
                        ingredient_item.id = ?
                )
        """.trimIndent()
        )!!

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
            Item(
                id = it.first().craftedId,
                name = it.first().craftedName,
                recipe = Recipe(
                    ingredients = it.map { row ->
                        Ingredient(itemId = row.ingredientId, amount = row.ingredientCount)
                    }
                )
            )
        }
    }



    fun close() {
        connection.close()
    }

}