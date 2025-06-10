package ru.orangesoftware.financisto.shared.model

interface EntityEnum : LocalizableEnum {
    // val iconId: Int // Android specific (R.drawable)
    val iconIdPlaceholder: String // Placeholder for icon resource ID
}

interface LocalizableEnum {
    // val titleId: Int // Android specific (R.string)
    val titleIdPlaceholder: String // Placeholder for title resource ID
    // fun getTitle(context: Context): String // Android specific
}
