/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.datetime;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.utils.LocalizableEnum;

/**
 * Created by IntelliJ IDEA.
 * User: denis.solonenko
 * Date: 12/17/12 9:08 PM
 */
public enum PeriodType implements LocalizableEnum {
    TODAY(R.string.period_today, "Today", true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.TODAY, start, end);
        }
    },
    YESTERDAY(R.string.period_yesterday, "Yesterday", true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.YESTERDAY, start, end);
        }
    },
    THIS_WEEK(R.string.period_this_week, "This week", true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            long start, end;
            if (dayOfWeek != Calendar.MONDAY) {
                c.add(Calendar.DAY_OF_MONTH, -(dayOfWeek == Calendar.SUNDAY ? 6 : dayOfWeek - 2));
            }
            start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 6);
            end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.THIS_WEEK, start, end);
        }
    },
    THIS_MONTH(R.string.period_this_month, "This month", true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.set(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.MONTH, 1);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.THIS_MONTH, start, end);
        }
    },
    LAST_WEEK(R.string.period_last_week, "Last week", true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.DAY_OF_YEAR, -7);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            long start, end;
            if (dayOfWeek != Calendar.MONDAY) {
                c.add(Calendar.DAY_OF_MONTH, -(dayOfWeek == Calendar.SUNDAY ? 6 : dayOfWeek - 2));
            }
            start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 6);
            end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.LAST_WEEK, start, end);
        }
    },
    LAST_MONTH(R.string.period_last_month, "Last month", true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.MONTH, -1);
            c.set(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.MONTH, 1);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.LAST_MONTH, start, end);
        }
    },
    THIS_AND_LAST_WEEK(R.string.period_this_and_last_week, "This and last week", true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Period lastWeek = LAST_WEEK.calculatePeriod(refTime);
            Period thisWeek = THIS_WEEK.calculatePeriod(refTime);
            return new Period(PeriodType.THIS_AND_LAST_WEEK, lastWeek.getStart(), thisWeek.getEnd());
        }
    },
    THIS_AND_LAST_MONTH(R.string.period_this_and_last_month, "This and last month", true, false) {
        @Override
        public Period calculatePeriod(long refTime) {
            Period lastMonth = LAST_MONTH.calculatePeriod(refTime);
            Period thisMonth = THIS_MONTH.calculatePeriod(refTime);
            return new Period(PeriodType.THIS_AND_LAST_MONTH, lastMonth.getStart(), thisMonth.getEnd());
        }
    },
    TOMORROW(R.string.period_tomorrow, "Tomorrow", false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.TOMORROW, start, end);
        }
    },
    NEXT_WEEK(R.string.period_next_week, "Next week", false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Period thisWeek = THIS_WEEK.calculatePeriod(refTime);
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(thisWeek.getStart());
            start.add(Calendar.DAY_OF_MONTH, 7);
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(thisWeek.getEnd());
            end.add(Calendar.DAY_OF_MONTH, 7);
            return new Period(PeriodType.NEXT_WEEK, start.getTimeInMillis(), end.getTimeInMillis());
        }
    },
    NEXT_MONTH(R.string.period_next_month, "Next month", false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.MONTH, 1);
            c.set(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.MONTH, 1);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.NEXT_MONTH, start, end);
        }
    },
    THIS_AND_NEXT_MONTH(R.string.period_this_and_next_month, "This and next month", false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Period thisMonth = THIS_MONTH.calculatePeriod(refTime);
            Period nextMonth = NEXT_MONTH.calculatePeriod(refTime);
            return new Period(PeriodType.THIS_AND_NEXT_MONTH, thisMonth.getStart(), nextMonth.getEnd());
        }
    },
    NEXT_3_MONTHS(R.string.period_next_3_months, "Next 3 months", false, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.set(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.MONTH, 3);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.NEXT_3_MONTHS, start, end);
        }
    },
    CUSTOM(R.string.period_custom, "Custom", true, true) {
        @Override
        public Period calculatePeriod(long refTime) {
            return null;
        }
    };

    public static PeriodType[] allRegular() {
        List<PeriodType> types = new ArrayList<>();
        for (PeriodType periodType : PeriodType.values()) {
            if (periodType.inPast) {
                types.add(periodType);
            }
        }
        return types.toArray(new PeriodType[0]);
    }

    public static PeriodType[] allPlanner() {
        List<PeriodType> types = new ArrayList<>();
        for (PeriodType periodType : PeriodType.values()) {
            if (periodType.inFuture) {
                types.add(periodType);
            }
        }
        return types.toArray(new PeriodType[0]);
    }

    public final int titleId;
    public final String defaultTitle;
    public final boolean inPast;
    public final boolean inFuture;

    PeriodType(int titleId, String defaultTitle, boolean inPast,boolean inFuture) {
        this.titleId = titleId;
        this.defaultTitle = defaultTitle;
        this.inPast = inPast;
        this.inFuture = inFuture;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }

    @NonNull
    @Override
    public String getName() {
        return name();
    }

    public abstract Period calculatePeriod(long refTime);

    public Period calculatePeriod() {
        return calculatePeriod(System.currentTimeMillis());
    }
}
