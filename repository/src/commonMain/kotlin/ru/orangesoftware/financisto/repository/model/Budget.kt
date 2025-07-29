package ru.orangesoftware.financisto.repository.model;

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.PrimaryKey
import ru.orangesoftware.financisto.db.DatabaseHelper.BUDGET_TABLE
import ru.orangesoftware.orb.EntityManager.DEF_ID_COL
import ru.orangesoftware.orb.EntityManager.DEF_SORT_COL

import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.utils.RecurUtils
import ru.orangesoftware.financisto.utils.RecurUtils.Recur
import ru.orangesoftware.orb.EntityManager

@Entity(tableName = BUDGET_TABLE)
data class Budget(
	@PrimaryKey
	@ColumnInfo(name = DEF_ID_COL) val id: Long = -1,
	@ColumnInfo(name = "title") val title: String,
	@ColumnInfo(name = "category_id") val categories: String,
	@ColumnInfo(name = "project_id") val projects: String,
	@ColumnInfo(name = "currency_id") val currencyId: Long = -1,
	// @JoinColumn(name = "budget_currency_id", required = false)
	@ForeignKey(
		entity = Currency::class,
		parentColumns = [EntityManager.DEF_ID_COL],
		childColumns = ["budget_currency_id"]
	)
	@ColumnInfo(name = "budget_currency_id") val currency: Currency? = null,
	// @JoinColumn(name = "budget_account_id", required = false)
	@ForeignKey(
		entity = Account::class,
		parentColumns = [EntityManager.DEF_ID_COL],
		childColumns = ["budget_account_id"]
	)
	@ColumnInfo(name = "budget_account_id") val account: Account? = null,
	@ColumnInfo(name = "amount") val amount: Long,
	@ColumnInfo(name = "include_subcategories") val includeSubcategories: Boolean,
	@ColumnInfo(name = "expanded") val expanded: Boolean,
	@ColumnInfo(name = "include_credit") val includeCredit: Boolean,
	@ColumnInfo(name = "start_date") val startDate: Long,
	@ColumnInfo(name = "end_date") val endDate: Long,
	@ColumnInfo(name = "recur") val recur: String,
	@ColumnInfo(name = "recur_num") val recurNum: Long,
	@ColumnInfo(name = "is_current") val isCurrent: Boolean,
	@ColumnInfo(name = "parent_budget_id") val parentBudgetId: Long,
	@ColumnInfo(name = "updated_on") val updatedOn: Long = System.currentTimeMillis(),
	@ColumnInfo(name = "remote_key") val remoteKey: String?,
	@ColumnInfo(name = DEF_SORT_COL) override val sortOrder: Long,
	@Transient
	@Ignore val categoriesText: String = "",
	@Transient
	@Ignore val projectsText: String = "",
	@Transient
	@Ignore val spent: Long = 0,
	@Transient
	@Ignore val updated: Boolean = false,
) : SortableEntity {

	fun getRecur(): Recur = RecurUtils.createFromExtraString(recur)

	fun getBudgetCurrency(): Currency? {
		return currency ?: account?.currency
	}

	companion object {
		fun createWhere(
			budget: Budget,
			categories: Map<Long, Category>,
			projects: Map<Long, Project>,
		): String = buildString {
			// currency
			if (budget.currency != null) {
				append(BlotterFilter.FROM_ACCOUNT_CURRENCY_ID).append("=")
					.append(budget.currency.id)
			} else if (budget.account != null) {
				append(BlotterFilter.FROM_ACCOUNT_ID).append("=").append(budget.account.id)
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
