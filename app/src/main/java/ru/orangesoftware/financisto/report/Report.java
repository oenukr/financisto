package ru.orangesoftware.financisto.report;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import ru.orangesoftware.financisto.activity.BlotterActivity;
import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.blotter.BlotterFilter;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.db.DatabaseHelper;
import ru.orangesoftware.financisto.db.DatabaseHelper.ReportColumns;
import ru.orangesoftware.financisto.db.TransactionsTotalCalculator;
import ru.orangesoftware.financisto.db.UnableToCalculateRateException;
import ru.orangesoftware.financisto.filter.Criteria;
import ru.orangesoftware.financisto.filter.WhereFilter;
import ru.orangesoftware.financisto.graph.Amount;
import ru.orangesoftware.financisto.graph.GraphStyle;
import ru.orangesoftware.financisto.graph.GraphUnit;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.model.Total;
import ru.orangesoftware.financisto.model.TotalError;
import ru.orangesoftware.financisto.rates.ExchangeRateProvider;
import ru.orangesoftware.financisto.utils.Logger;

public abstract class Report {

    private final Logger logger = new DependenciesHolder().getLogger();
	
	public final GraphStyle style;
    public final ReportType reportType;
	
    protected final boolean skipTransfers;
    protected final Currency currency;

    protected IncomeExpense incomeExpense = IncomeExpense.BOTH;

    public Report(ReportType reportType, Currency currency, boolean skipTransfers, float screenDensity) {
        this.reportType = reportType;
        this.skipTransfers = skipTransfers;
        this.style = new GraphStyle.Builder(screenDensity).build();
        this.currency = currency;
    }

    public void setIncomeExpense(IncomeExpense incomeExpense) {
        this.incomeExpense = incomeExpense;
    }

    @NonNull
    protected String alterName(long id, @Nullable String name) {
		return name != null ? name : "";
	}

    public abstract ReportData getReport(DatabaseAdapter db, WhereFilter filter);

    public ReportData getReportForChart(DatabaseAdapter db, WhereFilter filter) {
        return getReport(db, filter);
    }

	protected ReportData queryReport(DatabaseAdapter db, String table, WhereFilter filter) {
		filterTransfers(filter);
		Cursor c = db.db().query(table, DatabaseHelper.ReportColumns.NORMAL_PROJECTION,
                filter.getSelection(), filter.getSelectionArgs(), null, null, "_id");
		ArrayList<GraphUnit> units = getUnitsFromCursor(db, c);
        Total total = calculateTotal(units);
        return new ReportData(units, total);
	}

    protected void filterTransfers(WhereFilter filter) {
		if (skipTransfers) {
			filter.put(Criteria.eq(ReportColumns.IS_TRANSFER, "0"));
		}
	}

    @NonNull
    protected ArrayList<GraphUnit> getUnitsFromCursor(DatabaseAdapter db, Cursor c) {
        try (c) {
            ExchangeRateProvider rates = db.getHistoryRates();
            ArrayList<GraphUnit> units = new ArrayList<>();
            GraphUnit u = null;
            long lastId = -1;
            while (c.moveToNext()) {
                long id = getId(c);
                long isTransfer = c.getLong(c.getColumnIndex(ReportColumns.IS_TRANSFER));
                if (id != lastId) {
                    if (u != null) {
                        units.add(u);
                    }
                    String name = c.getString(c.getColumnIndex(ReportColumns.NAME));
                    u = new GraphUnit(id, alterName(id, name), currency, style);
                    lastId = id;
                }
                BigDecimal amount;
                try {
                    amount = TransactionsTotalCalculator.getAmountFromCursor(db, c, currency, rates, c.getColumnIndex(ReportColumns.DATETIME));
                } catch (UnableToCalculateRateException e) {
                    amount = BigDecimal.ZERO;
                    u.error = TotalError.atDateRateError(e.getFromCurrency(), e.getDatetime());
                }
                u.addAmount(amount, skipTransfers && isTransfer != 0);
            }
            if (u != null) {
                units.add(u);
            }
            for (GraphUnit unit : units) {
                unit.flatten(incomeExpense);
            }
            removeEmptyUnits(units);
            Collections.sort(units);
            return units;
        } catch (Exception e) {
            logger.e(e, "Error: ");
            return new ArrayList<>();
        }
    }

    private void removeEmptyUnits(ArrayList<GraphUnit> units) {
        Iterator<GraphUnit> unit = units.iterator();
        while (unit.hasNext()) {
            GraphUnit u = unit.next();
            if (u.maxAmount == 0) {
                unit.remove();
            }
        }
    }

    protected Total calculateTotal(List<? extends GraphUnit> units) {
        Total total = new Total(currency, true);
        for (GraphUnit u : units) {
            for (Amount a : u) {
                if (u.error != null) {
                    return new Total(currency, u.error);
                }
                long amount = a.getAmount();
                if (amount > 0) {
                    total.amount += amount;
                } else {
                    total.balance += amount;
                }
            }
        }
        return total;
    }

	protected long getId(Cursor c) {
		return c.getLong(0);
	}

	public Intent createActivityIntent(@NonNull Context context, @NonNull DatabaseAdapter db, @Nullable WhereFilter parentFilter, long id) {
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
        c = getCriteriaForId(db, id);
        if (c != null) {
            filter.put(c);
        }
        filter.eq("from_account_is_include_into_totals", "1");
		Intent intent = new Intent(context, getBlotterActivityClass());
		filter.toIntent(intent);
		return intent;
	}

    protected abstract Criteria getCriteriaForId(@NonNull DatabaseAdapter db, long id);

    protected Class<? extends BlotterActivity> getBlotterActivityClass() {
        return BlotterActivity.class;
    }

    protected void cleanupFilter(WhereFilter filter) {
        // fixing a bug with saving incorrect filter fot this report have to remove it here
        filter.remove("left");
        filter.remove("right");
    }

    public boolean shouldDisplayTotal() {
        return true;
    }

}
