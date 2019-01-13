data class Recipe(
    val ingredients: List<Ingredient>
) {
    val ingredientsHash by lazy {
        this.ingredients.sortedBy { it.itemId }.joinToString(separator = "|") { "${it.itemId},${it.amount}" }
    }
}

data class Ingredient(
    val itemId: Int,
    val amount: Int
)

