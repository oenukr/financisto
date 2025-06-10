package ru.orangesoftware.financisto.activity;

import static ru.orangesoftware.financisto.activity.CategorySelector.SelectorType.TRANSACTION;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.lifecycleScope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import greendroid.widget.QuickActionGrid;
import greendroid.widget.QuickActionWidget;
import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.db.entity.AccountEntity;
import ru.orangesoftware.financisto.db.entity.CurrencyEntity;
import ru.orangesoftware.financisto.db.entity.TransactionEntity;
import ru.orangesoftware.financisto.mapper.CurrencyEntityMapper;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.Category;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.utils.Logger;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.utils.Utils;
import kotlinx.coroutines.flow.FlowCollector;
import kotlin.Unit;
import ru.orangesoftware.financisto.viewmodel.TransactionViewModel;
import ru.orangesoftware.financisto.viewmodel.TransactionFormState;
import ru.orangesoftware.financisto.viewmodel.Event;
import ru.orangesoftware.financisto.widget.AmountInput;

public class TransactionActivity extends AbstractTransactionActivity {

    private final Logger logger = new DependenciesHolder().getLogger();
    private TransactionViewModel transactionViewModel;

    public static final String CURRENT_BALANCE_EXTRA = "accountCurrentBalance";
    public static final String AMOUNT_EXTRA = "accountAmount";
    public static final String ACTIVITY_STATE = "ACTIVITY_STATE";

    private static final int SPLIT_REQUEST = 5001;

    private final Currency currencyAsAccount = new Currency();

    private long idSequence = 0;
    private final IdentityHashMap<View, TransactionEntity> viewToSplitMap = new IdentityHashMap<>();

    private TextView differenceText;
    private boolean isUpdateBalanceModeActivityCopy = false;
    private Utils u;

    private LinearLayout splitsLayout;
    private TextView unsplitAmountText;

    private QuickActionWidget unsplitActionGrid;

    public TransactionActivity() {
        // Constructor
    }

    @Override
    protected int getLayoutId() {
        return MyPreferences.isUseFixedLayout(this) ? R.layout.transaction_fixed : R.layout.transaction_free;
    }

    @Override
    protected void internalOnCreate() {
        u = new Utils(this);
        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        Intent intent = getIntent();
        long transactionIdToLoad = intent.getLongExtra(TRAN_ID_EXTRA, 0L);
        long accountIdForNew = intent.getLongExtra(ACCOUNT_ID_EXTRA, 0L);
        Long currentBalanceExtra = intent.hasExtra(CURRENT_BALANCE_EXTRA) ? intent.getLongExtra(CURRENT_BALANCE_EXTRA, 0L) : null;
        Long amountExtra = intent.hasExtra(AMOUNT_EXTRA) ? intent.getLongExtra(AMOUNT_EXTRA, 0L) : null;

        transactionViewModel.loadTransaction(transactionIdToLoad, accountIdForNew, currentBalanceExtra, amountExtra);

        observeUiState();

        prepareUnsplitActionGrid();
        currencyAsAccount.name = getString(R.string.original_currency_as_account);
    }

