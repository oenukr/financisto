/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * <p/>
 * Contributors:
 * Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.activity;

import static ru.orangesoftware.financisto.utils.MyPreferences.isQuickMenuEnabledForAccount;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
// import android.database.Cursor; // No longer using Cursor directly for list
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ImageButton;
// import android.widget.ListAdapter; // Using AccountEntityAdapter now
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.lifecycleScope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greendroid.widget.QuickActionGrid;
import greendroid.widget.QuickActionWidget;
import ru.orangesoftware.financisto.R;
// import ru.orangesoftware.financisto.adapter.AccountListAdapter2; // Replaced
import ru.orangesoftware.financisto.adapter.AccountEntityAdapter; // New Adapter
import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.blotter.BlotterFilter;
// import ru.orangesoftware.financisto.blotter.TotalCalculationTask; // Removed
import ru.orangesoftware.financisto.bus.SwitchToMenuTabEvent;
// import ru.orangesoftware.financisto.db.DatabaseAdapter; // ViewModel handles DB access
import ru.orangesoftware.financisto.db.entity.AccountEntity; // New Entity
import ru.orangesoftware.financisto.dialog.AccountInfoDialog; // May need refactor
import ru.orangesoftware.financisto.filter.Criteria;
// import ru.orangesoftware.financisto.model.Account; // Replaced by AccountEntity where possible
import ru.orangesoftware.financisto.model.Total; // Now used for ViewModel's totalBalance
import ru.orangesoftware.financisto.utils.IntegrityCheckAutobackup;
import ru.orangesoftware.financisto.utils.MenuItemInfo;
import ru.orangesoftware.financisto.utils.MyPreferences;
// import ru.orangesoftware.financisto.utils.Utils; // May be needed for formatting Total
import ru.orangesoftware.financisto.view.NodeInflater;
import ru.orangesoftware.financisto.viewmodel.AccountDisplayData; // Added
import ru.orangesoftware.financisto.viewmodel.AccountListViewModel;

import kotlinx.coroutines.flow.FlowCollector;
import kotlin.Unit;

public class AccountListActivity extends AbstractListActivity {

    private static final int NEW_ACCOUNT_REQUEST = 1;

    public static final int EDIT_ACCOUNT_REQUEST = 2;
    private static final int VIEW_ACCOUNT_REQUEST = 3;
    private static final int PURGE_ACCOUNT_REQUEST = 4;

    private QuickActionWidget accountActionGrid;

    private AccountListViewModel accountViewModel;
    private AccountEntityAdapter accountEntityAdapter;
    private QuickActionWidget accountActionGrid;
    private long selectedId = -1;

    public AccountListActivity() {
        super(R.layout.account_list);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        accountViewModel = new ViewModelProvider(this).get(AccountListViewModel.class);
        accountEntityAdapter = new AccountEntityAdapter(this);
        getListView().setAdapter(accountEntityAdapter);

        setupUi();
        setupMenuButton();
        accountViewModel.refreshTotals(); // Initial call to load totals
        integrityCheck();

        // Observe ViewModel data for accounts list
        LifecycleOwner viewLifecycleOwner = this;
        lifecycleScope.launch(() -> {
            // Changed to observe accountsDisplayData
            accountViewModel.getAccountsDisplayData().collect(new FlowCollector<List<AccountDisplayData>>() {
                @Override
                public Object emit(List<AccountDisplayData> accountDisplayDataList, kotlin.coroutines.Continuation<? super Unit> continuation) {
                    accountEntityAdapter.submitList(accountDisplayDataList);
                    // TODO: Update footer with actual count if needed
                    // if (footer != null && accountDisplayDataList != null) {
                    // footer.setText(getString(R.string.pattern_records, accountDisplayDataList.size()));
                    // }
                    return Unit.INSTANCE;
                }
            });
        });

        // Initial filter state
        accountViewModel.setShowActiveOnly(MyPreferences.isHideClosedAccounts(this));

        // Observe ViewModel data for total balance
        TextView totalText = findViewById(R.id.total);
        totalText.setOnClickListener(view -> showTotals()); // Keep existing click listener, if showTotals is still relevant

        lifecycleScope.launch(() -> {
            accountViewModel.getTotalBalance().collect(new FlowCollector<Total>() {
                @Override
                public Object emit(Total total, kotlin.coroutines.Continuation<? super Unit> continuation) {
                    if (total != null && total.getError() == null) {
                        // TODO: Use Utils class for proper formatting if available and compatible with new Total
                        if (total.getCurrency() != null && total.getCurrency().getSymbol() != null) {
                            // Assuming total.getBalance() is in smallest currency unit (e.g. cents)
                            String formattedAmount = String.format(java.util.Locale.getDefault(), "%.2f", total.getBalance() / 100.0);
                            totalText.setText(total.getCurrency().getSymbol() + " " + formattedAmount);
                        } else if (total.getCurrency() != null) { // Symbol might be null
                            String formattedAmount = String.format(java.util.Locale.getDefault(), "%.2f", total.getBalance() / 100.0);
                            totalText.setText(total.getCurrency().getName() + " " + formattedAmount); // Fallback to currency name
                        } else {
                             totalText.setText("N/A"); // No currency info
                        }
                    } else if (total != null && total.getError() != null) {
                        totalText.setText(getString(total.getError().getResId()));
                    } else {
                        totalText.setText(""); // Loading or unknown state
                    }
                    return Unit.INSTANCE;
                }
            });
        });
    }

