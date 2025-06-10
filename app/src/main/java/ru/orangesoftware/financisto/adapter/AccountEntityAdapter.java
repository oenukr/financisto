package ru.orangesoftware.financisto.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.db.entity.AccountEntity;
import ru.orangesoftware.financisto.datetime.DateUtils;
import ru.orangesoftware.financisto.model.AccountType;
import ru.orangesoftware.financisto.model.CardIssuer;
import ru.orangesoftware.financisto.model.ElectronicPaymentType;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.utils.Utils;
import ru.orangesoftware.financisto.viewmodel.AccountDisplayData; // Added import

public class AccountEntityAdapter extends BaseAdapter {
    private Context context;
    private List<AccountDisplayData> accountsDisplayData = new ArrayList<>(); // Changed type
    private LayoutInflater inflater;
    private Utils u;
    private DateFormat df;
    private boolean isShowAccountLastTransactionDate;

    public AccountEntityAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.u = new Utils(context);
        this.df = DateUtils.getShortDateFormat(context);
        this.isShowAccountLastTransactionDate = MyPreferences.isShowAccountLastTransactionDate(context);
    }

    public void submitList(List<AccountDisplayData> newAccountsDisplayData) { // Changed type
        this.accountsDisplayData.clear();
        if (newAccountsDisplayData != null) {
            this.accountsDisplayData.addAll(newAccountsDisplayData);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return accountsDisplayData.size();
    }

    @Override
    public AccountDisplayData getItem(int position) { // Changed type
        if (position < 0 || position >= accountsDisplayData.size()) {
            return null;
        }
        return accountsDisplayData.get(position);
    }

    @Override
    public long getItemId(int position) {
        AccountDisplayData wrapper = getItem(position);
        // Ensure wrapper and its accountEntity are not null before calling getId
        return (wrapper != null && wrapper.getAccountEntity() != null) ? wrapper.getAccountEntity().getId() : -1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.account_list_item, parent, false);
            holder = new ViewHolder();
            holder.iconView = convertView.findViewById(R.id.icon);
            holder.iconOverView = convertView.findViewById(R.id.active_icon);
            holder.topView = convertView.findViewById(R.id.top);
            holder.centerView = convertView.findViewById(R.id.center);
            holder.bottomView = convertView.findViewById(R.id.bottom);
            holder.rightCenterView = convertView.findViewById(R.id.right_center);
            holder.rightView = convertView.findViewById(R.id.right);
            holder.progressBar = convertView.findViewById(R.id.progress);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AccountDisplayData wrapper = getItem(position);

        if (wrapper == null || wrapper.getAccountEntity() == null) {
            // Handle null case, clear views or set to default
            holder.centerView.setText("");
            holder.topView.setText("");
            holder.bottomView.setText("");
            holder.rightCenterView.setText("");
            holder.rightView.setText("");
            holder.progressBar.setVisibility(View.GONE);
            holder.iconView.setImageDrawable(null);
            holder.iconOverView.setVisibility(View.INVISIBLE);
            return convertView;
        }

        AccountEntity account = wrapper.getAccountEntity();
        String currencySymbol = wrapper.getCurrencySymbol();

        holder.centerView.setText(account.getTitle());

        AccountType type = AccountType.CASH; // Default
        if (account.getType() != null) {
            try {
                type = AccountType.valueOf(account.getType());
            } catch (IllegalArgumentException e) { /* Default or log */ }
        }

        if (type.isCard() && account.getIssuer() != null && !account.getIssuer().isEmpty()) {
            try {
                CardIssuer cardIssuer = CardIssuer.valueOf(account.getIssuer());
                holder.iconView.setImageResource(cardIssuer.getIconId());
            } catch (IllegalArgumentException e) {
                holder.iconView.setImageResource(type.getIconId());
            }
        } else if (type.isElectronic() && account.getIssuer() != null && !account.getIssuer().isEmpty()) {
            try {
                ElectronicPaymentType paymentType = ElectronicPaymentType.valueOf(account.getIssuer());
                holder.iconView.setImageResource(paymentType.getIconId());
            } catch (IllegalArgumentException e) {
                holder.iconView.setImageResource(type.getIconId());
            }
        } else {
            holder.iconView.setImageResource(type.getIconId());
        }

        if (account.isActive()) {
            holder.iconView.getDrawable().mutate().setAlpha(0xFF);
            holder.iconOverView.setVisibility(View.INVISIBLE);
        } else {
            holder.iconView.getDrawable().mutate().setAlpha(0x77);
            holder.iconOverView.setVisibility(View.VISIBLE);
        }

        StringBuilder sb = new StringBuilder();
        if (!Utils.isEmpty(account.getIssuer())) {
            sb.append(account.getIssuer());
        }
        if (!Utils.isEmpty(account.getNumber())) {
            sb.append(" #").append(account.getNumber());
        }
        if (sb.length() == 0 && type != null) { // Added null check for type
            sb.append(context.getString(type.getTitleId()));
        }
        holder.topView.setText(sb.toString());

        long dateToDisplay = account.getCreationDate();
        if (isShowAccountLastTransactionDate && account.getLastTransactionDate() > 0) {
            dateToDisplay = account.getLastTransactionDate();
        }
        if (dateToDisplay > 0) {
            holder.bottomView.setText(df.format(new Date(dateToDisplay)));
            holder.bottomView.setVisibility(View.VISIBLE);
        } else {
            holder.bottomView.setVisibility(View.GONE);
        }

        long amount = account.getTotalAmount();

        if (type == AccountType.CREDIT_CARD && account.getLimitAmount() != 0) {
            long limitAmount = Math.abs(account.getLimitAmount());
            long balance = limitAmount + amount;
            long balancePercentage = 0;
            if (limitAmount != 0) {
                balancePercentage = 10000 * balance / limitAmount;
            }

            u.setAmountText(holder.rightView, currencySymbol, amount, false, true);
            u.setAmountText(holder.rightCenterView, currencySymbol, balance, false, true);

            holder.rightView.setVisibility(View.VISIBLE);
            holder.progressBar.setMax(10000);
            holder.progressBar.setProgress((int) balancePercentage);
            holder.progressBar.setVisibility(View.VISIBLE);
        } else {
            u.setAmountText(holder.rightCenterView, currencySymbol, amount, false, true);
            holder.rightView.setVisibility(View.GONE);
            holder.progressBar.setVisibility(View.GONE);
        }

        return convertView;
    }

    static class ViewHolder {
        ImageView iconView;
        ImageView iconOverView;
        TextView topView;
        TextView centerView;
        TextView bottomView;
        TextView rightCenterView;
        TextView rightView;
        ProgressBar progressBar;
    }
}
