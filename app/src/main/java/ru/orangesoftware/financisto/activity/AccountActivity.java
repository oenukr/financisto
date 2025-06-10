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

import static ru.orangesoftware.financisto.utils.EnumUtils.selectEnum; // May still be used for AccountType enums
import static ru.orangesoftware.financisto.utils.Utils.text; // May still be used for EditText reading

import android.content.Intent;
// import android.database.Cursor; // Replaced by List<CurrencyEntity>
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
// import android.widget.ListAdapter; // No longer used
import android.widget.ArrayAdapter; // For currency spinner/dialog
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.lifecycleScope;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.EntityEnumAdapter;
import ru.orangesoftware.financisto.db.entity.AccountEntity;
import ru.orangesoftware.financisto.db.entity.CurrencyEntity;
// import ru.orangesoftware.financisto.model.Account; // Replaced by AccountEntity
import ru.orangesoftware.financisto.model.AccountType;
import ru.orangesoftware.financisto.model.CardIssuer;
import ru.orangesoftware.financisto.model.Currency; // Old model, used by AmountInput
import ru.orangesoftware.financisto.model.ElectronicPaymentType;
// import ru.orangesoftware.financisto.model.Transaction; // Not directly used here now
import ru.orangesoftware.financisto.utils.EntityEnum;
// import ru.orangesoftware.financisto.utils.TransactionUtils; // For currency adapter
import ru.orangesoftware.financisto.utils.Utils;
import ru.orangesoftware.financisto.db.dao.CurrencyDao; // Added for CurrencySelector
import org.koin.java.KoinJavaComponent; // Added for Koin direct get
import ru.orangesoftware.financisto.widget.AmountInput;
import ru.orangesoftware.financisto.widget.AmountInput_;
import ru.orangesoftware.financisto.viewmodel.AccountViewModel;
import ru.orangesoftware.financisto.viewmodel.AccountFormState;
import ru.orangesoftware.financisto.viewmodel.Event;
import ru.orangesoftware.financisto.viewmodel.SaveResult;
import ru.orangesoftware.financisto.mapper.CurrencyEntityMapper; // Import the mapper

import kotlinx.coroutines.flow.FlowCollector;
import kotlin.Unit;

public class AccountActivity extends AbstractActivity {

    public static final String ACCOUNT_ID_EXTRA = "accountId";
    private static final int NEW_CURRENCY_REQUEST = 1;

    private AmountInput amountInput;
    private AmountInput limitInput;
    private View limitAmountView;
    private EditText accountTitle;
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
    private View openingAmountNode; // To show/hide opening amount

    private EntityEnumAdapter<AccountType> accountTypeAdapter;
    private EntityEnumAdapter<CardIssuer> cardIssuerAdapter;
    private EntityEnumAdapter<ElectronicPaymentType> electronicPaymentAdapter;
    // private ListAdapter currencyAdapter; // Will be ArrayAdapter<CurrencyEntity> or similar for dialog
    private ArrayAdapter<String> currencyDialogAdapter;
    private List<CurrencyEntity> localCurrencyList = new ArrayList<>();


