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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.datetime.DateUtils;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.AccountType;
import ru.orangesoftware.financisto.model.CardIssuer;
import ru.orangesoftware.financisto.model.ElectronicPaymentType;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.utils.Utils;

public class AccountListAdapter2 extends ArrayAdapter<Account> {

    private final Utils u;
    private final DateFormat df;
    private final boolean isShowAccountLastTransactionDate;
    private final int resource;

    public AccountListAdapter2(Context context, int resource, List<Account> items) {
        super(context, resource, items);
        this.u = new Utils(context);
        this.df = DateUtils.getShortDateFormat(context);
        this.isShowAccountLastTransactionDate = MyPreferences.isShowAccountLastTransactionDate(context);
        this.resource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        AccountListItemHolder holder;
        if (convertView == null) {
            view = LayoutInflater.from(getContext()).inflate(resource, parent, false);
            holder = AccountListItemHolder.create(view);
            view.setTag(holder);
        } else {
            view = convertView;
            holder = (AccountListItemHolder) view.getTag();
        }

        Account a = getItem(position);

        holder.centerView.setText(a.getTitle());

        AccountType type = AccountType.valueOf(a.getType());
        if (type.isCard() && a.getCardIssuer() != null) {
            CardIssuer cardIssuer = CardIssuer.valueOf(a.getCardIssuer());
            holder.iconView.setImageResource(cardIssuer.getIconId());
        } else if (type.isElectronic() && a.getCardIssuer() != null) {
            ElectronicPaymentType paymentType = ElectronicPaymentType.valueOf(a.getCardIssuer());
            holder.iconView.setImageResource(paymentType.getIconId());
        } else {
            holder.iconView.setImageResource(type.getIconId());
        }
        if (a.isActive()) {
            holder.iconView.getDrawable().mutate().setAlpha(0xFF);
            holder.iconOverView.setVisibility(View.INVISIBLE);
        } else {
            holder.iconView.getDrawable().mutate().setAlpha(0x77);
            holder.iconOverView.setVisibility(View.VISIBLE);
        }

        StringBuilder sb = new StringBuilder();
        if (!Utils.isEmpty(a.getIssuer())) {
            sb.append(a.getIssuer());
        }
        if (!Utils.isEmpty(a.getNumber())) {
            sb.append(" #").append(a.getNumber());
        }
        if (sb.length() == 0) {
            sb.append(getContext().getString(type.getTitleId()));
        }
        holder.topView.setText(sb.toString());

        long date = a.getCreationDate();
        if (isShowAccountLastTransactionDate && a.getLastTransactionDate() > 0) {
            date = a.getLastTransactionDate();
        }
        holder.bottomView.setText(df.format(new Date(date)));

        long amount = a.getTotalAmount();
        if (type == AccountType.CREDIT_CARD && a.getLimitAmount() != 0) {
            long limitAmount = Math.abs(a.getLimitAmount());
            long balance = limitAmount + amount;
            long balancePercentage = 10000 * balance / limitAmount;
            u.setAmountText(holder.rightView, a.getCurrency(), amount, false);
            u.setAmountText(holder.rightCenterView, a.getCurrency(), balance, false);
            holder.rightView.setVisibility(View.VISIBLE);
            holder.progressBar.setMax(10000);
            holder.progressBar.setProgress((int) balancePercentage);
            holder.progressBar.setVisibility(View.VISIBLE);
        } else {
            u.setAmountText(holder.rightCenterView, a.getCurrency(), amount, false);
            holder.rightView.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.GONE);
        }
        return view;
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

        public static AccountListItemHolder create(View view) {
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
            return v;
        }

    }
}
