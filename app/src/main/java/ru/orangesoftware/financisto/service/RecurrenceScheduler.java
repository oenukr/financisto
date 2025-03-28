package ru.orangesoftware.financisto.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import ru.orangesoftware.financisto.activity.ScheduledAlarmReceiver;
import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.model.RestoredTransaction;
import ru.orangesoftware.financisto.model.SystemAttribute;
import ru.orangesoftware.financisto.model.TransactionAttributeInfo;
import ru.orangesoftware.financisto.model.TransactionInfo;
import ru.orangesoftware.financisto.recur.DateRecurrenceIterator;
import ru.orangesoftware.financisto.recur.Recurrence;
import ru.orangesoftware.financisto.utils.Logger;
import ru.orangesoftware.financisto.utils.MyPreferences;

public class RecurrenceScheduler {

    private final Logger logger = new DependenciesHolder().getLogger();

    private static final String TAG = "RecurrenceScheduler";
	private static final Date NULL_DATE = new Date(0);
	private static final int MAX_RESTORED = 1000;

    public static final String SCHEDULED_TRANSACTION_ID = "scheduledTransactionId";

    private final DatabaseAdapter db;

    public RecurrenceScheduler(DatabaseAdapter db) {
        this.db = db;
    }

    public int scheduleAll(Context context) {
        long now = System.currentTimeMillis();
        int restoredTransactionsCount = 0;
        if (MyPreferences.isRestoreMissedScheduledTransactions(context)) {
            restoredTransactionsCount = restoreMissedSchedules(now);
            // all transactions up to and including now has already been restored
            now += 1000;
        }
        scheduleAll(context, now);
        return restoredTransactionsCount;
    }

    @Nullable
    public TransactionInfo scheduleOne(Context context, long scheduledTransactionId) {
        logger.i("Alarm for " + scheduledTransactionId + " received..");
        TransactionInfo transaction = db.getTransactionInfo(scheduledTransactionId);
        if (transaction != null) {
            long transactionId = duplicateTransactionFromTemplate(transaction);
            boolean hasBeenRescheduled = rescheduleTransaction(context, transaction);
            if (!hasBeenRescheduled) {
                deleteTransactionIfNeeded(transaction);
                logger.i("Expired transaction " + transaction.id + " has been deleted");
            }
            transaction.id = transactionId;
            return transaction;
        }
        return null;
    }

    private void deleteTransactionIfNeeded(TransactionInfo transaction) {
        TransactionAttributeInfo a = db.getSystemAttributeForTransaction(SystemAttribute.DELETE_AFTER_EXPIRED, transaction.id);
        if (a != null && Boolean.parseBoolean(a.value)) {
            db.deleteTransaction(transaction.id);
        }
    }

    /**
     * Restores missed scheduled transactions on backup and on phone restart
     * @param now current time
     * @return restored transactions count
     */
    private int restoreMissedSchedules(long now) {
        try {
            List<RestoredTransaction> restored = getMissedSchedules(now);
            if (!restored.isEmpty()) {
                db.storeMissedSchedules(restored, now);
                logger.i("["+restored.size()+"] scheduled transactions have been restored:");
                for (int i=0; i<10 && i<restored.size(); i++) {
                    RestoredTransaction rt = restored.get(i);
                    logger.i(rt.getTransactionId()+" at "+rt.getDateTime());
                }
                return restored.size();
            }
        } catch (Exception ex) {
            // eat all exceptions
            logger.e(ex, "Unexpected error while restoring schedules");
        }
        return 0;
    }

    private long duplicateTransactionFromTemplate(TransactionInfo transaction) {
        return db.duplicateTransaction(transaction.id);
    }

	public List<RestoredTransaction> getMissedSchedules(long now) {
		long t0 = System.currentTimeMillis();
		try {
			Date endDate = new Date(now);
			List<RestoredTransaction> restored = new ArrayList<>();
			ArrayList<TransactionInfo> list = db.getAllScheduledTransactions();
			for (TransactionInfo t : list) {
				if (t.recurrence != null) {
					long lastRecurrence = t.lastRecurrence;
					if (lastRecurrence > 0) {
                        // move lastRecurrence time by 1 sec into future to not trigger the same time again
						DateRecurrenceIterator ri = createIterator(t.recurrence, lastRecurrence+1000);
						while (ri.hasNext()) {
							Date nextDate = ri.next();
							if (nextDate.after(endDate)) {
								break;
							}
							addRestoredTransaction(restored, t, nextDate);
						}
					}
				} else {
					Date nextDate = new Date(t.dateTime);
					if (nextDate.before(endDate)) {
						addRestoredTransaction(restored, t, nextDate);
					}
				}				
			}
			if (restored.size() > MAX_RESTORED) {
				Collections.sort(restored, (t01, t1) -> t1.getDateTime().compareTo(t01.getDateTime()));
				restored = restored.subList(0, MAX_RESTORED);
			}
			return restored;
		} finally {
			logger.i("getSortedSchedules="+(System.currentTimeMillis()-t0)+"ms");
		}		
	}

