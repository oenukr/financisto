package ru.orangesoftware.financisto.blotter

import ru.orangesoftware.financisto.db.DatabaseHelper.BlotterColumns

object BlotterFilter {
	@JvmField
	val FROM_ACCOUNT_ID: String = BlotterColumns.from_account_id.name
	@JvmField
	val FROM_ACCOUNT_CURRENCY_ID: String = BlotterColumns.from_account_currency_id.name
	@JvmField
	val CATEGORY_ID: String = BlotterColumns.category_id.name
	@JvmField
	val CATEGORY_LEFT: String = BlotterColumns.category_left.name
	@JvmField
	val CATEGORY_NAME: String = BlotterColumns.category_title.name
	@JvmField
	val LOCATION_ID: String = BlotterColumns.location_id.name
	@JvmField
	val PROJECT_ID: String = BlotterColumns.project_id.name
	@JvmField
	val PAYEE_ID: String = BlotterColumns.payee_id.name
	@JvmField
	val NOTE: String = BlotterColumns.note.name
	@JvmField
	val TEMPLATE_NAME: String = BlotterColumns.template_name.name
	@JvmField
	val DATETIME: String = BlotterColumns.datetime.name
	const val BUDGET_ID: String = "budget_id"
	@JvmField
	val IS_TEMPLATE: String = BlotterColumns.is_template.name
	@JvmField
	val PARENT_ID: String = BlotterColumns.parent_id.name
	@JvmField
	val STATUS: String = BlotterColumns.status.name

	@JvmField
	val SORT_NEWER_TO_OLDER: String = "${BlotterColumns.datetime} desc"
	@JvmField
	val SORT_OLDER_TO_NEWER: String = "${BlotterColumns.datetime} asc"

    const val SORT_NEWER_TO_OLDER_BY_ID: String = "_id desc"
    const val SORT_OLDER_TO_NEWER_BY_ID: String = "_id asc"

	@JvmField
    val SORT_BY_TEMPLATE_NAME: String = "${BlotterColumns.template_name} asc"
	@JvmField
	val SORT_BY_ACCOUNT_NAME: String = "${BlotterColumns.from_account_title} asc"
}
