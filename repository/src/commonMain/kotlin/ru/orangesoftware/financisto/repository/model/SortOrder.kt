package ru.orangesoftware.financisto.repository.model

enum class AccountSortOrder(val property: String, val asc: Boolean) {
    SORT_ORDER_ASC("sortOrder", true),
    SORT_ORDER_DESC("sortOrder", false),
    NAME("title", true),
    LAST_TRANSACTION_ASC("lastTransactionDate", true),
    LAST_TRANSACTION_DESC("lastTransactionDate", false);
}

enum class LocationsSortOrder(val property: String, val asc: Boolean) {
    FREQUENCY("count", false),
    TITLE("title", true)
}

enum class TemplatesSortOrder(val property: String, val asc: Boolean) {
    DATE("datetime", false),
    NAME("template_name", true),
    ACCOUNT("from_account", true)
}

enum class BudgetsSortOrder(val property: String, val asc: Boolean) {
    DATE("startDate", false),
    NAME("title", true),
    AMOUNT("amount", false)
}
