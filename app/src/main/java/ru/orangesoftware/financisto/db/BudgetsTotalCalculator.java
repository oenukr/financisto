/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.db;

import android.os.Handler;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.model.Budget;
import ru.orangesoftware.financisto.model.Category;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.model.MyEntity;
import ru.orangesoftware.financisto.model.Project;
import ru.orangesoftware.financisto.model.Total;
import ru.orangesoftware.financisto.model.TotalError;
import ru.orangesoftware.financisto.rates.ExchangeRate;
import ru.orangesoftware.financisto.rates.ExchangeRateProvider;
import ru.orangesoftware.financisto.utils.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: solomonk
 * Date: 4/28/12
 * Time: 2:33 AM
 */
public class BudgetsTotalCalculator {

    private final Logger logger = new DependenciesHolder().getLogger();

    private final DatabaseAdapter db;
    private final List<Budget> budgets;

    public BudgetsTotalCalculator(DatabaseAdapter db, List<Budget> budgets) {
        this.db = db;
        this.budgets = budgets;
    }

    public void updateBudgets(Handler handler) {
        long t0 = System.currentTimeMillis();
        try {
            Map<Long, Category> categories = MyEntity.asMap(db.getCategoriesList(true));
            Map<Long, Project> projects = MyEntity.asMap(db.getAllProjectsList(true));
            for (final Budget budget : budgets) {
                final long spent = db.fetchBudgetBalance(categories, projects, budget);
                final String categoriesText = getChecked(categories, budget.getCategories());
                final String projectsText = getChecked(projects, budget.getProjects());
                budget.setSpent(spent);
                if (handler != null) {
                    handler.post(() -> {
                        budget.setUpdated(true);
                        budget.setSpent(spent);
                        budget.setCategoriesText(categoriesText);
                        budget.setProjectsText(projectsText);
                    });
                }
            }
        } finally {
            long t1 = System.currentTimeMillis();
            logger.d("BUDGET UPDATE", (t1 - t0) + "ms");
        }
    }
    
    public Total[] calculateTotals() {
        Map<Currency, Total> totals = new HashMap<>();
        for (Budget b : budgets) {
            Currency c = b.getBudgetCurrency();
            Total total = totals.get(c);
            if (total == null) {
                total = new Total(c, true);
                totals.put(c, total);
            }
            total.amount += b.getSpent();
            total.balance += b.getAmount() + b.getSpent();
        }
        Collection<Total> values = totals.values();
        return values.toArray(new Total[0]);
    }

    public Total calculateTotalInHomeCurrency() {
        long t0 = System.currentTimeMillis();
        try {
            BigDecimal amount = BigDecimal.ZERO;
            BigDecimal balance = BigDecimal.ZERO;
            ExchangeRateProvider rates = db.getLatestRates();
            Currency homeCurrency = db.getHomeCurrency();
            for (Budget b : budgets) {
                Currency currency = b.getBudgetCurrency();
                ExchangeRate r = rates.getRate(currency, homeCurrency);
                if (r == ExchangeRate.NA) {
                    return new Total(homeCurrency, TotalError.lastRateError(currency));
                } else {
                    amount = amount.add(convert(r, b.getSpent()));
                    balance = balance.add(convert(r, b.getAmount() + b.getSpent()));
                }
            }
            Total total = new Total(homeCurrency, true);
            total.amount = amount.longValue();
            total.balance = balance.longValue();
            return total;
        } finally {
            long t1 = System.currentTimeMillis();
            logger.d("BUDGET TOTALS", (t1 - t0) + "ms");
        }
    }

    private BigDecimal convert(ExchangeRate r, long spent) {
        return BigDecimal.valueOf(r.rate * spent);
    }

    private <T extends MyEntity> String getChecked(Map<Long, T> entities, String s) {
        Long[] ids = MyEntity.splitIds(s);
        if (ids == null) {
            return null;
        }
        if (ids.length == 1) {
            MyEntity e = entities.get(ids[0]);
            if (e == null) {
                return null;
            } else {
                return e.getTitle();
            }
        } else {
            StringBuilder sb = new StringBuilder();
            for (long id : ids) {
                MyEntity e = entities.get(id);
                if (e != null) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append(e.getTitle());
                }
            }
            if (sb.length() == 0) {
                return null;
            } else {
                return sb.toString();
            }
        }
    }


}
