inline infix fun <T, R> T?.whenNotNull(block: (T) -> R?): R? {
    return when (this) {
        null -> null
        else -> block(this)
    }
}