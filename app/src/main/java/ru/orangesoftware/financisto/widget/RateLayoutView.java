package ru.orangesoftware.financisto.widget;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.activity.AbstractActivity;
import ru.orangesoftware.financisto.activity.ActivityLayout;
import ru.orangesoftware.financisto.db.entity.CurrencyEntity;
import ru.orangesoftware.financisto.mapper.CurrencyEntityMapper;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.utils.MyPreferences;

import static ru.orangesoftware.financisto.activity.AbstractActivity.setVisibility;

public class RateLayoutView implements RateNode.RateNodeOwner {

    private final AbstractActivity activity;
    private final ActivityLayout x;
    private final LinearLayout layout;

    private AmountInput amountInputFrom;
    private AmountInput amountInputTo;

    private RateNode rateNode;
    private View amountInputFromNode;
    private View amountInputToNode;
    private int amountFromTitleId;
    private int amountToTitleId;

    private AmountInput.OnAmountChangedListener amountFromChangeListener;
    private AmountInput.OnAmountChangedListener amountToChangeListener;

    private CurrencyEntity currencyFromEntity;
    private CurrencyEntity currencyToEntity;

    public RateLayoutView(AbstractActivity activity, ActivityLayout x, LinearLayout layout) {
        this.activity = activity;
        this.x = x;
        this.layout = layout;
    }

    public void setAmountFromChangeListener(AmountInput.OnAmountChangedListener amountFromChangeListener) {
        this.amountFromChangeListener = amountFromChangeListener;
    }

    public void setAmountToChangeListener(AmountInput.OnAmountChangedListener amountToChangeListener) {
        this.amountToChangeListener = amountToChangeListener;
    }