    private void observeUiState() {
        lifecycleScope.launch(() -> {
            transactionViewModel.getUiState().collect(new FlowCollector<TransactionFormState>() {
                @Override
                public Object emit(TransactionFormState state, kotlin.coroutines.Continuation<? super Unit> continuation) {
                    TransactionEntity currentTx = state.getMainTransaction();
                    isUpdateBalanceModeActivityCopy = state.isUpdateBalanceMode();

                    if (accountText != null) {
                        accountText.setText(state.getSelectedAccountTitle() != null ? state.getSelectedAccountTitle() : getString(R.string.select_account));
                    }

                    if (categorySelector != null) {
                        // Pass full tree and path map to CategorySelector
                        categorySelector.setCategoryData(
                            state.getAvailableCategoriesTree(),
                            state.getCategoryDisplayPathMap()
                        );
                        // Then update its displayed selection from state
                        categorySelector.selectCategoryPath(state.getSelectedCategoryPath());
                    }

                    if (payeeSelector != null && isShowPayee) {
                         if ((state.getSelectedPayeeId() == null && payeeSelector.getSelectedEntityId() != 0) ||
                            (state.getSelectedPayeeId() != null && payeeSelector.getSelectedEntityId() != state.getSelectedPayeeId())) {
                            payeeSelector.selectEntity(state.getSelectedPayeeId() != null ? state.getSelectedPayeeId() : 0L);
                        }
                    }

                    if (dateText != null && timeText != null) {
                        setDateTime(currentTx.getDateTime());
                    }

                    if (noteText != null && currentTx.getNote() != null && !noteText.getText().toString().equals(currentTx.getNote())) {
                        noteText.setText(currentTx.getNote());
                    }

                    if (rateView != null) {
                        CurrencyEntity accountCurrencyEntity = state.getAvailableCurrencies().stream()
                            .filter(c -> c.getId() == state.getSelectedAccountCurrencyId()).findFirst().orElse(null);

                        rateView.setCurrencyFromDetails(accountCurrencyEntity, accountCurrencyEntity != null ? (accountCurrencyEntity.getSymbol() != null ? accountCurrencyEntity.getSymbol() : accountCurrencyEntity.getName()) : "");

                        if (state.getSelectedOriginalCurrencyId() != null && state.getSelectedOriginalCurrencyId() != 0L &&
                            state.getSelectedOriginalCurrencyId() != state.getSelectedAccountCurrencyId()) {
                            CurrencyEntity origCurrEntity = state.getAvailableCurrencies().stream()
                                .filter(c -> c.getId() == state.getSelectedOriginalCurrencyId()).findFirst().orElse(null);
                            rateView.setCurrencyToDetails(origCurrEntity, origCurrEntity != null ? (origCurrEntity.getSymbol() != null ? origCurrEntity.getSymbol() : origCurrEntity.getName()) : "");
                            if (currencyText != null) currencyText.setText(origCurrEntity != null ? origCurrEntity.getName() : getString(R.string.select_currency));
                        } else {
                            rateView.setCurrencyToDetails(accountCurrencyEntity, accountCurrencyEntity != null ? (accountCurrencyEntity.getSymbol()!= null ? accountCurrencyEntity.getSymbol() : accountCurrencyEntity.getName()) : ""); // Same as from currency
                            if (currencyText != null) currencyText.setText(R.string.original_currency_as_account);
                        }

                        if (rateView.getFromAmount() != state.getRateViewFromAmount()) {
                             rateView.setFromAmount(state.getRateViewFromAmount());
                        }
                        if (rateView.isDifferentCurrenciesConfigured()) {
                            if (rateView.getToAmount() != state.getRateViewToAmount()) {
                                 rateView.setToAmount(state.getRateViewToAmount());
                            }
                        }
                    }

                     if (currentTx.isTemplateLike()) {
                        setTitle(currentTx.isTemplate() ? R.string.transaction_template : R.string.transaction_schedule);
                        if (currentTx.isTemplate() && dateText != null && timeText != null) {
                            dateText.setEnabled(false);
                            timeText.setEnabled(false);
                        }
                    } else {
                        setTitle(R.string.transaction);
                         if (dateText != null && timeText != null) {
                             dateText.setEnabled(true);
                             timeText.setEnabled(true);
                        }
                    }

                    if (state.isUpdateBalanceMode()) {
                        if (differenceText != null) {
                            differenceText.setVisibility(View.VISIBLE);
                            if (rateView != null && rateView.getCurrencyFrom() != null)
                                u.setAmountText(differenceText, rateView.getCurrencyFrom(), state.getDifferenceForUpdateMode(), true);
                        }
                    } else {
                        if (differenceText != null) differenceText.setVisibility(View.GONE);
                    }

                    if (splitsLayout != null) {
                        splitsLayout.setVisibility(state.isSplitCategorySelected() ? View.VISIBLE : View.GONE);
                        if (state.isSplitCategorySelected()) {
                             updateSplitsLayout(state.getSplits());
                             updateUnsplitAmountText(state.getUnsplitAmount(), rateView != null ? rateView.getCurrencyFrom() : null);
                        }
                    }

                    if (!state.getErrorMessages().isEmpty()) {
                        Toast.makeText(TransactionActivity.this, String.join("\n", state.getErrorMessages()), Toast.LENGTH_LONG).show();
                        transactionViewModel.consumeErrorMessages();
                    }

                    Event<Boolean> saveEvent = state.getSaveEvent();
                    if (saveEvent != null) {
                        Boolean success = saveEvent.getContentIfNotHandled();
                        if (success != null && success) {
                            setResult(RESULT_OK);
                            finish();
                        }
                    }
                    return Unit.INSTANCE;
                }
            });
        });
    }