    // private Account account = new Account(); // Replaced by ViewModel's state
    private AccountViewModel accountViewModel;
    private CurrencyDao currencyDao; // Added

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account);

        accountViewModel = new ViewModelProvider(this).get(AccountViewModel.class);
        try {
            currencyDao = KoinJavaComponent.get(CurrencyDao.class); // Get CurrencyDao from Koin
        } catch (Exception e) {
            // Handle error - Koin not started or CurrencyDao not found. This is critical.
            Toast.makeText(this, "Error initializing database components.", Toast.LENGTH_LONG).show();
            finish(); // Or some other error handling
            return;
        }


        setupViews(); // Initialize UI elements
        setupListeners(); // Setup listeners for UI elements to update ViewModel

        long accountId = getIntent().getLongExtra(ACCOUNT_ID_EXTRA, -1L);
        accountViewModel.loadAccount(accountId == -1L ? null : accountId);

        observeUiState();
        observeCurrencies();
    }

    private void setupViews() {
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
        // amountInput.setOwner(this); // Review if still needed with ViewModel

        limitInput = AmountInput_.build(this);
        // limitInput.setOwner(this); // Review if still needed

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

        // Currency selection setup
        // currencyCursor = db.getAllCurrencies("name"); // Replaced by ViewModel
        // startManagingCursor(currencyCursor); // No cursor
        // currencyAdapter = TransactionUtils.createCurrencyAdapter(this, currencyCursor); // Replaced
        currencyDialogAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());


        activityLayout.addEditNode(layout, R.string.title, accountTitle);
        currencyText = activityLayout.addListNodePlus(layout, R.id.currency, R.id.currency_add, R.string.currency, R.string.select_currency);

        limitInput.setExpense();
        limitInput.disableIncomeExpenseButton();
        limitAmountView = activityLayout.addEditNode(layout, R.string.limit_amount, limitInput);
        setVisibility(limitAmountView, View.GONE);

        openingAmountNode = activityLayout.addEditNode(layout, R.string.opening_amount, amountInput);
        // Visibility handled by uiState observer based on isNewAccount
        amountInput.setIncome();


        noteText = new EditText(this);
        noteText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        noteText.setLines(2);
        activityLayout.addEditNode(layout, R.string.note, noteText);

        activityLayout.addEditNode(layout, R.string.sort_order, sortOrderText);
        isIncludedIntoTotals = activityLayout.addCheckboxNode(layout,
                R.id.is_included_into_totals, R.string.is_included_into_totals,
                R.string.is_included_into_totals_summary, true);

        Button bOK = findViewById(R.id.bOK);
        bOK.setOnClickListener(arg0 -> accountViewModel.saveAccount());

        Button bCancel = findViewById(R.id.bCancel);
        bCancel.setOnClickListener(arg0 -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private void observeUiState() {
        lifecycleScope.launch(() -> {
            accountViewModel.getUiState().collect(new FlowCollector<AccountFormState>() {
                @Override
                public Object emit(AccountFormState state, kotlin.coroutines.Continuation<? super Unit> continuation) {
                    AccountEntity account = state.getAccount();

                    // Update fields only if they are not focused to avoid cursor jumps
                    if (!accountTitle.hasFocus()) accountTitle.setText(account.getTitle());
                    if (!issuerName.hasFocus()) issuerName.setText(account.getIssuer());
                    if (!numberText.hasFocus()) numberText.setText(account.getNumber());
                    if (!sortOrderText.hasFocus()) sortOrderText.setText(String.valueOf(account.getSortOrder()));
                    if (!noteText.hasFocus()) noteText.setText(account.getNote());
                    if (!closingDayText.hasFocus()) closingDayText.setText(account.getClosingDay() > 0 ? String.valueOf(account.getClosingDay()) : "");
                    if (!paymentDayText.hasFocus()) paymentDayText.setText(account.getPaymentDay() > 0 ? String.valueOf(account.getPaymentDay()) : "");

                    isIncludedIntoTotals.setChecked(account.isIncludeIntoTotals());
                    // AmountInput expects negative for credit limit display
                    if (limitInput.getAmount() != -Math.abs(account.getLimitAmount())) { // Only update if different
                         limitInput.setAmount(-Math.abs(account.getLimitAmount()));
                    }


                    if (state.isNewAccount()) {
                        openingAmountNode.setVisibility(View.VISIBLE);
                        if (amountInput.getAmount() != state.getOpeningBalance()) { // Only update if different
                            amountInput.setAmount(state.getOpeningBalance());
                        }
                    } else {
                        openingAmountNode.setVisibility(View.GONE);
                    }

                    AccountType currentType = AccountType.GENERAL; // Default
                    if (account.getType() != null) {
                        try { currentType = AccountType.valueOf(account.getType()); } catch (Exception e) {/* use default */}
                    }
                    selectAccountTypeUIUpdate(currentType);

                    // Currency text updated by observeCurrencies based on account.currencyId

                    if (state.getErrorResId() != null) {
                        Toast.makeText(AccountActivity.this, getString(state.getErrorResId()), Toast.LENGTH_LONG).show();
                        accountViewModel.consumeError();
                    }

                    Event<SaveResult> saveResultEvent = state.getSaveResult();
                    if (saveResultEvent != null) {
                        SaveResult result = saveResultEvent.getContentIfNotHandled();
                        if (result != null && result.getSuccess()) {
                            AccountWidget.updateWidgets(AccountActivity.this);
                            Intent intentResult = new Intent();
                            intentResult.putExtra(ACCOUNT_ID_EXTRA, result.getAccountId());
                            setResult(RESULT_OK, intentResult);
                            finish();
                        }
                    }
                    return Unit.INSTANCE;
                }
            });
        });
    }

    private void observeCurrencies() {
        lifecycleScope.launch(() -> {
            accountViewModel.getCurrencies().collect(new FlowCollector<List<CurrencyEntity>>() {
                @Override
                public Object emit(List<CurrencyEntity> currencyList, kotlin.coroutines.Continuation<? super Unit> continuation) {
                    localCurrencyList.clear();
                    localCurrencyList.addAll(currencyList);

                    List<String> currencyNames = currencyList.stream().map(CurrencyEntity::getName).collect(Collectors.toList());
                    currencyDialogAdapter.clear();
                    currencyDialogAdapter.addAll(currencyNames);
                    currencyDialogAdapter.notifyDataSetChanged();

                    if (!currencyList.isEmpty()) {
                        AccountEntity currentAccount = accountViewModel.getUiState().getValue().getAccount();
                        CurrencyEntity selectedCurrency = currencyList.stream()
                            .filter(c -> c.getId() == currentAccount.getCurrencyId())
                            .findFirst().orElse(null);

                        if (selectedCurrency != null) {
                            currencyText.setText(selectedCurrency.getName());
                            amountInput.setCurrency(CurrencyEntityMapper.toModel(selectedCurrency));
                            limitInput.setCurrency(CurrencyEntityMapper.toModel(selectedCurrency));
                        } else if (currentAccount.getCurrencyId() == 0L && !currencyList.isEmpty()) {
                            // This case handles when ViewModel auto-selects a default currency for a new account
                            CurrencyEntity defaultSelected = currencyList.stream()
                                .filter(c -> c.getId() == currentAccount.getCurrencyId()) // Find the one ViewModel picked
                                .findFirst().orElse(currencyList.get(0)); // Or fallback
                            currencyText.setText(defaultSelected.getName());
                            amountInput.setCurrency(CurrencyEntityMapper.toModel(defaultSelected));
                            limitInput.setCurrency(CurrencyEntityMapper.toModel(defaultSelected));
                        }
                    }
                    return Unit.INSTANCE;
                }
            });
        });
    }

    private void setupListeners() {
        accountTitle.addTextChangedListener(new TextWatcherAdapter(s -> accountViewModel.updateAccountField(acc -> acc.copy(title = s))));
        issuerName.addTextChangedListener(new TextWatcherAdapter(s -> accountViewModel.updateAccountField(acc -> acc.copy(issuer = s))));
        numberText.addTextChangedListener(new TextWatcherAdapter(s -> accountViewModel.updateAccountField(acc -> acc.copy(number = s))));
        noteText.addTextChangedListener(new TextWatcherAdapter(s -> accountViewModel.updateAccountField(acc -> acc.copy(note = s))));
        sortOrderText.addTextChangedListener(new TextWatcherAdapter(s -> {
            try {
                int sortOrder = s.isEmpty() ? 0 : Integer.parseInt(s);
                accountViewModel.updateAccountField(acc -> acc.copy(sortOrder = sortOrder));
            } catch (NumberFormatException e) { /* ignore */ }
        }));
        closingDayText.addTextChangedListener(new TextWatcherAdapter(s -> {
            try {
                int day = s.isEmpty() ? 0 : Integer.parseInt(s);
                accountViewModel.updateAccountField(acc -> acc.copy(closingDay = day));
            } catch (NumberFormatException e) { /* ignore */ }
        }));
        paymentDayText.addTextChangedListener(new TextWatcherAdapter(s -> {
            try {
                int day = s.isEmpty() ? 0 : Integer.parseInt(s);
                accountViewModel.updateAccountField(acc -> acc.copy(paymentDay = day));
            } catch (NumberFormatException e) { /* ignore */ }
        }));

        amountInput.setListener(amount -> {
            if (accountViewModel.getUiState().getValue().isNewAccount()) {
                accountViewModel.setOpeningBalance(amount);
            } // For existing accounts, totalAmount is not edited directly here.
            return Unit.INSTANCE;
        });
        limitInput.setListener(amount -> {
            // Limit is stored positive, but AmountInput might handle it as negative for display
            accountViewModel.updateAccountField(acc -> acc.copy(limitAmount = Math.abs(amount)));
            return Unit.INSTANCE;
        });

        isIncludedIntoTotals.setOnCheckedChangeListener((buttonView, isChecked) ->
            accountViewModel.updateAccountField(acc -> acc.copy(isIncludeIntoTotals = isChecked))
        );
    }


    @Override
    protected void onClick(View v, int id) {
        switch (id) {
            case R.id.is_included_into_totals:
                isIncludedIntoTotals.performClick();
                break;
            case R.id.account_type:
                String currentAccountType = accountViewModel.getUiState().getValue().getAccount().getType();
                int currentOrdinal = AccountType.GENERAL.ordinal(); // Default
                if (currentAccountType != null) {
                    try { currentOrdinal = AccountType.valueOf(currentAccountType).ordinal(); } catch (Exception e) {/* use default */}
                }
                activityLayout.selectPosition(this, R.id.account_type, R.string.account_type, accountTypeAdapter, currentOrdinal);
                break;
            case R.id.card_issuer:
                String currentIssuer = accountViewModel.getUiState().getValue().getAccount().getIssuer();
                int issuerOrdinal = 0;
                if (currentIssuer != null) {
                    try { issuerOrdinal = CardIssuer.valueOf(currentIssuer).ordinal(); } catch (Exception e) {/* use default */}
                }
                activityLayout.selectPosition(this, R.id.card_issuer, R.string.card_issuer, cardIssuerAdapter, issuerOrdinal);
                break;
            case R.id.electronic_payment_type:
                String currentElectronicIssuer = accountViewModel.getUiState().getValue().getAccount().getIssuer();
                int electronicOrdinal = ElectronicPaymentType.PAYPAL.ordinal(); // Default
                 if (currentElectronicIssuer != null) {
                    try { electronicOrdinal = ElectronicPaymentType.valueOf(currentElectronicIssuer).ordinal(); } catch (Exception e) {/* use default */}
                }
                activityLayout.selectPosition(this, R.id.electronic_payment_type, R.string.electronic_payment_type, electronicPaymentAdapter, electronicOrdinal);
                break;
            case R.id.currency:
                // Show a dialog with currencies from localCurrencyList
                if (!localCurrencyList.isEmpty()) {
                    String[] currencyNames = localCurrencyList.stream().map(CurrencyEntity::getName).toArray(String[]::new);
                    new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.select_currency)
                        .setItems(currencyNames, (dialog, which) -> {
                            onSelectedId(R.id.currency, localCurrencyList.get(which).getId());
                        })
                        .show();
                }
                break;
            case R.id.currency_add:
                addNewCurrency();
                break;
        }
    }

    private void addNewCurrency() {
        // Pass currencyDao to CurrencySelector
        new CurrencySelector(this, currencyDao, currencyId -> {
            if (currencyId == 0) { // User selected "New Currency" or an error occurred in selector
                Intent intent = new Intent(AccountActivity.this, CurrencyActivity.class);
                startActivityForResult(intent, NEW_CURRENCY_REQUEST);
            } else {
                // currencyCursor.requery(); // No cursor
                // This path might be obsolete if dialog shows all currencies or ViewModel handles new ones.
                // For now, assume new currency is added and then list refreshes via ViewModel.
                accountViewModel.updateAccountField(acc -> acc.copy(currencyId = currencyId));
            }
        }).show(); // This CurrencySelector needs refactoring to not use DB directly.
    }

    @Override
    public void onSelectedId(int id, long selectedId) {
        if (id == R.id.currency) {
            // selectCurrency(selectedId); // Old method
            accountViewModel.updateAccountField(acc -> acc.copy(currencyId = selectedId));
        }
    }

    @Override
    public void onSelectedPos(int id, int selectedPos) {
        switch (id) {
            case R.id.account_type:
                AccountType type = AccountType.values()[selectedPos];
                // selectAccountType(type); // Old method
                accountViewModel.updateAccountField(acc -> acc.copy(type = type.name()));
                // UI updates (visibility changes) will be handled by uiState observer calling selectAccountTypeUIUpdate
                break;
            case R.id.card_issuer:
                CardIssuer issuer = CardIssuer.values()[selectedPos];
                // selectCardIssuer(issuer); // Old method
                accountViewModel.updateAccountField(acc -> acc.copy(issuer = issuer.name()));
                break;
            case R.id.electronic_payment_type:
                ElectronicPaymentType paymentType = ElectronicPaymentType.values()[selectedPos];
                // selectElectronicType(paymentType); // Old method
                accountViewModel.updateAccountField(acc -> acc.copy(issuer = paymentType.name()));
                break;
        }
    }

    private void selectAccountTypeUIUpdate(AccountType type) { // Renamed from selectAccountType
        ImageView icon = accountTypeNode.findViewById(R.id.icon);
        icon.setImageResource(type.getIconId());
        TextView label = accountTypeNode.findViewById(R.id.label);
        label.setText(type.getTitleId());

        setVisibility(cardIssuerNode, type.isCard() ? View.VISIBLE : View.GONE);
        setVisibility(issuerNode, type.getHasIssuer() && !type.isCard() && !type.isElectronic() ? View.VISIBLE : View.GONE); // Adjusted logic
        setVisibility(electronicPaymentNode, type.isElectronic() ? View.VISIBLE : View.GONE);
        setVisibility(numberNode, type.getHasNumber() ? View.VISIBLE : View.GONE);
        setVisibility(closingDayNode, type.isCreditCard() ? View.VISIBLE : View.GONE);
        setVisibility(paymentDayNode, type.isCreditCard() ? View.VISIBLE : View.GONE);
        setVisibility(limitAmountView, type == AccountType.CREDIT_CARD ? View.VISIBLE : View.GONE);

        // The following lines that modify account state are removed, ViewModel handles state.
        // account.type = type.name();
        // if (type.isCard()) {
        // selectCardIssuerUIUpdate(selectEnum(CardIssuer.class, accountViewModel.getUiState().getValue().getAccount().getIssuer(), CardIssuer.DEFAULT));
        // } else if (type.isElectronic()) {
        // selectElectronicTypeUIUpdate(selectEnum(ElectronicPaymentType.class, accountViewModel.getUiState().getValue().getAccount().getIssuer(), ElectronicPaymentType.PAYPAL));
        // } else {
        // accountViewModel.updateAccountField(acc -> acc.copy(issuer = null));
        // }
    }

    // selectCardIssuer and selectElectronicType are effectively replaced by onSelectedPos logic
    // and UI updates handled by selectAccountTypeUIUpdate via uiState observation.
    // Helper methods to update node UI directly are still fine if needed for immediate feedback before state propagates.
    private void updateNode(View node, EntityEnum enumItem) {
        ImageView icon = node.findViewById(R.id.icon);
        icon.setImageResource(enumItem.getIconId());
        TextView label = node.findViewById(R.id.label);
        label.setText(enumItem.getTitleId());
    }

    // selectCurrency(long currencyId) and selectCurrency(Currency c) are replaced by onSelectedId and uiState observation.

    // editAccount() method is removed. UI population is handled by uiState observer.

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == NEW_CURRENCY_REQUEST) {
                // currencyCursor.requery(); // No cursor
                long currencyId = data.getLongExtra(CurrencyActivity.CURRENCY_ID_EXTRA, -1L);
                if (currencyId != -1L) {
                    // selectCurrency(currencyId); // Old method
                    accountViewModel.updateAccountField(acc -> acc.copy(currencyId = currencyId));
                }
            }
        }
    }

    // Helper for TextWatcher
    private static class TextWatcherAdapter implements TextWatcher {
        private final java.util.function.Consumer<String> onTextChanged;
        TextWatcherAdapter(java.util.function.Consumer<String> onTextChanged) { this.onTextChanged = onTextChanged; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { onTextChanged.accept(s.toString()); }
        @Override public void afterTextChanged(Editable s) {}
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