    private void createUI(int fromAmountTitleId, int toAmountTitleId) {
        amountInputFrom = AmountInput_.build(activity);
        amountInputFrom.setOwner(activity);
        amountInputFrom.setExpense();
        amountFromTitleId = fromAmountTitleId;
        amountInputFromNode = x.addEditNode(layout, fromAmountTitleId, amountInputFrom);

        amountInputTo = AmountInput_.build(activity);
        amountInputTo.setOwner(activity);
        amountInputTo.setIncome();
        amountToTitleId = toAmountTitleId;
        amountInputToNode = x.addEditNode(layout, toAmountTitleId, amountInputTo);

        amountInputTo.setOnAmountChangedListener(onAmountToChangedListener);
        amountInputFrom.setOnAmountChangedListener(onAmountFromChangedListener);

        setVisibility(amountInputToNode, View.GONE);
        rateNode = new RateNode(this, x, layout);
        setVisibility(rateNode.rateInfoNode, View.GONE);

        if (MyPreferences.isSetFocusOnAmountField(activity)) {
            amountInputFrom.requestFocusFromTouch();
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    public void createTransferUI() {
        createUI(R.string.amount_from, R.string.amount_to);
        if (amountInputFrom != null) amountInputFrom.disableIncomeExpenseButton();
        if (amountInputTo != null) amountInputTo.disableIncomeExpenseButton();
    }

    public void createTransactionUI() {
        createUI(R.string.amount, R.string.amount);
        if (amountInputTo != null) amountInputTo.disableIncomeExpenseButton();
    }

    public void setIncome() {
        if (amountInputFrom != null) amountInputFrom.setIncome();
        if (amountInputTo != null) amountInputTo.setIncome();
    }

    public void setExpense() {
        if (amountInputFrom != null) amountInputFrom.setExpense();
        if (amountInputTo != null) amountInputTo.setExpense();
    }

    public void setCurrencyFromDetails(CurrencyEntity cEntity, String cSymbol) {
        this.currencyFromEntity = cEntity;
        Currency modelCurrency = CurrencyEntityMapper.toModel(cEntity);
        if (amountInputFrom != null) amountInputFrom.setCurrency(modelCurrency);
        updateTitle(amountInputFromNode, amountFromTitleId, cSymbol != null ? cSymbol : (modelCurrency != null ? modelCurrency.name : ""));
        checkNeedRate();
    }

    public void setCurrencyToDetails(CurrencyEntity cEntity, String cSymbol) {
        this.currencyToEntity = cEntity;
        Currency modelCurrency = CurrencyEntityMapper.toModel(cEntity);
        if (amountInputTo != null) amountInputTo.setCurrency(modelCurrency);
        updateTitle(amountInputToNode, amountToTitleId, cSymbol != null ? cSymbol : (modelCurrency != null ? modelCurrency.name : ""));
        checkNeedRate();
    }

    public void setSameCurrencies(CurrencyEntity cEntity, String cSymbol) {
        this.currencyFromEntity = cEntity;
        this.currencyToEntity = cEntity;
        Currency modelCurrency = CurrencyEntityMapper.toModel(cEntity);
        String displaySymbol = cSymbol != null ? cSymbol : (modelCurrency != null ? modelCurrency.name : "");
        if (amountInputFrom != null) amountInputFrom.setCurrency(modelCurrency);
        if (amountInputTo != null) amountInputTo.setCurrency(modelCurrency);
        updateTitle(amountInputFromNode, amountFromTitleId, displaySymbol);
        updateTitle(amountInputToNode, amountToTitleId, displaySymbol); // Should use same title ID if same currency
        checkNeedRate();
    }


    private void updateTitle(View node, int titleId, String currencySymbolOrName) {
        if (node == null) return;
        TextView title = node.findViewById(R.id.label);
        if (title == null) return;

        if (currencySymbolOrName != null && !currencySymbolOrName.isEmpty()) {
            title.setText(activity.getString(titleId) + " (" + currencySymbolOrName + ")");
        } else {
            title.setText(activity.getString(titleId));
        }
    }

    private void checkNeedRate() {
        boolean isDifferent = isDifferentCurrenciesConfigured();
        if (rateNode != null && rateNode.rateInfoNode != null) {
            setVisibility(rateNode.rateInfoNode, isDifferent ? View.VISIBLE : View.GONE);
        }
        if (amountInputToNode != null) {
            setVisibility(amountInputToNode, isDifferent ? View.VISIBLE : View.GONE);
        }
        if (isDifferent) {
            calculateRate();
        }
    }

    public boolean isDifferentCurrenciesConfigured() {
        return currencyFromEntity != null && currencyToEntity != null && currencyFromEntity.getId() != currencyToEntity.getId();
    }


    private void calculateRate() {
        if (amountInputFrom == null || amountInputTo == null || rateNode == null) return;
        long amountFrom = amountInputFrom.getAmount();
        long amountTo = amountInputTo.getAmount();

        if (amountFrom == 0 && amountTo == 0 && rateNode.getRate() != 0) {
        } else if (amountFrom != 0) {
            float r = 1.0f * amountTo / amountFrom;
            if (!Float.isNaN(r) && !Float.isInfinite(r)) {
                rateNode.setRate(r);
            } else {
                 rateNode.setRate(0);
            }
        } else if (amountTo != 0) {
             rateNode.setRate(0);
        } else {
             rateNode.setRate(1.0f);
        }
        rateNode.updateRateInfo();
    }

    public long getFromAmount() {
        return amountInputFrom != null ? amountInputFrom.getAmount() : 0L;
    }

    public long getToAmount() {
        if (isDifferentCurrenciesConfigured()) {
            return amountInputTo != null ? amountInputTo.getAmount() : 0L;
        } else {
            return amountInputFrom != null ? amountInputFrom.getAmount() : 0L;
        }
    }

    private final AmountInput.OnAmountChangedListener onAmountFromChangedListener = new AmountInput.OnAmountChangedListener(){
        @Override
        public void onAmountChanged(long oldAmount, long newAmount) {
            if (rateNode != null && amountInputTo != null) {
                double r = rateNode.getRate();
                if (r > 0 && isDifferentCurrenciesConfigured()) {
                    long calculatedToAmount = Math.round(r * newAmount);
                    AmountInput.OnAmountChangedListener tempListener = amountInputTo.getOnAmountChangedListener();
                    amountInputTo.setOnAmountChangedListener(null);
                    amountInputTo.setAmount(calculatedToAmount);
                    amountInputTo.setOnAmountChangedListener(tempListener);
                } else {
                    calculateRate();
                }
            }
            if (amountInputFrom != null && amountInputTo != null && amountInputFrom.isIncomeExpenseEnabled()) {
                if (amountInputFrom.isExpense()) amountInputTo.setExpense(); else amountInputTo.setIncome();
            }
            if (rateNode != null) rateNode.updateRateInfo();
            if (amountFromChangeListener != null) {
                amountFromChangeListener.onAmountChanged(oldAmount, newAmount);
            }
        }
    };

    private final AmountInput.OnAmountChangedListener onAmountToChangedListener = new AmountInput.OnAmountChangedListener(){
        @Override
        public void onAmountChanged(long oldAmount, long newAmount) {
            if (rateNode != null && amountInputFrom != null) {
                long currentFromAmount = amountInputFrom.getAmount();
                if (currentFromAmount != 0 && isDifferentCurrenciesConfigured()) {
                    rateNode.setRate(1.0f * newAmount / currentFromAmount);
                } else if (!isDifferentCurrenciesConfigured()){
                    // If same currency, fromAmount could mirror toAmount
                } else {
                    // amountFrom is 0, cannot calculate rate based on it
                }
            }
            if (rateNode != null) rateNode.updateRateInfo();
            if (amountToChangeListener != null) {
                amountToChangeListener.onAmountChanged(oldAmount, newAmount);
            }
        }
    };

    public void setFromAmount(long fromAmount) {
        if (amountInputFrom != null) {
             AmountInput.OnAmountChangedListener tempListener = amountInputFrom.getOnAmountChangedListener();
             amountInputFrom.setOnAmountChangedListener(null);
             amountInputFrom.setAmount(fromAmount);
             amountInputFrom.setOnAmountChangedListener(tempListener);
        }
    }

    public void setToAmount(long toAmount) {
        if (amountInputTo != null) {
            AmountInput.OnAmountChangedListener tempListener = amountInputTo.getOnAmountChangedListener();
            amountInputTo.setOnAmountChangedListener(null);
            amountInputTo.setAmount(toAmount);
            amountInputTo.setOnAmountChangedListener(tempListener);
        }
    }

    private void updateToAmountFromRate() {
        if (rateNode == null || amountInputFrom == null || amountInputTo == null || !isDifferentCurrenciesConfigured()) return;
        double r = rateNode.getRate();
        long currentFromAmount = amountInputFrom.getAmount();
        long calculatedToAmount = (long) Math.round(r * currentFromAmount);

        long oldToAmount = amountInputTo.getAmount();
        AmountInput.OnAmountChangedListener tempListener = amountInputTo.getOnAmountChangedListener();
        amountInputTo.setOnAmountChangedListener(null);
        amountInputTo.setAmount(calculatedToAmount);
        amountInputTo.setOnAmountChangedListener(tempListener);

        if (amountToChangeListener != null && oldToAmount != calculatedToAmount) {
            amountToChangeListener.onAmountChanged(oldToAmount, calculatedToAmount);
        }
    }

    public void openFromAmountCalculator() {
        if (amountInputFrom != null) amountInputFrom.openCalculator();
    }

    @Override
    public void onBeforeRateDownload() {
        if (amountInputFrom != null) amountInputFrom.setEnabled(false);
        if (amountInputTo != null) amountInputTo.setEnabled(false);
        if (rateNode != null) rateNode.disableAll();
    }

    @Override
    public Currency getCurrencyFrom() {
        return CurrencyEntityMapper.toModel(this.currencyFromEntity);
    }

    @Override
    public Currency getCurrencyTo() {
        return CurrencyEntityMapper.toModel(this.currencyToEntity);
    }

    public CurrencyEntity getCurrencyFromEntity() { return currencyFromEntity; }
    public CurrencyEntity getCurrencyToEntity() { return currencyToEntity; }

    public long getCurrencyToId() {
        return currencyToEntity != null ? currencyToEntity.getId() : 0L;
    }

    @Override
    public void onAfterRateDownload() {
        if (amountInputFrom != null) amountInputFrom.setEnabled(true);
        if (amountInputTo != null) amountInputTo.setEnabled(true);
        if (rateNode != null) rateNode.enableAll();
    }

    @Override
    public void onSuccessfulRateDownload() {
        updateToAmountFromRate();
    }

    @Override
    public void onRateChanged() {
        updateToAmountFromRate();
    }

    @Override
    public Activity getActivity() {
        return activity;
    }
}
