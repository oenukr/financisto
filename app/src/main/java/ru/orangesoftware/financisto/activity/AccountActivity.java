/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 *     Abdsandryk - adding bill filtering parameters
 ******************************************************************************/
package ru.orangesoftware.financisto.activity;

import static ru.orangesoftware.financisto.utils.EnumUtils.selectEnum;
import static ru.orangesoftware.financisto.utils.Utils.text;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.EntityEnumAdapter;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.AccountType;
import ru.orangesoftware.financisto.model.CardIssuer;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.model.ElectronicPaymentType;
import ru.orangesoftware.financisto.model.Transaction;
import ru.orangesoftware.financisto.utils.EntityEnum;
import ru.orangesoftware.financisto.utils.TransactionUtils;
import ru.orangesoftware.financisto.utils.Utils;
import ru.orangesoftware.financisto.widget.AmountInput;
import ru.orangesoftware.financisto.widget.AmountInput_;

public class AccountActivity extends AbstractActivity {

    public static final String ACCOUNT_ID_EXTRA = "accountId";

    private static final int NEW_CURRENCY_REQUEST = 1;

    private AmountInput amountInput;
    private AmountInput limitInput;
    private View limitAmountView;
    private EditText accountTitle;

    private Cursor currencyCursor;
    private TextView currencyText;
    private View accountTypeNode;
    private View cardIssuerNode;
    private View electronicPaymentNode;
    private View issuerNode;
    private EditText numberText;
    private View numberNode;
    private EditText issuerName;
    private EditText sortOrderText;
    private CheckBox isIncludedIntoTotals;
    private EditText noteText;
    private EditText closingDayText;
    private EditText paymentDayText;
    private View closingDayNode;
    private View paymentDayNode;

    private EntityEnumAdapter<AccountType> accountTypeAdapter;
    private EntityEnumAdapter<CardIssuer> cardIssuerAdapter;
    private EntityEnumAdapter<ElectronicPaymentType> electronicPaymentAdapter;
    private ListAdapter currencyAdapter;

