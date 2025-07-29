/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.adapter;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.datetime.DateUtils;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.AccountType;
import ru.orangesoftware.financisto.model.CardIssuer;
import ru.orangesoftware.financisto.model.ElectronicPaymentType;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.utils.Utils;
import ru.orangesoftware.orb.EntityManager;

public class AccountListAdapter2 extends ResourceCursorAdapter {

    private final Utils u;
    private final DateFormat df;
    private final boolean isShowAccountLastTransactionDate;

    public AccountListAdapter2(Context context, Cursor c) {
        super(context, R.layout.account_list_item, c);
        this.u = new Utils(context);
        this.df = DateUtils.getShortDateFormat(context);
        this.isShowAccountLastTransactionDate = MyPreferences.isShowAccountLastTransactionDate(context);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = super.newView(context, cursor, parent);
        return AccountListItemHolder.create(view);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        Account a = EntityManager.loadFromCursor(cursor, Account.class);
        AccountListItemHolder v = (AccountListItemHolder) view.getTag();

        v.centerView.setText(a.getTitle());

        AccountType type = AccountType.valueOf(a.getType());
        if (type.isCard() && a.getCardIssuer() != null) {
            CardIssuer cardIssuer = CardIssuer.valueOf(a.getCardIssuer());
            v.iconView.setImageResource(cardIssuer.getIconId());
        } else if (type.isElectronic() && a.getCardIssuer() != null) {
            ElectronicPaymentType paymentType = ElectronicPaymentType.valueOf(a.getCardIssuer());
            v.iconView.setImageResource(paymentType.getIconId());
        } else {
            v.iconView.setImageResource(type.getIconId());
        }
        if (a.isActive()) {
            v.iconView.getDrawable().mutate().setAlpha(0xFF);
            v.iconOverView.setVisibility(View.INVISIBLE);
        } else {
            v.iconView.getDrawable().mutate().setAlpha(0x77);
            v.iconOverView.setVisibility(View.VISIBLE);
        }

        StringBuilder sb = new StringBuilder();
        if (!Utils.isEmpty(a.getIssuer())) {
            sb.append(a.getIssuer());
        }
        if (!Utils.isEmpty(a.getNumber())) {
            sb.append(" #").append(a.getNumber());
        }
        if (sb.length() == 0) {
            sb.append(context.getString(type.getTitleId()));
        }
        v.topView.setText(sb.toString());

        long date = a.getCreationDate();
        if (isShowAccountLastTransactionDate && a.getLastTransactionDate() > 0) {
            date = a.getLastTransactionDate();
        }
        v.bottomView.setText(df.format(new Date(date)));

        long amount = a.getTotalAmount();
        if (type == AccountType.CREDIT_CARD && a.getLimitAmount() != 0) {
            long limitAmount = Math.abs(a.getLimitAmount());
            long balance = limitAmount + amount;
            long balancePercentage = 10000 * balance / limitAmount;
            u.setAmountText(v.rightView, a.getCurrency(), amount, false);
            u.setAmountText(v.rightCenterView, a.getCurrency(), balance, false);
            v.rightView.setVisibility(View.VISIBLE);
            v.progressBar.setMax(10000);
            v.progressBar.setProgress((int) balancePercentage);
            v.progressBar.setVisibility(View.VISIBLE);
        } else {
            u.setAmountText(v.rightCenterView, a.getCurrency(), amount, false);
            v.rightView.setVisibility(View.GONE);
            v.progressBar.setVisibility(View.GONE);
        }
    }

    private static class AccountListItemHolder {
        ImageView iconView;
        ImageView iconOverView;
        TextView topView;
        TextView centerView;
        TextView bottomView;
        TextView rightCenterView;
        TextView rightView;
        ProgressBar progressBar;

        public static View create(View view) {
            AccountListItemHolder v = new AccountListItemHolder();
            v.iconView = view.findViewById(R.id.icon);
            v.iconOverView = view.findViewById(R.id.active_icon);
            v.topView = view.findViewById(R.id.top);
            v.centerView = view.findViewById(R.id.center);
            v.bottomView = view.findViewById(R.id.bottom);
            v.rightCenterView = view.findViewById(R.id.right_center);
            v.rightView = view.findViewById(R.id.right);
            v.rightView.setVisibility(View.GONE);
            v.progressBar = view.findViewById(R.id.progress);
            v.progressBar.setVisibility(View.GONE);
            view.setTag(v);
            return view;
        }

    }


}
