import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


interface GuildWars2Repository {
    fun item(itemId: Int): Item?
    fun items(itemIds: List<Int>): List<Item>
    fun craftedItemsUsing(item: Item): List<Item>
    fun listing(item: Item): Listing?
    fun listings(itemIds: List<Int>): List<Listing>
}

class CachedRepository(
    private val db: DatabaseRepository,
    private val api: RateLimitedApiRepository
) : GuildWars2Repository {
    private val MAX_AGE = 120  // seconds

    override fun item(itemId: Int): Item? {
        return db.item(itemId) ?: api.item(itemId).also { item ->
            item.whenNotNull { db.storeItem(it) }
        }
    }

    override fun items(itemIds: List<Int>): List<Item> {
        val items = mutableListOf<Item>()
        val idsNotInDb = mutableListOf<Int>()

        itemIds.forEach { id ->
            db.item(id).whenNotNull { item ->
                items.add(item)
            } ?: idsNotInDb.add(id)
        }

        items.addAll(
            api.items(idsNotInDb).also { itemsFromApi ->
                itemsFromApi.forEach { item ->
                    db.storeItem(item)
                }
            }
        )

        return items.toList()
    }

    fun updateStubItems() {
        val sql = """
            SELECT
                id
            FROM
                Item
            WHERE
                name = 'TBD'
        """.trimIndent()

        val ids = with (db.connection.prepareStatement(sql)) {
            this.executeQuery().toList {
                it.getInt("id")
            }
        }

        if (ids.isNotEmpty()) {
            api.items(ids).forEach {
                db.storeItem(it)
            }
        }
    }

    override fun craftedItemsUsing(item: Item): List<Item> {
        val craftedItems = db.craftedItemsUsing(item)

        if (craftedItems.isEmpty()) {
            val apiCraftedItems = api.craftedItemsUsing(item)
            apiCraftedItems.forEach {
                db.storeItem(it)
            }

            return apiCraftedItems
        }

        return craftedItems
    }

    override fun listing(item: Item): Listing? {
        val dbListing = db.listing(item)
        return if (dbListing == null) {
            api.listing(item).also { listing ->
                listing.whenNotNull { db.storeListing(it) }
            }
        } else {
            if (listingTooOld(dbListing)) {
                api.listing(item).also { listing ->
                    listing.whenNotNull { db.storeListing(it) }
                }
            } else {
                dbListing
            }
        }
    }

    override fun listings(itemIds: List<Int>): List<Listing> {
        val dbListings = itemIds.mapNotNull { db.listing(Item(id = it, name = "whatever", recipe = null)) }
        val result = mutableListOf<Listing>()

        val missingListings = itemIds.subtract(dbListings.map { it.itemId })

        dbListings.forEach { dbListing ->
            if (listingTooOld(dbListing)) {
                val item = Item(dbListing.itemId, "dummy", null)
                api.listing(item).also { apiListing ->
                    apiListing.whenNotNull {
                        db.storeListing(it)
                        result.add(it)
                    }
                }
            } else {
                result.add(dbListing)
            }
        }

        missingListings.forEach { itemId ->
            val item = Item(itemId, "dummy", null)
            val apiListing = api.listing(item)
            apiListing.whenNotNull {
                db.storeListing(it)
                result.add(it)
            }
        }

        return result.toList()
    }

    private fun listingTooOld(listing: Listing): Boolean {
        val now = LocalDateTime.now()!!
        val age = ChronoUnit.SECONDS.between(listing.timestamp, now)

        return age > MAX_AGE
    }

}

class DatabaseRepository(
    path: String
) : GuildWars2Repository {
    val connection: Connection = DriverManager.getConnection(path)!!
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")!!

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

        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS
            Listing(
                pk INTEGER PRIMARY KEY,
                ts INTEGER DEFAULT CURRENT_TIMESTAMP,
                item_id INTEGER NOT NULL,
                highest_buy_order INTEGER NOT NULL,
                lowest_sell_order INTEGER NOT NULL,
                UNIQUE(item_id)
            );
            """.trimIndent()
        )
    }

    override fun item(itemId: Int): Item? {
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

    override fun items(itemIds: List<Int>): List<Item> {
        TODO("not implemented")
    }

    fun storeItem(item: Item): Int {
        val statement = connection.prepareStatement(
            """
            INSERT INTO
                Item (id, name)
            VALUES
                (?, ?)
            ON CONFLICT
                (id)
            DO UPDATE SET
                ts = CURRENT_TIMESTAMP,
                name = excluded.name
            WHERE
                excluded.name != "TBD"
        """.trimIndent()
        )

        statement.setInt(1, item.id)
        statement.setString(2, item.name)
        statement.executeUpdate()

        val itemRef = with(connection.prepareStatement("SELECT pk FROM Item WHERE id = ?")) {
            this.setInt(1, item.id)
            this.executeQuery()!!.getInt("pk")
        }

        item.recipe.whenNotNull {
            val recipeRef = with(
                connection.prepareStatement(
                    """
                    INSERT INTO
                        Recipe (output, ingredients_hash)
                    VALUES
                        (?, ?)
                    ON CONFLICT(output, ingredients_hash) DO NOTHING
                """.trimIndent()
                )
            ) {
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

    override fun craftedItemsUsing(item: Item): List<Item> {
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

    override fun listing(item: Item): Listing? {
        val statement = connection.prepareStatement(
            """
            SELECT
                listing.ts,
                listing.item_id,
                listing.highest_buy_order,
                listing.lowest_sell_order
            FROM
                Listing listing
            WHERE
                item_id = ?
        """.trimIndent()
        )!!

        statement.setInt(1, item.id)
        val rs = statement.executeQuery()!!

        return if (!rs.next()) {
            null
        } else {
            val ts = rs.getString("ts")
            Listing(
                timestamp = LocalDateTime.parse(ts, formatter),
                itemId = rs.getInt("item_id"),
                highestBuyOrder = rs.getInt("highest_buy_order"),
                lowestSellOrder = rs.getInt("lowest_sell_order")
            )
        }
    }

    override fun listings(itemIds: List<Int>): List<Listing> {
        TODO("not implemented")
    }

    fun storeListing(listing: Listing): Int {
        val statement = connection.prepareStatement(
            """
            INSERT INTO
                Listing (ts, item_id, highest_buy_order, lowest_sell_order)
            VALUES
                (?, ?, ?, ?)
            ON CONFLICT(item_id) DO UPDATE SET ts = excluded.ts
        """.trimIndent()
        )

        statement.setString(1, listing.timestamp.format(formatter))
        statement.setInt(2, listing.itemId)
        statement.setInt(3, listing.highestBuyOrder)
        statement.setInt(4, listing.lowestSellOrder)
        statement.executeUpdate()

        return with(connection.prepareStatement("SELECT pk FROM Listing WHERE item_id = ?")) {
            this.setInt(1, listing.itemId)
            this.executeQuery()!!.getInt("pk")
        }
    }

    fun close() {
        connection.close()
    }

}