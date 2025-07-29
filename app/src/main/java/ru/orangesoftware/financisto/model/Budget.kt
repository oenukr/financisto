package ru.orangesoftware.financisto.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.db.DatabaseHelper.BUDGET_TABLE
import ru.orangesoftware.financisto.utils.RecurUtils
import ru.orangesoftware.financisto.utils.RecurUtils.Recur
import ru.orangesoftware.orb.EntityManager.DEF_ID_COL
import ru.orangesoftware.orb.EntityManager.DEF_SORT_COL

@Entity(
	tableName = BUDGET_TABLE,
	foreignKeys = [
		ForeignKey(
			entity = Currency::class,
			parentColumns = [DEF_ID_COL],
			childColumns = ["budget_currency_id"],
			onDelete = ForeignKey.NO_ACTION
		),
		ForeignKey(
			entity = Account::class,
			parentColumns = [DEF_ID_COL],
			childColumns = ["budget_account_id"],
			onDelete = ForeignKey.NO_ACTION
		),
	]
)
data class Budget(
	@PrimaryKey
	@ColumnInfo(name = DEF_ID_COL) var id: Long = -1,
	@ColumnInfo(name = "title") var title: String,
	@ColumnInfo(name = "category_id") var categories: String,
	@ColumnInfo(name = "project_id") var projects: String,
	@ColumnInfo(name = "currency_id") val currencyId: Long = -1,
	// @JoinColumn(name = "budget_currency_id", required = false)
//	@ForeignKey(
//		entity = Currency::class,
//		parentColumns = [EntityManager.DEF_ID_COL],
//		childColumns = ["budget_currency_id"]
//	)
	@ColumnInfo(name = "budget_currency_id") var currency: Currency? = null,
	// @JoinColumn(name = "budget_account_id", required = false)
//	@ForeignKey(
//		entity = Account::class,
//		parentColumns = [EntityManager.DEF_ID_COL],
//		childColumns = ["budget_account_id"]
//	)
	@ColumnInfo(name = "budget_account_id") var account: Account? = null,
	@ColumnInfo(name = "amount") var amount: Long,
	@ColumnInfo(name = "include_subcategories") var includeSubcategories: Boolean,
	@ColumnInfo(name = "expanded") var expanded: Boolean,
	@ColumnInfo(name = "include_credit") var includeCredit: Boolean,
	@ColumnInfo(name = "start_date") var startDate: Long,
	@ColumnInfo(name = "end_date") var endDate: Long,
	@ColumnInfo(name = "recur") var recur: String,
	@ColumnInfo(name = "recur_num") var recurNum: Long,
	@ColumnInfo(name = "is_current") val isCurrent: Boolean,
	@ColumnInfo(name = "parent_budget_id") var parentBudgetId: Long,
	@ColumnInfo(name = "updated_on") val updatedOn: Long = System.currentTimeMillis(),
	@ColumnInfo(name = "remote_key") var remoteKey: String?,
	@ColumnInfo(name = DEF_SORT_COL) override val sortOrder: Long,
	@Transient
	@Ignore var categoriesText: String = "",
	@Transient
	@Ignore var projectsText: String = "",
	@Transient
	@Ignore var spent: Long = 0,
	@Transient
	@Ignore var updated: Boolean = false,
) : SortableEntity {
	
	fun getTheRecur(): Recur = RecurUtils.createFromExtraString(recur)

	fun getBudgetCurrency(): Currency? {
		return currency ?: account?.currency
	}

	companion object {
		@JvmStatic
		fun createWhere(
			budget: Budget,
			categories: Map<Long, Category>,
			projects: Map<Long, Project>,
		): String = buildString {
			// currency
			if (budget.currency != null) {
				append(BlotterFilter.FROM_ACCOUNT_CURRENCY_ID).append("=")
					.append((budget.currency as MyEntity).id)
			} else if (budget.account != null) {
				append(BlotterFilter.FROM_ACCOUNT_ID).append("=").append((budget.account as MyEntity).id)
			} else {
				append(" 1=1 ")
			}
			val categoriesWhere = createCategoriesWhere(budget, categories)
			val hasCategories = !categoriesWhere.isNullOrEmpty()
			val projectWhere = createProjectsWhere(budget, projects)
			val hasProjects = !projectWhere.isNullOrEmpty()
			if (hasCategories && hasProjects) {
				append(" AND ((").append(categoriesWhere).append(") ")
				append(if (budget.expanded) "OR" else "AND")
				append(" (").append(projectWhere).append("))")
			} else if (hasCategories) {
				append(" AND (").append(categoriesWhere).append(")")
			} else if (hasProjects) {
				append(" AND (").append(projectWhere).append(")")
			}
			// start date
			if (budget.startDate > 0) {
				append(" AND ").append(BlotterFilter.DATETIME).append(">=").append(budget.startDate)
			}
			// end date
			if (budget.endDate > 0) {
				append(" AND ").append(BlotterFilter.DATETIME).append("<=").append(budget.endDate)
			}
			if (!budget.includeCredit) {
				append(" AND from_amount<0")
			}
		}

		@JvmStatic
		private fun createCategoriesWhere(
			budget: Budget,
			categories: Map<Long, Category>,
		): String? {
			val ids = MyEntity.splitIds(budget.categories) ?: return null

			var found = false
			val string = buildString {
				for (id in ids) {
					val category = categories[id]
					if (category != null) {
						if (found) {
							append(" OR ")
						}
						if (budget.includeSubcategories) {
							append("(")
							append(BlotterFilter.CATEGORY_LEFT)
							append(" BETWEEN ")
							append(category.left)
							append(" AND ")
							append(category.right)
							append(")")
						} else {
							append(BlotterFilter.CATEGORY_ID)
							append("=")
							append(category.id)
						}
						found = true
					}
				}
			}
			if (found) {
				return string
			}
			return null
		}

		@JvmStatic
		private fun createProjectsWhere(
			budget: Budget,
			projects: Map<Long, Project>,
		): String? {
			val ids = MyEntity.splitIds(budget.projects) ?: return null

			var found = false
			val string = buildString {
				for (id in ids) {
					val project = projects[id]
					if (project != null) {
						if (found) {
							append(" OR ")
						}
						append(BlotterFilter.PROJECT_ID)
						append("=")
						append(project.id)
						found = true
					}
				}
			}
			if (found) {
				return string
			}
			return null
		}
	}
}
