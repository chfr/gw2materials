data class CraftedItem(
    val item: Item,
    val recipe: Recipe
)

data class Recipe(
    val ingredients: List<Ingredient>
)

data class Ingredient(
    val itemId: Int,
    val amount: Int
)