    private void prepareUnsplitActionGrid() {
        unsplitActionGrid = new QuickActionGrid(this);
        unsplitActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_add, R.string.transaction));
        unsplitActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_transfer, R.string.transfer));
        unsplitActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_tick, R.string.unsplit_adjust_amount));
        unsplitActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_tick, R.string.unsplit_adjust_evenly));
        unsplitActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_tick, R.string.unsplit_adjust_last));
        unsplitActionGrid.setOnQuickActionClickListener(unsplitActionListener);
    }

    private final QuickActionWidget.OnQuickActionClickListener unsplitActionListener = (widget, position) -> {
        switch (position) {
            case 0:
                transactionViewModel.addSplit(false);
                break;
            case 1:
                transactionViewModel.addSplit(true);
                break;
        }
    };

    @Override
    protected void fetchCategories() {
        // ViewModel loads categories.
    }

    @Override
    protected void createListNodes(LinearLayout layout) {
        // AbstractTransactionActivity's createListNodes will call categorySelector.createNode()
        // We need to set the listener on categorySelector, which is initialized in Abstract's onCreate
        if (categorySelector != null) {
            categorySelector.setListener(new CategorySelector.CategorySelectorListener() {
                @Override
                public void onCategorySelected(@Nullable CategoryEntity category, @Nullable String categoryPath, boolean selectLast) {
                    TransactionActivity.this.onCategorySelected(category, categoryPath, selectLast);
                }
            });
        }

        super.createListNodes(layout); // Let Abstract add its common nodes including category node

        if (transactionViewModel.getUiState().getValue().isUpdateBalanceMode()) {
             if (differenceText == null) {
                differenceText = activityLayout.addInfoNode(layout, -1, R.string.difference, "0");
             }
        } else {
            createSplitsLayout(layout);
        }

        if (rateView != null) {
            rateView.setAmountFromChangeListener((oldAmount, newAmount) -> {
                if (transactionViewModel.getUiState().getValue().getRateViewFromAmount() != newAmount ||
                    (rateView.isDifferentCurrenciesConfigured() && transactionViewModel.getUiState().getValue().getRateViewToAmount() != rateView.getToAmount())) {
                    transactionViewModel.updateRateViewAmounts(
                        rateView.getFromAmount(),
                        rateView.isDifferentCurrenciesConfigured() ? rateView.getToAmount() : null
                    );
                }
            });
            rateView.setAmountToChangeListener((oldAmount, newAmount) -> {
                 if (transactionViewModel.getUiState().getValue().getRateViewToAmount() != newAmount ||
                     transactionViewModel.getUiState().getValue().getRateViewFromAmount() != rateView.getFromAmount()) {
                    transactionViewModel.updateRateViewAmounts(
                        rateView.getFromAmount(),
                        rateView.isDifferentCurrenciesConfigured() ? rateView.getToAmount() : null
                    );
                }
            });
        }
        if (noteText != null) {
            noteText.addTextChangedListener(new TextWatcherAdapter(s ->
                transactionViewModel.updateMainTransaction(tx -> tx.copy(note = s))
            ));
        }
    }

    private void createSplitsLayout(LinearLayout layout) {
        if (splitsLayout == null) {
            splitsLayout = new LinearLayout(this);
            splitsLayout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(splitsLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (splitsLayout.findViewWithTag("unsplit_node") == null) {
            View v = activityLayout.addNodeUnsplit(splitsLayout);
            v.setTag("unsplit_node");
            unsplitAmountText = v.findViewById(R.id.data);
        }
        splitsLayout.setVisibility(View.GONE);
    }

    @Override
    protected void addOrRemoveSplits() {
        // UI update handled by observeUiState
    }

    private void updateSplitsLayout(List<TransactionEntity> splits) {
        if (splitsLayout == null || !transactionViewModel.getUiState().getValue().isSplitCategorySelected()) {
            if (splitsLayout != null) splitsLayout.removeAllViews();
            viewToSplitMap.clear();
            return;
        }
        View unsplitNode = splitsLayout.findViewWithTag("unsplit_node");
        splitsLayout.removeAllViews();
        viewToSplitMap.clear();

        if (unsplitNode != null) {
            splitsLayout.addView(unsplitNode);
        } else {
            View v = activityLayout.addNodeUnsplit(splitsLayout);
            v.setTag("unsplit_node");
            unsplitAmountText = v.findViewById(R.id.data);
            splitsLayout.addView(v);
        }

        for (TransactionEntity split : splits) {
            View v = activityLayout.addSplitNodeMinus(splitsLayout, R.id.edit_aplit, R.id.delete_split, R.string.split, "");
            setSplitDataUi(v, split);
            viewToSplitMap.put(v, split);
        }
    }

    private void updateUnsplitAmountText(long amount, Currency currency) {
        if (unsplitAmountText != null) {
            if (currency != null) {
                u.setAmountText(unsplitAmountText, currency, amount, false);
            } else {
                unsplitAmountText.setText(Utils.formatAmount(amount / 100.0, 2));
            }
        }
    }

    @Override
    protected void switchIncomeExpenseButton(Category category) {
        super.switchIncomeExpenseButton(category);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected boolean onOKClicked() {
        transactionViewModel.saveTransaction();
        return false;
    }

    @Override
    protected void editTransaction(ru.orangesoftware.financisto.model.Transaction transaction_old_model) {
        super.editTransaction(transaction_old_model);
    }

    protected void commonEditTransaction(TransactionEntity transactionEntity) {
        if (transactionEntity == null) return;
        selectStatus(TransactionStatus.valueOf(transactionEntity.getStatus() == null ? TransactionStatus.EMPTY.name() : transactionEntity.getStatus()));
        if (categorySelector != null) categorySelector.selectCategory(transactionEntity.getCategoryId());
        if (projectSelector != null) projectSelector.selectEntity(transactionEntity.getProjectId());
        if (locationSelector != null) locationSelector.selectEntity(transactionEntity.getLocationId());
        setDateTime(transactionEntity.getDateTime());
        if (noteText != null) noteText.setText(transactionEntity.getNote());
        if (templateName != null && transactionEntity.isTemplate()) {
            templateName.setText(transactionEntity.getTemplateName());
        }
    }

    @Override
    protected Account selectAccount(long accountId, boolean selectLast) {
        transactionViewModel.selectAccount(accountId);
        TransactionFormState currentState = transactionViewModel.getUiState().getValue();
        AccountEntity entity = currentState.getAvailableAccounts().stream().filter(e -> e.getId() == accountId).findFirst().orElse(null);
        Account oldModelAccount = mapToOldAccount(entity);
        if (oldModelAccount != null) {
            selectedAccount = oldModelAccount;
        }
        return oldModelAccount;
    }

    @Override
    protected void onClick(View v, int id) {
        super.onClick(v, id);
        switch (id) {
            case R.id.unsplit_action:
                if (unsplitActionGrid != null) unsplitActionGrid.show(v);
                break;
            case R.id.delete_split:
                View parentView = (View) v.getParent();
                TransactionEntity splitToDelete = viewToSplitMap.get(parentView);
                if (splitToDelete != null) {
                    transactionViewModel.deleteSplit(splitToDelete.getId());
                }
                break;
            default:
                TransactionEntity splitToEdit = viewToSplitMap.get(v);
                if (splitToEdit != null) {
                    // TODO: Launch SplitEditActivity using splitToEdit.getId()
                }
                break;
        }
    }

    @Override
    public void onCategorySelected(@Nullable CategoryEntity category, @Nullable String categoryPath, boolean selectLast) {
        // This method is now the direct listener for CategorySelector if set up correctly.
        // Or it's called by AbstractTransactionActivity if that class implements the listener
        // and calls this override.

        // Update ViewModel
        transactionViewModel.selectCategory(
            category != null ? category.getId() : CategorySelector.NO_CATEGORY_ID_CONSTANT,
            categoryPath != null ? categoryPath : getString(R.string.no_category)
        );

        // Logic from AbstractTransactionActivity.onCategorySelected that might still be relevant here
        // or should be fully managed by observing ViewModel state changes.
        if (category != null) {
            if (selectLast && isShowProject && projectSelector != null && category.getLastProjectId() > 0) {
                projectSelector.selectEntity(category.getLastProjectId());
            }
            if (selectLast && isShowLocation && locationSelector != null && category.getLastLocationId() > 0) {
                locationSelector.selectEntity(category.getLastLocationId());
            }
        }
        // switchIncomeExpenseButton and addOrRemoveSplits are called from observeUiState based on ViewModel
    }


    @Override
    public void onSelectedPos(int id, int selectedPos) {
        super.onSelectedPos(id, selectedPos);
        if (id == R.id.payee && payeeSelector != null) {
             transactionViewModel.selectPayee(payeeSelector.getSelectedEntityId());
        }
    }

    @Override
    public void onSelectedId(int id, long selectedId) {
        super.onSelectedId(id, selectedId);
        if (id == R.id.original_currency) {
            transactionViewModel.selectOriginalCurrency(selectedId == -1L ? null : selectedId);
        }
    }

    @Override
    protected void selectPayee(long payeeId) {
        super.selectPayee(payeeId);
        transactionViewModel.selectPayee(payeeId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPLIT_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                // TODO: Handle result from SplitEditActivity.
            }
        }
    }

    private void setSplitDataUi(View v, TransactionEntity split) {
        TextView label = v.findViewById(R.id.label);
        TextView data = v.findViewById(R.id.data);
        TransactionFormState formState = transactionViewModel.getUiState().getValue();

        String catName = String.valueOf(split.getCategoryId());
        if (formState != null && formState.getAvailableCategoriesTree() != null) {
            CategoryEntity category = formState.getAvailableCategoriesTree()
                                    .stream().filter(c -> c.getId() == split.getCategoryId()).findFirst().orElse(null);
            if (category != null) catName = category.getTitle();
        }

        if (split.getToAccountId() > 0) {
            String toAccName = String.valueOf(split.getToAccountId());
             if (formState != null && formState.getAvailableAccounts() != null) {
                AccountEntity toAccount = formState.getAvailableAccounts()
                                        .stream().filter(a -> a.getId() == split.getToAccountId()).findFirst().orElse(null);
                if (toAccount != null) toAccName = toAccount.getTitle();
            }
            label.setText(String.format("%s -> %s", catName, toAccName));
        } else {
            label.setText(catName);
        }

        Currency displayCurrency = null;
        if (rateView != null) displayCurrency = rateView.getCurrencyFrom();

        if (displayCurrency != null) {
             u.setAmountText(data, displayCurrency, split.getFromAmount(), false);
        } else {
            data.setText(Utils.formatAmount(split.getFromAmount() / 100.0, 2));
        }
    }

    private Account mapToOldAccount(AccountEntity entity) {
        if (entity == null) return null;
        Account acc = new Account();
        acc.id = entity.getId();
        acc.title = entity.getTitle();
        acc.currencyId = entity.getCurrencyId();
        TransactionFormState currentState = transactionViewModel.getUiState().getValue();
        if (currentState != null) {
            CurrencyEntity currEntity = currentState.getAvailableCurrencies().stream()
                                        .filter(c -> c.getId() == entity.getCurrencyId()).findFirst().orElse(null);
            acc.currency = CurrencyEntityMapper.toModel(currEntity);
        }
        return acc;
    }

    // TextWatcher adapter for cleaner listeners
    private static class TextWatcherAdapter implements TextWatcher {
        private final java.util.function.Consumer<String> onTextChanged;
        TextWatcherAdapter(java.util.function.Consumer<String> onTextChanged) { this.onTextChanged = onTextChanged; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { onTextChanged.accept(s.toString()); }
        @Override public void afterTextChanged(Editable s) {}
    }
}
```