    private void setupUi() {
        findViewById(R.id.integrity_error).setOnClickListener(v -> v.setVisibility(View.GONE));
        getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            selectedId = id; // id from adapter is AccountEntity.getId()
            // TODO: prepareAccountActionGrid may need to become async or fetch data differently
            // For now, some actions in grid might be disabled or simplified if they need immediate Account data not in AccountEntity
            lifecycleScope.launch(() -> {
                prepareAccountActionGrid(); // This may need to be async if getAccountById is suspend
                if (accountActionGrid != null) {
                    accountActionGrid.show(view);
                }
            });
            return true;
        });
    }

    private void setupMenuButton() {
        final ImageButton bMenu = findViewById(R.id.bMenu);
        if (MyPreferences.isShowMenuButtonOnAccountsScreen(this)) {
            bMenu.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(AccountListActivity.this, bMenu);
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.account_list_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(item -> {
                    handlePopupMenu(item.getItemId());
                    return true;
                });
                popupMenu.show();
            });
        } else {
            bMenu.setVisibility(View.GONE);
        }
    }

    private void handlePopupMenu(int id) {
        switch (id) {
            case R.id.backup:
                MenuListItem.MENU_BACKUP.call(this);
                break;
            case R.id.go_to_menu:
                new DependenciesHolder().getGreenRobotBus().post(new SwitchToMenuTabEvent());
                break;
        }
    }

    // This method now needs to be careful about Main thread access for DB operations
    // accountViewModel.getAccountById is a suspend function.
    // For simplicity, some actions might be temporarily simplified or assume AccountEntity from adapter is sufficient.
    // This method now needs to be careful about Main thread access for DB operations
    protected void prepareAccountActionGrid() throws Exception {
        AccountDisplayData aDD = accountViewModel.getAccountDisplayDataById(selectedId); // Changed to getAccountDisplayDataById
        if (aDD == null || aDD.getAccountEntity() == null) {
            accountActionGrid = null;
            return;
        }
        AccountEntity a = aDD.getAccountEntity();

        accountActionGrid = new QuickActionGrid(this);
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_info, R.string.info));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_list, R.string.blotter));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_edit, R.string.edit));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_add, R.string.transaction));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_transfer, R.string.transfer));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_tick, R.string.balance));
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_flash, R.string.delete_old_transactions));
        if (a.isActive()) {
            accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_lock_closed, R.string.close_account));
        } else {
            accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_lock_open, R.string.reopen_account));
        }
        accountActionGrid.addQuickAction(new MyQuickAction(this, R.drawable.ic_action_trash, R.string.delete_account));
        accountActionGrid.setOnQuickActionClickListener(accountActionListener);
    }

    private final QuickActionWidget.OnQuickActionClickListener accountActionListener = new QuickActionWidget.OnQuickActionClickListener() {
        public void onQuickActionClicked(QuickActionWidget widget, int position) {
            // selectedId should be valid here
            if (selectedId == -1) return;

            switch (position) {
                case 0: // Info
                    showAccountInfo(selectedId);
                    break;
                case 1: // Blotter
                    showAccountTransactions(selectedId);
                    break;
                case 2: // Edit
                    editAccount(selectedId);
                    break;
                case 3: // Add Transaction
                    addTransaction(selectedId, TransactionActivity.class);
                    break;
                case 4: // Add Transfer
                    addTransaction(selectedId, TransferActivity.class);
                    break;
                case 5: // Balance
                    updateAccountBalance(selectedId);
                    break;
                case 6: // Purge
                    purgeAccount(); // Uses selectedId
                    break;
                case 7: // Close/Open
                    closeOrOpenAccount(); // Uses selectedId
                    break;
                case 8: // Delete
                    deleteAccount(); // Uses selectedId
                    break;
            }
        }
    };

    private void addTransaction(long accountId, Class<? extends AbstractTransactionActivity> clazz) {
        Intent intent = new Intent(this, clazz);
        intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId);
        startActivityForResult(intent, VIEW_ACCOUNT_REQUEST); // VIEW_ACCOUNT_REQUEST might be reused or a new one defined
    }

    @Override
    public void recreateCursor() {
        super.recreateCursor(); // Parent's method is now mostly commented out
        accountViewModel.refreshTotals(); // Refresh totals when this is called
        accountViewModel.setShowActiveOnly(MyPreferences.isHideClosedAccounts(this));
    }

    // AccountTotalsCalculationTask and related methods (calculateTotals, showTotals) are removed.
    // Total calculation is now handled by AccountListViewModel.refreshTotals()
    // and observed via accountViewModel.getTotalBalance() Flow.
    // The showTotals() method, if it showed a detailed breakdown, would need a new implementation
    // possibly by navigating to a new Activity/Fragment that observes more detailed total data from a ViewModel.
    // For now, removing it or leaving its click listener to do nothing if it was just for total text.
    private void showTotals() {
        // If AccountListTotalsDetailsActivity is still relevant and can work with new data sources (e.g. from ViewModel/Repo)
        // Intent intent = new Intent(this, AccountListTotalsDetailsActivity.class);
        // startActivityForResult(intent, -1);
        // For now, let's assume the total text click doesn't need to do anything complex.
        // Or it could trigger a refresh:
        accountViewModel.refreshTotals();
    }

    // createAdapter and createCursor are removed.

    protected List<MenuItemInfo> createContextMenus(long id) {
        return new ArrayList<>();
    }

    @Override
    public boolean onPopupItemSelected(int itemId, View view, int position, long id) {
        // do nothing
        return true;
    }

    private boolean updateAccountBalance(long id) {
        lifecycleScope.launch(() -> {
            AccountDisplayData  aDD = accountViewModel.getAccountDisplayDataById(id); // Changed
            if (aDD != null && aDD.getAccountEntity() != null) {
                AccountEntity account = aDD.getAccountEntity();
                Intent intent = new Intent(this, TransactionActivity.class);
                intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, account.getId());
                intent.putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, account.getTotalAmount());
                // intent.putExtra(TransactionActivity.CURRENCY_SYMBOL_EXTRA, aDD.getCurrencySymbol()); // If TransactionActivity needs it
                startActivityForResult(intent, 0);
            }
        });
        return true;
    }

    @Override
    protected void addItem() {
        Intent intent = new Intent(AccountListActivity.this, AccountActivity.class); // AccountActivity may need refactor
        startActivityForResult(intent, NEW_ACCOUNT_REQUEST);
    }

    @Override
    protected void deleteItem(View v, int position, final long id) { // id is from adapter item
        // This method is called by AbstractListActivity if item long click is not overridden.
        // Here, we use QuickActionGrid, so this might not be directly called for delete.
        // The deleteAccount() method triggered by QuickActionGrid is more relevant.
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_account_confirm)
                .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                    accountViewModel.deleteAccount(id); // Use ViewModel
                    // List updates reactively
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void editItem(View v, int position, long id) { // id is from adapter item
        editAccount(id);
    }

    private void editAccount(long id) {
        Intent intent = new Intent(AccountListActivity.this, AccountActivity.class); // AccountActivity may need refactor
        intent.putExtra(AccountActivity.ACCOUNT_ID_EXTRA, id);
        startActivityForResult(intent, EDIT_ACCOUNT_REQUEST);
    }

    // selectedId is now set by OnItemLongClickListener

    private void showAccountInfo(long id) {
        // AccountInfoDialog needs refactoring to not use DatabaseAdapter directly.
        lifecycleScope.launch(() -> {
            AccountDisplayData aDD = accountViewModel.getAccountDisplayDataById(id); // Changed
            if (aDD != null && aDD.getAccountEntity() != null) {
                 LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                 NodeInflater nodeInflater = new NodeInflater(layoutInflater);
                 AccountInfoDialog accountInfoDialog = new AccountInfoDialog(AccountListActivity.this, aDD.getAccountEntity(), aDD.getCurrencySymbol(), nodeInflater);
                 accountInfoDialog.show();
            }
        });
    }


    @Override
    protected void onItemClick(View v, int position, long id) { // id is from adapter item
        if (isQuickMenuEnabledForAccount(this)) {
            selectedId = id;
            lifecycleScope.launch(() -> {
                prepareAccountActionGrid();
                 if (accountActionGrid != null) {
                    accountActionGrid.show(v);
                }
            });
        } else {
            showAccountTransactions(id);
        }
    }

    @Override
    protected void viewItem(View v, int position, long id) { // id is from adapter item
        showAccountTransactions(id);
    }

    private void showAccountTransactions(long id) {
        lifecycleScope.launch(() -> {
            AccountDisplayData aDD = accountViewModel.getAccountDisplayDataById(id); // Changed
            if (aDD != null && aDD.getAccountEntity() != null) {
                AccountEntity accountEntity = aDD.getAccountEntity();
                Intent intent = new Intent(AccountListActivity.this, BlotterActivity.class);
                Criteria.eq(BlotterFilter.FROM_ACCOUNT_ID, String.valueOf(id))
                        .toIntent(accountEntity.getTitle(), intent); // Use title from entity
                intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true);
                startActivityForResult(intent, VIEW_ACCOUNT_REQUEST);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data); // Parent's method is mostly commented out
        // No explicit recreateCursor needed. ViewModel's Flow should handle data changes.
        // if (requestCode == VIEW_ACCOUNT_REQUEST || requestCode == PURGE_ACCOUNT_REQUEST) {
            // recreateCursor(); // Removed
        // }
        // If AccountTotalsCalculationTask was used, it might be triggered here.
    }

    private void purgeAccount() { // Uses selectedId
        Intent intent = new Intent(this, PurgeAccountActivity.class); // PurgeAccountActivity may need refactor
        intent.putExtra(PurgeAccountActivity.ACCOUNT_ID, selectedId);
        startActivityForResult(intent, PURGE_ACCOUNT_REQUEST);
    }

    private void closeOrOpenAccount() { // Uses selectedId
        if (selectedId == -1) return;
        // Fetch the current state to display confirmation correctly
        lifecycleScope.launch(() -> {
            AccountDisplayData aDD = accountViewModel.getAccountDisplayDataById(selectedId);
            if (aDD == null || aDD.getAccountEntity() == null) return Unit.INSTANCE;
            AccountEntity account = aDD.getAccountEntity();

            if (account.isActive()) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.close_account_confirm)
                        .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                            lifecycleScope.launch(() -> {
                                accountViewModel.flipAccountActiveState(selectedId);
                                accountViewModel.refreshTotals(); // Refresh totals after state change
                                return Unit.INSTANCE;
                            });
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
            } else {
                lifecycleScope.launch(() -> {
                    accountViewModel.flipAccountActiveState(selectedId);
                    accountViewModel.refreshTotals(); // Refresh totals after state change
                    return Unit.INSTANCE;
                });
            }
            return Unit.INSTANCE;
        });
    }

    // Removed old flipAccountActive method as its logic is now in ViewModel's flipAccountActiveState
    // and triggered by closeOrOpenAccount.

    private void deleteAccount() { // Uses selectedId
        // Default account check needs re-implementation (e.g. in ViewModel or check against preference)
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_account_confirm)
                .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                    accountViewModel.deleteAccount(selectedId);
                    // List updates reactively
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void integrityCheck() {
        new IntegrityCheckTask(this).execute(new IntegrityCheckAutobackup(TimeUnit.DAYS.toMillis(7)));
    }

}
