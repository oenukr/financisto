/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.report;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.utils.EntityEnum;

/**
 * Created by IntelliJ IDEA.
 * User: solomonk
 * Date: 5/12/12
 * Time: 1:40 AM
 */
public enum IncomeExpense implements EntityEnum {

    BOTH(R.string.report_income_expense_both, R.drawable.ic_menu_report_both),
    EXPENSE(R.string.report_income_expense_expense, R.drawable.ic_menu_report_expense),
    INCOME(R.string.report_income_expense_income, R.drawable.ic_menu_report_income),
    SUMMARY(R.string.report_income_expense_summary, R.drawable.ic_menu_report_summary);

    @StringRes
    private final int titleId;
    @DrawableRes
    private final int iconId;

    IncomeExpense(@StringRes int titleId, @DrawableRes int iconId) {
        this.titleId = titleId;
        this.iconId = iconId;
    }

    @Override
    @StringRes
    public int getTitleId() {
        return titleId;
    }

    @Override
    @DrawableRes
    public int getIconId() {
        return iconId;
    }

    @NonNull
    @Override
    public String getName() {
        return name();
    }
}
