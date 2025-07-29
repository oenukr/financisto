package ru.orangesoftware.financisto.repository.local

import android.content.Context
import ru.orangesoftware.financisto.repository.utils.Logger
import ru.orangesoftware.orb.EntityManager.Companion.DEF_SORT_COL
import kotlin.collections.map
import kotlin.enums.EnumEntries

fun <T : Enum<T>> EnumEntries<T>.toStringArray(): Array<String> = map { it.name }.toTypedArray()

class DatabaseHelper(context: Context, logger: Logger) : ru.orangesoftware.financisto.repository.local.DatabaseSchemaEvolution(
    context,
    Database.DATABASE_NAME,
    null,
    Database.DATABASE_VERSION,
    logger,
) {

    init {
        ru.orangesoftware.financisto.repository.local.DatabaseSchemaEvolution.setAutoDropViews = true
    }

    override fun getViewNameFromScriptName(scriptFileName: String): String? {
        val x: Int = scriptFileName.indexOf('[')
        val y: Int = scriptFileName.indexOf(']')
        return if (x != -1 && y != -1 && y - x > 1) {
            scriptFileName.substring(x + 1, y)
        } else null
    }

    companion object {
        const val TRANSACTION_TABLE = "transactions"
        const val ACCOUNT_TABLE = "account"
        const val CURRENCY_TABLE = "currency"
        const val CATEGORY_TABLE = "category"
        const val BUDGET_TABLE = "budget"
        const val PROJECT_TABLE = "project"
        const val ATTRIBUTES_TABLE = "attributes"
        const val SMS_TEMPLATES_TABLE = "sms_template"
        const val CATEGORY_ATTRIBUTE_TABLE = "category_attribute"
        const val TRANSACTION_ATTRIBUTE_TABLE = "transaction_attribute"
        const val LOCATIONS_TABLE = "locations"
        const val PAYEE_TABLE = "payee"
        const val CCARD_CLOSING_DATE_TABLE = "ccard_closing_date"
        const val EXCHANGE_RATES_TABLE = "currency_exchange_rate"

        const val V_ALL_TRANSACTIONS = "v_all_transactions"
        const val V_BLOTTER = "v_blotter"
        const val V_BLOTTER_FLAT_SPLITS = "v_blotter_flatsplits"
        const val V_BLOTTER_FOR_ACCOUNT_WITH_SPLITS = "v_blotter_for_account_with_splits"
        const val V_CATEGORY = "v_category"
        const val V_ATTRIBUTES = "v_attributes"
        const val V_REPORT_CATEGORY = "v_report_category"
        const val V_REPORT_SUB_CATEGORY = "v_report_sub_category"
        const val V_REPORT_PERIOD = "v_report_period"
        const val V_REPORT_LOCATIONS = "v_report_location"
        const val V_REPORT_PROJECTS = "v_report_project"
        const val V_REPORT_PAYEES = "v_report_payee"
    }

    enum class TransactionColumns {
        _id,
        parent_id,
        from_account_id,
        to_account_id,
        category_id,
        project_id,
        payee_id,
        note,
        from_amount,
        to_amount,
        datetime,
        original_currency_id,
        original_from_amount,
        location_id,
        provider,
        accuracy,
        latitude,
        longitude,
        is_template,
        template_name,
        recurrence,
        notification_options,
        status,
        attached_picture,
        is_ccard_payment,
        last_recurrence,
        blob_key;

        val NORMAL_PROJECTION: Array<String> = entries.toStringArray()
    }

    enum class BlotterColumns {
        _id,
        parent_id,
        from_account_id,
        from_account_title,
        from_account_currency_id,
        to_account_id,
        to_account_title,
        to_account_currency_id,
        category_id,
        category_title,
        category_left,
        category_right,
        category_type,
        project_id,
        project,
        location_id,
        location,
        payee_id,
        payee,
        note,
        from_amount,
        to_amount,
        datetime,
        original_currency_id,
        original_from_amount,
        is_template,
        template_name,
        recurrence,
        notification_options,
        status,
        is_ccard_payment,
        attached_picture,
        last_recurrence,
        from_account_balance,
        to_account_balance,
        is_transfer;

        val NORMAL_PROJECTION: Array<String> = entries.toStringArray()

        val BALANCE_PROJECTION: Array<String>
            get() = arrayOf(
                    from_account_currency_id.name,
                    "SUM($from_amount)",
            )

        val BALANCE_GROUP_BY = "FROM_ACCOUNT_CURRENCY_ID"
    }

    object AccountColumns {
        const val ID = "_id"
        const val TITLE = "title"
        const val CREATION_DATE = "creation_date"
        const val CURRENCY_ID = "currency_id"
        const val TYPE = "type"
        const val ISSUER = "issuer"
        const val NUMBER = "number"
        const val TOTAL_AMOUNT = "total_amount"
        const val SORT_ORDER = DEF_SORT_COL
        const val LAST_CATEGORY_ID = "last_category_id"
        const val LAST_ACCOUNT_ID = "last_account_id"
        const val CLOSING_DAY = "closing_day"
        const val PAYMENT_DAY = "payment_day"
        const val IS_INCLUDE_INTO_TOTALS = "is_include_into_totals"
        const val IS_ACTIVE = "is_active"
        const val LAST_TRANSACTION_DATE = "last_transaction_date"
    }

    enum class CategoryColumns {
        _id,
        title,
        left,
        right,
        type,
        last_location_id,
        last_project_id,
        sort_order
    }

    enum class CategoryViewColumns {
        _id,
        title,
        level,
        left,
        right,
        type,
        last_location_id,
        last_project_id,
        sort_order;

        val NORMAL_PROJECTION: Array<String> = entries.toStringArray()
    }

    enum class ExchangeRateColumns {
        from_currency_id,
        to_currency_id,
        rate_date,
        rate;

        val NORMAL_PROJECTION: Array<String> = entries.toStringArray()
        val LATEST_RATE_PROJECTION: Array<String>
            get() = arrayOf(
                from_currency_id.name,
                to_currency_id.name,
                "max($rate_date)",
                rate.name,
            )
        val DELETE_CLAUSE: String
            get() = buildString {
                append(from_currency_id)
                append("=? and ")
                append(to_currency_id)
                append("=? and ")
                append(rate_date)
                append("=?")
            }
        val LATEST_RATE_GROUP_BY: String
            get() = buildString {
                append(from_currency_id)
                append(",")
                append(to_currency_id)
            }
        val NORMAL_PROJECTION_WHERE: String
            get() = buildString {
                append(from_currency_id)
                append("=? and ")
                append(to_currency_id)
                append("=? and ")
                append(rate_date)
                append("=?")
            };
    }

    object EntityColumns {
        const val ID = "_id"
        const val TITLE = "title"
    }

    object AttributeColumns {

        const val  ID = "_id"
        const val  TITLE = "title"
        const val  TYPE = "type"
        const val  LIST_VALUES = "list_values"
        const val  DEFAULT_VALUE = "default_value"

        val NORMAL_PROJECTION: Array<String> = arrayOf(
                ID,
                TITLE,
                TYPE,
                LIST_VALUES,
                DEFAULT_VALUE,
        )

        object Indicies {
            const val  ID = 0
            const val  TITLE = 1
            const val  TYPE = 2
            const val  LIST_VALUES = 3
            const val  DEFAULT_VALUE = 4
        }

    }

    object AttributeViewColumns {

        const val TITLE = "title"
        const val CATEGORY_ID = "category_id"
        const val CATEGORY_LEFT = "category_left"
        const val CATEGORY_RIGHT = "category_right"

        val NORMAL_PROJECTION: Array<String> = arrayOf(
                CATEGORY_ID,
                TITLE,
        )

        object Indicies {
            const val CATEGORY_ID = 0
            const val NAME = 1
        }
    }

    object CategoryAttributeColumns {
        const val ATTRIBUTE_ID = "attribute_id"
        const val CATEGORY_ID = "category_id"
    }

    enum class SmsTemplateColumns {
        _id,
        title,
        template,
        category_id,
        account_id,
        is_income,
        sort_order;

        val NORMAL_PROJECTION: Array<String> = entries.toStringArray()
    }

    enum class SmsTemplateListColumns {
        cat_name,
        cat_level;
    }

    object TransactionAttributeColumns {
        const val ATTRIBUTE_ID = "attribute_id"
        const val TRANSACTION_ID = "transaction_id"
        const val VALUE = "value"

        val NORMAL_PROJECTION: Array<String> = arrayOf(
                ATTRIBUTE_ID,
                TRANSACTION_ID,
                VALUE,
        )

        object Indicies {
            const val ATTRIBUTE_ID = 0
            const val TRANSACTION_ID = 1
            const val VALUE = 2
        }
    }

    object ReportColumns {
        const val ID = "_id"
        const val NAME = "name"
        const val DATETIME = "datetime"
        const val FROM_ACCOUNT_CURRENCY_ID = "from_account_currency_id"
        const val FROM_AMOUNT = "from_amount"
        const val TO_ACCOUNT_CURRENCY_ID = "to_account_currency_id"
        const val TO_AMOUNT = "to_amount"
        const val ORIGINAL_CURRENCY_ID = "original_currency_id"
        const val ORIGINAL_FROM_AMOUNT = "original_from_amount"
        const val IS_TRANSFER = "is_transfer"

        val NORMAL_PROJECTION: Array<String> = arrayOf(
            ID,
            NAME,
            DATETIME,
            FROM_ACCOUNT_CURRENCY_ID,
            FROM_AMOUNT,
            TO_ACCOUNT_CURRENCY_ID,
            TO_AMOUNT,
            ORIGINAL_CURRENCY_ID,
            ORIGINAL_FROM_AMOUNT,
            IS_TRANSFER,
        )

    }
    object SubCategoryReportColumns {
        const val ID = "_id"
        const val NAME = "name"
        const val DATETIME = "datetime"
        const val FROM_ACCOUNT_CURRENCY_ID = "from_account_currency_id"
        const val FROM_AMOUNT = "from_amount"
        const val TO_ACCOUNT_CURRENCY_ID = "to_account_currency_id"
        const val TO_AMOUNT = "to_amount"
        const val ORIGINAL_CURRENCY_ID = "original_currency_id"
        const val ORIGINAL_FROM_AMOUNT = "original_from_amount"
        const val IS_TRANSFER = "is_transfer"
        const val LEFT = "left"
        const val RIGHT = "right"

        val NORMAL_PROJECTION: Array<String> = arrayOf(
            ID,
            NAME,
            DATETIME,
            FROM_ACCOUNT_CURRENCY_ID,
            FROM_AMOUNT,
            TO_ACCOUNT_CURRENCY_ID,
            TO_AMOUNT,
            ORIGINAL_CURRENCY_ID,
            ORIGINAL_FROM_AMOUNT,
            LEFT,
            RIGHT,
            IS_TRANSFER,
        )
    }


    object LocationColumns {
        const val ID = "_id"
        const val TITLE = "title"
        const val DATETIME = "datetime"
        const val PROVIDER = "provider"
        const val ACCURACY = "accuracy"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val IS_PAYEE = "is_payee"
        const val RESOLVED_ADDRESS = "resolved_address"
    }

    object CreditCardClosingDateColumns {

        const val ACCOUNT_ID = "account_id"
        // Period key in database (MMYYYY), where MM = 0 to 11
        const val PERIOD = "period"
        const val CLOSING_DAY = "closing_day"

    }

    object deleteLogColumns {
        const val TABLE_NAME = "table_name"
        const val DELETED_ON = "deleted_on"
    }

}
