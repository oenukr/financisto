/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package ru.orangesoftware.financisto.dialog;

import static ru.orangesoftware.financisto.utils.Utils.isNotEmpty;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.activity.AccountActivity;
import ru.orangesoftware.financisto.activity.AccountListActivity;
// import ru.orangesoftware.financisto.db.DatabaseAdapter; // Removed
import ru.orangesoftware.financisto.db.entity.AccountEntity; // Added
import ru.orangesoftware.financisto.model.AccountType;
import ru.orangesoftware.financisto.model.CardIssuer;
import ru.orangesoftware.financisto.utils.Utils;
import ru.orangesoftware.financisto.view.NodeInflater;

public class AccountInfoDialog {

    private final AccountListActivity parentActivity;
    // private final long accountId; // Replaced by accountEntity
    // private final DatabaseAdapter db; // Removed
    private final AccountEntity accountEntity; // Added
    private final String currencySymbol; // Added
    private final NodeInflater inflater;
    private final LayoutInflater layoutInflater;
    private final Utils u;

    public AccountInfoDialog(AccountListActivity parentActivity, AccountEntity accountEntity,
                             String currencySymbol, NodeInflater inflater) { // Updated constructor
        this.parentActivity = parentActivity;
        this.accountEntity = accountEntity;
        this.currencySymbol = currencySymbol != null ? currencySymbol : ""; // Handle null
        // this.db = db; // Removed
        this.inflater = inflater;
        this.layoutInflater = (LayoutInflater) parentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.u = new Utils(parentActivity);
    }

    public void show() {
        // Account a = db.getAccount(accountId); // Replaced by accountEntity from constructor
        if (accountEntity == null) {
            Toast t = Toast.makeText(parentActivity, R.string.no_account, Toast.LENGTH_LONG);
            t.show();
            return;
        }

        View v = layoutInflater.inflate(R.layout.info_dialog, null);
        LinearLayout layout = v.findViewById(R.id.list);

        View titleView = createTitleView(accountEntity);
        createNodes(accountEntity, layout);

        showDialog(v, titleView, accountEntity.getId()); // Pass ID for edit button
    }

    private View createTitleView(AccountEntity a) { // Takes AccountEntity
        View titleView = layoutInflater.inflate(R.layout.info_dialog_title, null);
        TextView titleLabel = titleView.findViewById(R.id.label);
        TextView titleData = titleView.findViewById(R.id.data);
        ImageView titleIcon = titleView.findViewById(R.id.icon);

        titleLabel.setText(a.getTitle());

        AccountType type = AccountType.CASH; // Default
        if (a.getType() != null) {
            try {
                type = AccountType.valueOf(a.getType());
            } catch (IllegalArgumentException e) { /* Default or log */ }
        }
        titleData.setText(type.getTitleId());
        titleIcon.setImageResource(type.getIconId());

        return titleView;
    }

    private void createNodes(AccountEntity a, LinearLayout layout) { // Takes AccountEntity
        AccountType type = AccountType.CASH; // Default
        if (a.getType() != null) {
            try {
                type = AccountType.valueOf(a.getType());
            } catch (IllegalArgumentException e) { /* Default or log */ }
        }

        if (type.isCard() && isNotEmpty(a.getIssuer())) {
            CardIssuer cardIssuer = null;
            try {
                 cardIssuer = CardIssuer.valueOf(a.getIssuer());
            } catch (IllegalArgumentException e) { /* Issuer string not in enum, handle if necessary */ }

            if (cardIssuer != null) {
                add(layout, R.string.issuer, issuerTitle(a), cardIssuer);
            } else {
                // Fallback if issuer string is not a known CardIssuer enum constant
                add(layout, R.string.issuer, issuerTitle(a));
            }
        }
        // Use currencySymbol passed to constructor.
        add(layout, R.string.currency, currencySymbol.isEmpty() ? String.valueOf(a.getCurrencyId()) : currencySymbol);

        if (type.isCreditCard() && a.getLimitAmount() != 0) {
            long limitAmount = Math.abs(a.getLimitAmount());
            long balance = limitAmount + a.getTotalAmount(); // totalAmount for CC is usually negative
            TextView amountView = add(layout, R.string.amount, "");
            // Assuming u.setAmountText can take currencySymbol or currencyId (needs check or adaptation of Utils)
            u.setAmountText(amountView, currencySymbol, a.getTotalAmount(), true, true);
            TextView limitAmountView = add(layout, R.string.balance, "");
            u.setAmountText(limitAmountView, currencySymbol, balance, true, true);
        } else {
            TextView amountView = add(layout, R.string.balance, "");
            u.setAmountText(amountView, currencySymbol, a.getTotalAmount(), true, true);
        }
        // Note field from AccountEntity does not exist. Original Account model had 'note'.
        // If notes are needed, AccountEntity needs a 'note' field or it's fetched differently.
        // add(layout, R.string.note, a.getNote()); // AccountEntity doesn't have getNote()
    }

    private String issuerTitle(AccountEntity a) { // Takes AccountEntity
        return (isNotEmpty(a.getIssuer()) ? a.getIssuer() : "")+" "+(isNotEmpty(a.getNumber()) ? "#"+a.getNumber() : "");
    }

    private void showDialog(final View v, View titleView, final long currentAccountId) { // Added accountId for edit
        final Dialog d = new AlertDialog.Builder(parentActivity)
                .setCustomTitle(titleView)
                .setView(v)
                .create();
        d.setCanceledOnTouchOutside(true);

        Button bEdit = v.findViewById(R.id.bEdit);
        bEdit.setOnClickListener(arg0 -> {
            d.dismiss();
            Intent intent = new Intent(parentActivity, AccountActivity.class);
            intent.putExtra(AccountActivity.ACCOUNT_ID_EXTRA, currentAccountId); // Use passed ID
            parentActivity.startActivityForResult(intent, AccountListActivity.EDIT_ACCOUNT_REQUEST);
        });

        Button bClose = v.findViewById(R.id.bClose);
        bClose.setOnClickListener(arg0 -> d.dismiss());

        d.show();
    }

    private void add(LinearLayout layout, int labelId, String data, CardIssuer cardIssuer) {
        inflater.new Builder(layout, R.layout.select_entry_simple_icon)
                .withIcon(cardIssuer.getIconId()).withLabel(labelId).withData(data).create();
    }

    private TextView add(LinearLayout layout, int labelId, String data) {
        View v = inflater.new Builder(layout, R.layout.select_entry_simple).withLabel(labelId)
                .withData(data).create();
        return v.findViewById(R.id.data);
    }

    private LinearLayout add(LinearLayout layout, String label, String data) {
        return (LinearLayout) inflater.new Builder(layout, R.layout.select_entry_simple).withLabel(label)
                .withData(data).create();
    }

}
