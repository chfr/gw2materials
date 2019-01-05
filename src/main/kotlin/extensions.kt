import java.sql.ResultSet

inline infix fun <T, R> T?.whenNotNull(block: (T) -> R?): R? {
    return when (this) {
        null -> null
        else -> block(this)
    }
}

fun <T> ResultSet.toList(block: (ResultSet) -> T): List<T> {
    val output = mutableListOf<T>()
    while (this.next()) {
        output.add(block(this))
    }

    return output.toList()
}