    private Account account = new Account();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account);

        accountTitle = new EditText(this);
        accountTitle.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        accountTitle.setSingleLine();

        issuerName = new EditText(this);
        issuerName.setSingleLine();

        numberText = new EditText(this);
        numberText.setHint(R.string.card_number_hint);
        numberText.setSingleLine();

        sortOrderText = new EditText(this);
        sortOrderText.setInputType(InputType.TYPE_CLASS_NUMBER);
        sortOrderText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        sortOrderText.setSingleLine();

        closingDayText = new EditText(this);
        closingDayText.setInputType(InputType.TYPE_CLASS_NUMBER);
        closingDayText.setHint(R.string.closing_day_hint);
        closingDayText.setSingleLine();

        paymentDayText = new EditText(this);
        paymentDayText.setInputType(InputType.TYPE_CLASS_NUMBER);
        paymentDayText.setHint(R.string.payment_day_hint);
        paymentDayText.setSingleLine();

        amountInput = AmountInput_.build(this);
        amountInput.setOwner(this);

        limitInput = AmountInput_.build(this);
        limitInput.setOwner(this);

        LinearLayout layout = findViewById(R.id.layout);

        accountTypeAdapter = new EntityEnumAdapter<>(this, AccountType.values(), false);
        accountTypeNode = activityLayout.addListNodeIcon(layout, R.id.account_type, R.string.account_type, R.string.account_type);
        ImageView icon = accountTypeNode.findViewById(R.id.icon);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.holo_gray_light));

        cardIssuerAdapter = new EntityEnumAdapter<>(this, CardIssuer.values(), false);
        cardIssuerNode = activityLayout.addListNodeIcon(layout, R.id.card_issuer, R.string.card_issuer, R.string.card_issuer);
        setVisibility(cardIssuerNode, View.GONE);

        electronicPaymentAdapter = new EntityEnumAdapter<>(this, ElectronicPaymentType.values(), false);
        electronicPaymentNode = activityLayout.addListNodeIcon(layout, R.id.electronic_payment_type, R.string.electronic_payment_type, R.string.card_issuer);
        setVisibility(electronicPaymentNode, View.GONE);

        issuerNode = activityLayout.addEditNode(layout, R.string.issuer, issuerName);
        setVisibility(issuerNode, View.GONE);

        numberNode = activityLayout.addEditNode(layout, R.string.card_number, numberText);
        setVisibility(numberNode, View.GONE);

        closingDayNode = activityLayout.addEditNode(layout, R.string.closing_day, closingDayText);
        setVisibility(closingDayNode, View.GONE);

        paymentDayNode = activityLayout.addEditNode(layout, R.string.payment_day, paymentDayText);
        setVisibility(paymentDayNode, View.GONE);

        currencyCursor = db.getAllCurrencies("name");
        startManagingCursor(currencyCursor);
        currencyAdapter = TransactionUtils.createCurrencyAdapter(this, currencyCursor);

        activityLayout.addEditNode(layout, R.string.title, accountTitle);
        currencyText = activityLayout.addListNodePlus(layout, R.id.currency, R.id.currency_add, R.string.currency, R.string.select_currency);

        limitInput.setExpense();
        limitInput.disableIncomeExpenseButton();
        limitAmountView = activityLayout.addEditNode(layout, R.string.limit_amount, limitInput);
        setVisibility(limitAmountView, View.GONE);

        Intent intent = getIntent();
        if (intent != null) {
            long accountId = intent.getLongExtra(ACCOUNT_ID_EXTRA, -1);
            if (accountId != -1) {
                this.account = db.getAccount(accountId);
                if (this.account == null) {
                    this.account = new Account();
                }
            } else {
                selectAccountType(AccountType.valueOf(account.getType()));
            }
        }

        if (account.getId() == -1) {
            activityLayout.addEditNode(layout, R.string.opening_amount, amountInput);
            amountInput.setIncome();
        }

        noteText = new EditText(this);
        noteText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        noteText.setLines(2);
        activityLayout.addEditNode(layout, R.string.note, noteText);

        activityLayout.addEditNode(layout, R.string.sort_order, sortOrderText);
        isIncludedIntoTotals = activityLayout.addCheckboxNode(layout,
                R.id.is_included_into_totals, R.string.is_included_into_totals,
                R.string.is_included_into_totals_summary, true);

        if (account.getId() > 0) {
            editAccount();
        }

        Button bOK = findViewById(R.id.bOK);
        bOK.setOnClickListener(arg0 -> {
            if (account.getCurrency() == null) {
                Toast.makeText(AccountActivity.this, R.string.select_currency, Toast.LENGTH_SHORT).show();
                return;
            }
            if (Utils.isEmpty(accountTitle)) {
                accountTitle.setError(getString(R.string.title));
                return;
            }
            AccountType type = AccountType.valueOf(account.getType());
            if (type.getHasIssuer()) {
                account.setIssuer(Utils.text(issuerName));
            }
            if (type.getHasNumber()) {
                account.setNumber(Utils.text(numberText));
            }

            /********** validate closing and payment days **********/
            if (type.isCreditCard()) {
                String closingDay = Utils.text(closingDayText);
                account.setClosingDay(closingDay == null ? 0 : Integer.parseInt(closingDay));
                if (account.getClosingDay() != 0) {
                    if (account.getClosingDay() > 31) {
                        Toast.makeText(AccountActivity.this, R.string.closing_day_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                String paymentDay = Utils.text(paymentDayText);
                account.setPaymentDay(paymentDay == null ? 0 : Integer.parseInt(paymentDay));
                if (account.getPaymentDay() != 0) {
                    if (account.getPaymentDay() > 31) {
                        Toast.makeText(AccountActivity.this, R.string.payment_day_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }

            account.setTitle(text(accountTitle));
            account.setCreationDate(System.currentTimeMillis());
            String sortOrder = text(sortOrderText);
            account.setSortOrder(sortOrder == null ? 0 : Integer.parseInt(sortOrder));
            account.setIncludeIntoTotals(isIncludedIntoTotals.isChecked());
            account.setLimitAmount(-Math.abs(limitInput.getAmount()));
            account.setNote(text(noteText));

            long accountId = db.saveAccount(account);
            long amount = amountInput.getAmount();
            if (amount != 0) {
                Transaction t = new Transaction();
                t.fromAccountId = accountId;
                t.categoryId = 0;
                t.note = getResources().getText(R.string.opening_amount) + " (" + account.getTitle() + ")";
                t.fromAmount = amount;
                db.insertOrUpdate(t, null);
            }
            AccountWidget.updateWidgets(this);
            Intent intent1 = new Intent();
            intent1.putExtra(ACCOUNT_ID_EXTRA, accountId);
            setResult(RESULT_OK, intent1);
            finish();
        });

        Button bCancel = findViewById(R.id.bCancel);
        bCancel.setOnClickListener(arg0 -> {
            setResult(RESULT_CANCELED);
            finish();
        });

    }

    @Override
    protected void onClick(View v, int id) {
        switch (id) {
            case R.id.is_included_into_totals:
                isIncludedIntoTotals.performClick();
                break;
            case R.id.account_type:
                activityLayout.selectPosition(this, R.id.account_type, R.string.account_type, accountTypeAdapter, AccountType.valueOf(account.getType()).ordinal());
                break;
            case R.id.card_issuer:
                activityLayout.selectPosition(this, R.id.card_issuer, R.string.card_issuer, cardIssuerAdapter,
                        account.getCardIssuer() != null ? CardIssuer.valueOf(account.getCardIssuer()).ordinal() : 0);
                break;
            case R.id.electronic_payment_type:
                activityLayout.selectPosition(this, R.id.electronic_payment_type, R.string.electronic_payment_type, electronicPaymentAdapter,
                        selectEnum(ElectronicPaymentType.class, account.getCardIssuer(), ElectronicPaymentType.PAYPAL).ordinal());
                break;
            case R.id.currency:
                activityLayout.select(this, R.id.currency, R.string.currency, currencyCursor, currencyAdapter,
                        "_id", account.getCurrency() != null ? account.getCurrency().getId() : -1);
                break;
            case R.id.currency_add:
                addNewCurrency();
                break;
        }
    }

    private void addNewCurrency() {
        new CurrencySelector(this, db, currencyId -> {
            if (currencyId == 0) {
                Intent intent = new Intent(AccountActivity.this, CurrencyActivity.class);
                startActivityForResult(intent, NEW_CURRENCY_REQUEST);
            } else {
                currencyCursor.requery();
                selectCurrency(currencyId);
            }
        }).show();
    }

    @Override
    public void onSelectedId(int id, long selectedId) {
        if (id == R.id.currency) {
            selectCurrency(selectedId);
        }
    }

    @Override
    public void onSelectedPos(int id, int selectedPos) {
        switch (id) {
            case R.id.account_type:
                AccountType type = AccountType.values()[selectedPos];
                selectAccountType(type);
                break;
            case R.id.card_issuer:
                CardIssuer issuer = CardIssuer.values()[selectedPos];
                selectCardIssuer(issuer);
                break;
            case R.id.electronic_payment_type:
                ElectronicPaymentType paymentType = ElectronicPaymentType.values()[selectedPos];
                selectElectronicType(paymentType);
                break;
        }
    }

    private void selectAccountType(AccountType type) {
        ImageView icon = accountTypeNode.findViewById(R.id.icon);
        icon.setImageResource(type.getIconId());
        TextView label = accountTypeNode.findViewById(R.id.label);
        label.setText(type.getTitleId());

        setVisibility(cardIssuerNode, type.isCard() ? View.VISIBLE : View.GONE);
        setVisibility(issuerNode, type.getHasIssuer() ? View.VISIBLE : View.GONE);
        setVisibility(electronicPaymentNode, type.isElectronic() ? View.VISIBLE : View.GONE);
        setVisibility(numberNode, type.getHasNumber() ? View.VISIBLE : View.GONE);
        setVisibility(closingDayNode, type.isCreditCard() ? View.VISIBLE : View.GONE);
        setVisibility(paymentDayNode, type.isCreditCard() ? View.VISIBLE : View.GONE);

        setVisibility(limitAmountView, type == AccountType.CREDIT_CARD ? View.VISIBLE : View.GONE);
        account.setType(type.name());
        if (type.isCard()) {
            selectCardIssuer(selectEnum(CardIssuer.class, account.getCardIssuer(), CardIssuer.DEFAULT));
        } else if (type.isElectronic()) {
            selectElectronicType(selectEnum(ElectronicPaymentType.class, account.getCardIssuer(), ElectronicPaymentType.PAYPAL));
        } else {
            account.setCardIssuer(null);
        }
    }

    private void selectCardIssuer(CardIssuer issuer) {
        updateNode(cardIssuerNode, issuer);
        account.setCardIssuer(issuer.name());
    }

    private void selectElectronicType(ElectronicPaymentType paymentType) {
        updateNode(electronicPaymentNode, paymentType);
        account.setCardIssuer(paymentType.name());
    }

    private void updateNode(View note, EntityEnum enumItem) {
        ImageView icon = note.findViewById(R.id.icon);
        icon.setImageResource(enumItem.getIconId());
        TextView label = note.findViewById(R.id.label);
        label.setText(enumItem.getTitleId());
    }

    private void selectCurrency(long currencyId) {
        Currency c = db.get(Currency.class, currencyId);
        if (c != null) {
            selectCurrency(c);
        }
    }

    private void selectCurrency(Currency c) {
        currencyText.setText(c.getName());
        amountInput.setCurrency(c);
        limitInput.setCurrency(c);
        account.setCurrency(c);
    }

    private void editAccount() {
        selectAccountType(AccountType.valueOf(account.getType()));
        selectCurrency(account.getCurrency());
        accountTitle.setText(account.getTitle());
        issuerName.setText(account.getIssuer());
        numberText.setText(account.getNumber());
        sortOrderText.setText(String.valueOf(account.getSortOrder()));

        /******** bill filtering ********/
        if (account.getClosingDay() > 0) {
            closingDayText.setText(String.valueOf(account.getClosingDay()));
        }
        if (account.getPaymentDay() > 0) {
            paymentDayText.setText(String.valueOf(account.getPaymentDay()));
        }
        /********************************/

        isIncludedIntoTotals.setChecked(account.isIncludeIntoTotals());
        if (account.getLimitAmount() != 0) {
            limitInput.setAmount(-Math.abs(account.getLimitAmount()));
        }
        noteText.setText(account.getNote());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == NEW_CURRENCY_REQUEST) {
                currencyCursor.requery();
                long currencyId = data.getLongExtra(CurrencyActivity.CURRENCY_ID_EXTRA, -1);
                if (currencyId != -1) {
                    selectCurrency(currencyId);
                }
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

}