	private void addRestoredTransaction(List<RestoredTransaction> restored,
			TransactionInfo t, Date nextDate) {
		RestoredTransaction rt = new RestoredTransaction(t.id, nextDate);
		restored.add(rt);							
	}

    public ArrayList<TransactionInfo> getSortedSchedules(long now) {
        long t0 = System.currentTimeMillis();
        try {
            ArrayList<TransactionInfo> list = db.getAllScheduledTransactions();
            logger.i("Got " + list.size() + " scheduled transactions");
            calculateNextScheduleDateForAllTransactions(list, now);
            sortTransactionsByScheduleDate(list, now);
            return list;
        } finally {
            logger.i("getSortedSchedules=" + (System.currentTimeMillis() - t0) + "ms");
        }
    }

    public ArrayList<TransactionInfo> scheduleAll(Context context, long now) {
        ArrayList<TransactionInfo> scheduled = getSortedSchedules(now);
        for (TransactionInfo transaction : scheduled) {
            scheduleAlarm(context, transaction, now);
        }
        return scheduled;
    }

    public boolean scheduleAlarm(Context context, TransactionInfo transaction, long now) {
        if (shouldSchedule(transaction, now)) {
            Date scheduleTime = transaction.nextDateTime;
            AlarmManager service = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            if (service != null) {
                PendingIntent pendingIntent = createPendingIntentForScheduledAlarm(context, transaction.id);
                service.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduleTime.getTime(), pendingIntent);
                logger.i("Scheduling alarm for " + transaction.id + " at " + scheduleTime);
                return true;
            }
        }
        logger.i("Transactions "+transaction.id+" with next date/time "+transaction.nextDateTime+" is not selected for schedule");
        return false;
    }

    public boolean rescheduleTransaction(Context context, TransactionInfo transaction) {
        if (transaction.recurrence != null) {
            long now = System.currentTimeMillis()+1000;
            calculateAndSetNextDateTimeOnTransaction(transaction, now);
            return scheduleAlarm(context, transaction, now);
        }
        return false;
    }

    private boolean shouldSchedule(TransactionInfo transaction, long now) {
        return transaction.nextDateTime != null && now < transaction.nextDateTime.getTime();
    }

    public void cancelPendingIntentForSchedule(Context context, long transactionId) {
        logger.i("Cancelling pending alarm for "+transactionId);
        AlarmManager service = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent intent = createPendingIntentForScheduledAlarm(context, transactionId);
        service.cancel(intent);
    }

    private PendingIntent createPendingIntentForScheduledAlarm(Context context, long transactionId) {
        Intent intent = new Intent("ru.orangesoftware.financisto.SCHEDULED_ALARM");
        intent.setClass(context, ScheduledAlarmReceiver.class);
        intent.putExtra(SCHEDULED_TRANSACTION_ID, transactionId);
        return PendingIntent.getBroadcast(context, (int)transactionId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Correct order by nextDateTime:
     * 2010-12-01
     * 2010-12-02
     * 2010-11-23 <- today
     * 2010-11-11
     * 2010-10-08
     * NULL
     */
    public static class RecurrenceComparator implements Comparator<TransactionInfo> {

        private final Date today;

        public RecurrenceComparator(long now) {
            this.today = new Date(now);
        }

        @Override
        public int compare(TransactionInfo o1, TransactionInfo o2) {
            Date d1 = o1 != null ? (o1.nextDateTime != null ? o1.nextDateTime : NULL_DATE) : NULL_DATE;
            Date d2 = o2 != null ? (o2.nextDateTime != null ? o2.nextDateTime : NULL_DATE) : NULL_DATE;
            if (d1.after(today)) {
                if (d2.after(today)) {
                    return d1.compareTo(d2);
                } else {
                    return -1;
                }
            } else {
                if (d2.after(today)) {
                    return 1;
                } else {
                    return -d1.compareTo(d2);
                }
            }
        }
    }

	private void sortTransactionsByScheduleDate(ArrayList<TransactionInfo> list, long now) {
		Collections.sort(list, new RecurrenceComparator(now));
	}

	private void calculateNextScheduleDateForAllTransactions(ArrayList<TransactionInfo> list, long now) {
		for (TransactionInfo t : list) {
            calculateAndSetNextDateTimeOnTransaction(t, now);
        }
	}

    private void calculateAndSetNextDateTimeOnTransaction(TransactionInfo t, long now) {
        if (t.recurrence != null) {
            t.nextDateTime = calculateNextDate(t.recurrence, now);
        } else {
            t.nextDateTime = new Date(t.dateTime);
        }
        logger.i("Calculated schedule time for "+t.id+" is "+t.nextDateTime);
    }

	public Date calculateNextDate(String recurrence, long now) {
        try {
            DateRecurrenceIterator ri = createIterator(recurrence, now);
            if (ri.hasNext()) {
                return ri.next();
            }
        } catch (Exception ex) {
            logger.e("Unable to calculate next date for "+recurrence+" at "+now);
        }
        return null;
	}

	private DateRecurrenceIterator createIterator(String recurrence, long now) {
		Recurrence r = Recurrence.parse(recurrence);
        Date advanceDate = new Date(now);
        return r.createIterator(advanceDate);
	}

}
