package ru.orangesoftware.financisto.service;

import static ru.orangesoftware.financisto.service.FinancistoSmsWorkManager.ACTION_NEW_TRANSACTION_SMS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkRequest;

import java.util.Set;

import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.utils.Logger;

public class SmsReceiver extends BroadcastReceiver {

    private final Logger logger = new DependenciesHolder().getLogger();

    public static final String PDUS_NAME = "pdus";
    public static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    public static final String SMS_TRANSACTION_NUMBER = "SMS_TRANSACTION_NUMBER";
    public static final String SMS_TRANSACTION_BODY = "SMS_TRANSACTION_BODY";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if(!SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        Bundle pdusObj = intent.getExtras();
        final DatabaseAdapter db = new DatabaseAdapter(context);
        Set<String> smsNumbers = db.findAllSmsTemplateNumbers();
        logger.d("All sms numbers: " + smsNumbers);

        Object[] msgs;
        if (pdusObj != null && (msgs = (Object[]) pdusObj.get(PDUS_NAME)) != null && msgs.length > 0) {
            logger.d("pdus: %s", msgs.length);

            SmsMessage msg = null;
            String addr = null;
            final StringBuilder body = new StringBuilder();
            String format = intent.getExtras().getString("format");

            for (final Object one : msgs) {
                msg = SmsMessage.createFromPdu((byte[]) one, format);
                addr = msg.getOriginatingAddress();
                if (smsNumbers.contains(addr)) {
                    body.append(msg.getDisplayMessageBody());
                }
            }

            final String fullSmsBody = body.toString();
            if (!fullSmsBody.isEmpty()) {
                logger.d("%s sms from %s: `%s`", msg.getTimestampMillis(), addr, fullSmsBody);

                Data inputData = new Data.Builder()
                        .putString("action", ACTION_NEW_TRANSACTION_SMS)
                        .putString(SMS_TRANSACTION_NUMBER, addr)
                        .putString(SMS_TRANSACTION_BODY, fullSmsBody)
                        .build();

                WorkRequest workRequest = new OneTimeWorkRequest.Builder(FinancistoSmsWorkManager.class)
                        .setInputData(inputData)
                        .build();
                FinancistoSmsWorkManager.enqueueWork(context, workRequest);
            }
                // Display SMS message
                //                Toast.makeText(context, String.format("%s:%s", addr, body), Toast.LENGTH_SHORT).show();
        }

        // WARNING!!!
        // If you uncomment the next line then received SMS will not be put to incoming.
        // Be careful!
        // this.abortBroadcast();
    }
}
