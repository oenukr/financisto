package ru.orangesoftware.financisto.report;

import static ru.orangesoftware.financisto.db.DatabaseHelper.V_REPORT_CATEGORY;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import ru.orangesoftware.financisto.activity.ReportActivity;
import ru.orangesoftware.financisto.activity.ReportsListActivity;
import ru.orangesoftware.financisto.blotter.BlotterFilter;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.filter.Criteria;
import ru.orangesoftware.financisto.filter.WhereFilter;
import ru.orangesoftware.financisto.graph.GraphStyle;
import ru.orangesoftware.financisto.model.Category;
import ru.orangesoftware.financisto.model.Currency;

public class CategoryReport extends Report {
	
	public CategoryReport(Currency currency, boolean skipTransfers, float screenDensity) {
		super(ReportType.BY_CATEGORY, currency, skipTransfers, screenDensity);
	}

	@Override
	public ReportData getReport(DatabaseAdapter db, WhereFilter filter) {
		cleanupFilter(filter);
		filter.eq("parent_id", "0");
		return queryReport(db, V_REPORT_CATEGORY, filter);
	}

	@Override
	public Intent createActivityIntent(@NonNull Context context, @NonNull DatabaseAdapter db, WhereFilter parentFilter, long id) {
		WhereFilter filter = createFilterForSubCategory(db, parentFilter, id);
		Intent intent = new Intent(context, ReportActivity.class);
		filter.toIntent(intent);
		intent.putExtra(ReportsListActivity.EXTRA_REPORT_TYPE, ReportType.BY_SUB_CATEGORY.name());
		intent.putExtra(ReportActivity.FILTER_INCOME_EXPENSE, incomeExpense.name());
		return intent;
	}

	public WhereFilter createFilterForSubCategory(DatabaseAdapter db, WhereFilter parentFilter, long id) {
		WhereFilter filter = WhereFilter.empty();
		Criteria c = parentFilter.get(BlotterFilter.DATETIME);
		if (c != null) {
			filter.put(c);
		}
		c = parentFilter.get(BlotterFilter.CATEGORY_LEFT);
		if (c != null) {
			filter.put(c);
		}
		c = parentFilter.get(BlotterFilter.PROJECT_ID);
		if (c != null) {
			filter.put(c);
		}
		c = parentFilter.get(BlotterFilter.PAYEE_ID);
		if (c != null) {
			filter.put(c);
		}

		filterTransfers(filter);
		Category category = db.getCategoryWithParent(id);
		filter.put(Criteria.gte("left", String.valueOf(category.left)));
		filter.put(Criteria.lte("right", String.valueOf(category.right)));
		return filter;
	}

    @Override
	public Criteria getCriteriaForId(DatabaseAdapter db, long id) {
		Category c = db.getCategoryWithParent(id);
		return Criteria.btw(BlotterFilter.CATEGORY_LEFT, String.valueOf(c.left), String.valueOf(c.right));
	}
}
