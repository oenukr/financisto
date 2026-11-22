// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui

import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.transaction.R
import ru.orangesoftware.financisto.transaction.model.AccountOption
import ru.orangesoftware.financisto.transaction.model.CategoryOption
import ru.orangesoftware.financisto.transaction.model.CurrencyOption
import ru.orangesoftware.financisto.transaction.model.LocationOption
import ru.orangesoftware.financisto.transaction.model.PayeeOption
import ru.orangesoftware.financisto.transaction.model.ProjectOption
import ru.orangesoftware.financisto.transaction.model.TransactionCatalog
import ru.orangesoftware.financisto.transaction.model.TransactionFormPreferences
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.model.TransactionStatusOption
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.mvi.ExpandedInlineField
import ru.orangesoftware.financisto.transaction.mvi.TransactionFormState
import ru.orangesoftware.financisto.transaction.mvi.TransactionIntent
import ru.orangesoftware.financisto.transaction.mvi.TransactionUiState
import ru.orangesoftware.financisto.transaction.ui.components.CreditCardPaymentCard
import ru.orangesoftware.financisto.transaction.ui.components.ExchangeRateCard
import ru.orangesoftware.financisto.transaction.ui.components.HeroAmountCard
import ru.orangesoftware.financisto.transaction.ui.components.HeroAmountDisplayModel
import ru.orangesoftware.financisto.transaction.ui.components.LocationFieldCard
import ru.orangesoftware.financisto.transaction.ui.components.NoteFieldCard
import ru.orangesoftware.financisto.transaction.ui.components.PayeeFieldCard
import ru.orangesoftware.financisto.transaction.ui.components.ProjectFieldCard
import ru.orangesoftware.financisto.transaction.ui.components.ReceiptAttachmentCard
import ru.orangesoftware.financisto.transaction.ui.components.SingleAccountAndCurrencyPills
import ru.orangesoftware.financisto.transaction.ui.components.SingleCategoryCard
import ru.orangesoftware.financisto.transaction.ui.components.SplitCategoryCallbacks
import ru.orangesoftware.financisto.transaction.ui.components.SplitCategoryCard
import ru.orangesoftware.financisto.transaction.ui.components.TransactionBottomBar
import ru.orangesoftware.financisto.transaction.ui.components.TransactionTopBar
import ru.orangesoftware.financisto.transaction.ui.components.TransferAccountsGroupCard

private const val DefaultDecimals = 2

@Composable
fun TransactionScreen(
    uiState: TransactionUiState,
    resolveReceiptPath: (String) -> String?,
    onIntent: (TransactionIntent) -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinancistoTheme {
        when (uiState) {
            is TransactionUiState.Loading,
            is TransactionUiState.LegacyRedirecting,
            -> CenteredLoadingOrMask(showSpinner = true, modifier = modifier)
            is TransactionUiState.PinLockedMask -> CenteredLoadingOrMask(showSpinner = false, modifier = modifier)
            is TransactionUiState.NotFound -> SimpleBlockingMessage(
                title = stringResource(R.string.tx_error_not_found_title),
                body = stringResource(R.string.tx_error_not_found_body),
                onClose = onCloseRequested,
                modifier = modifier,
            )
            is TransactionUiState.NoAccounts -> SimpleBlockingMessage(
                title = stringResource(R.string.tx_error_no_accounts_title),
                body = stringResource(R.string.tx_error_no_accounts_body),
                onClose = onCloseRequested,
                modifier = modifier,
            )
            is TransactionUiState.Ready -> ReadyTransactionContent(
                state = uiState,
                resolveReceiptPath = resolveReceiptPath,
                onIntent = onIntent,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ReadyTransactionContent(
    state: TransactionUiState.Ready,
    resolveReceiptPath: (String) -> String?,
    onIntent: (TransactionIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = true,
        onBackCompleted = { onIntent(TransactionIntent.CancelOrBackClicked) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = { ReadyTopBar(state = state, onIntent = onIntent) },
        bottomBar = {
            TransactionBottomBar(
                isBlankNew = state.form.isFromNewBlankLaunch,
                mode = state.form.mode,
                isSaving = state.isSaving,
                onSecondaryAction = {
                    val intent = if (state.form.isFromNewBlankLaunch) {
                        TransactionIntent.SaveAndNewClicked
                    } else {
                        TransactionIntent.CancelOrBackClicked
                    }
                    onIntent(intent)
                },
                onSaveClick = { onIntent(TransactionIntent.SaveClicked) },
            )
        },
    ) { innerPadding ->
        TransactionFormScrollBody(
            state = state,
            resolveReceiptPath = resolveReceiptPath,
            onIntent = onIntent,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        )
    }

    TransactionActiveSheetHost(
        state = state,
        onIntent = onIntent,
        onDismissAll = { onIntent(TransactionIntent.DismissSheet) },
    )
}

@Composable
private fun ReadyTopBar(
    state: TransactionUiState.Ready,
    onIntent: (TransactionIntent) -> Unit,
) {
    Box(modifier = Modifier.statusBarsPadding()) {
        TransactionTopBar(
            dateTimeMillis = state.form.dateTimeMillis,
            is24HourFormat = state.prefs.is24HourFormat,
            status = state.form.status,
            onBackClick = { onIntent(TransactionIntent.CancelOrBackClicked) },
            onDateClick = { onIntent(TransactionIntent.OpenDatePicker) },
            onTimeClick = { onIntent(TransactionIntent.OpenTimePicker) },
            onStatusClick = { onIntent(TransactionIntent.OpenStatusPicker) },
            onClearFormClick = { onIntent(TransactionIntent.ClearFormClicked) },
            onOpenLegacyClick = if (state.form.isAiDraft) {
                null
            } else {
                { onIntent(TransactionIntent.OpenInLegacyClicked) }
            },
        )
    }
}

@Composable
private fun TransactionFormScrollBody(
    state: TransactionUiState.Ready,
    resolveReceiptPath: (String) -> String?,
    onIntent: (TransactionIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val primaryCurrency = state.primaryCurrency
    val secondaryCurrency = state.secondaryCurrency
    val dimens = FinancistoTheme.dimens
    val heroModel = buildHeroAmountModel(state)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.ScreenHorizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(dimens.CardSpacing),
    ) {
        CapturedAnimatedVisibility(visible = state.validationErrors.isNotEmpty()) {
            ValidationErrorsBanner(state.validationErrors)
        }
        HeroAmountCard(
            model = heroModel,
            onSwitchMode = { onIntent(TransactionIntent.SwitchMode(it)) },
            onCycleSignOrMode = { onIntent(TransactionIntent.CycleSignOrMode) },
            onPrimaryAmountChanged = { onIntent(TransactionIntent.UpdatePrimaryAmount(it)) },
            onSecondaryAmountChanged = { onIntent(TransactionIntent.UpdateSecondaryAmount(it)) },
            onOpenPrimaryCalculator = { onIntent(TransactionIntent.OpenCalculator(CalculatorTarget.PRIMARY_AMOUNT)) },
            onOpenSecondaryCalculator = {
                onIntent(TransactionIntent.OpenCalculator(CalculatorTarget.SECONDARY_AMOUNT))
            },
        )
        CapturedAnimatedVisibility(
            visible = state.isDualCurrency && primaryCurrency != null && secondaryCurrency != null,
        ) {
            if (primaryCurrency != null && secondaryCurrency != null) {
                ExchangeRateCard(
                    rateInput = form.exchangeRateInput,
                    rateValue = form.exchangeRate,
                    fromCode = primaryCurrency.code,
                    toCode = secondaryCurrency.code,
                    isFetchingRate = state.isFetchingRate,
                    onRateInputChange = { onIntent(TransactionIntent.UpdateExchangeRate(it)) },
                    onSyncRateClick = { onIntent(TransactionIntent.FetchLiveRate) },
                    onOpenRateCalculator = {
                        onIntent(TransactionIntent.OpenCalculator(CalculatorTarget.EXCHANGE_RATE))
                    },
                )
            }
        }
        TransactionAccountsSection(state = state, onIntent = onIntent)
        TransactionPrimaryDetailsSection(state = state, onIntent = onIntent)
        TransactionSecondaryDetailsSection(
            state = state,
            resolveReceiptPath = resolveReceiptPath,
            onIntent = onIntent,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun buildHeroAmountModel(state: TransactionUiState.Ready): HeroAmountDisplayModel {
    val form = state.form
    val primaryCurrency = state.primaryCurrency
    val secondaryCurrency = state.secondaryCurrency
    val fromTitle = state.fromAccount?.title
    val toTitle = state.toAccount?.title
    val primaryLabel = fromTitle?.let { stringResource(R.string.tx_label_from_account_inline, it) }.orEmpty()
    val secondaryLabel = if (form.mode == TransactionMode.TRANSFER) {
        toTitle?.let { stringResource(R.string.tx_label_to_account_inline, it) }.orEmpty()
    } else {
        fromTitle.orEmpty()
    }
    return HeroAmountDisplayModel(
        mode = form.mode,
        isDualCurrency = state.isDualCurrency,
        primaryInput = form.primaryAmountInput,
        primaryCurrencyCode = primaryCurrency?.code.orEmpty(),
        primaryCurrencySymbol = primaryCurrency?.symbol ?: primaryCurrency?.code.orEmpty(),
        primaryAccountLabel = primaryLabel,
        secondaryInput = form.secondaryAmountInput,
        secondaryCurrencyCode = secondaryCurrency?.code.orEmpty(),
        secondaryCurrencySymbol = secondaryCurrency?.symbol ?: secondaryCurrency?.code.orEmpty(),
        secondaryAccountLabel = secondaryLabel,
    )
}

@Composable
private fun TransactionAccountsSection(
    state: TransactionUiState.Ready,
    onIntent: (TransactionIntent) -> Unit,
) {
    val form = state.form
    if (form.mode == TransactionMode.TRANSFER) {
        val inMinor = if (state.isDualCurrency) form.secondaryAmountMinor else form.primaryAmountMinor
        TransferAccountsGroupCard(
            fromAccount = state.fromAccount,
            toAccount = state.toAccount,
            outAmountMinor = form.primaryAmountMinor,
            inAmountMinor = inMinor,
            onFromAccountClick = { onIntent(TransactionIntent.OpenAccountPicker(isDestination = false)) },
            onToAccountClick = { onIntent(TransactionIntent.OpenAccountPicker(isDestination = true)) },
            onSwapClick = { onIntent(TransactionIntent.SwapTransferAccounts) },
        )
    } else {
        val showCurrency = state.prefs.isShowCurrency || form.originalCurrencyId != null
        val selectedOrig = remember(state.catalog.currencies, form.originalCurrencyId) {
            form.originalCurrencyId?.let { id -> state.catalog.currencies.firstOrNull { it.id == id } }
        }
        SingleAccountAndCurrencyPills(
            account = state.fromAccount,
            selectedCurrency = selectedOrig,
            showCurrencySelector = showCurrency,
            onAccountClick = { onIntent(TransactionIntent.OpenAccountPicker(isDestination = false)) },
            onCurrencyClick = { onIntent(TransactionIntent.OpenCurrencyPicker) },
        )
    }
}

@Composable
private fun TransactionPrimaryDetailsSection(
    state: TransactionUiState.Ready,
    onIntent: (TransactionIntent) -> Unit,
) {
    val form = state.form
    val primaryCurrency = state.primaryCurrency
    val currencySymbol = primaryCurrency?.symbol ?: primaryCurrency?.code.orEmpty()
    val currencyDecimals = primaryCurrency?.decimals ?: DefaultDecimals
    val payeePrefVisible = if (form.mode == TransactionMode.TRANSFER) {
        state.prefs.isShowPayeeInTransfers
    } else {
        state.prefs.isShowPayee
    }
    if (payeePrefVisible || form.payeeId > 0L || !form.pendingPayeeName.isNullOrBlank()) {
        PayeeFieldCard(
            query = form.payeeQuery,
            selectedPayeeId = form.payeeId,
            pendingPayeeName = form.pendingPayeeName,
            payees = state.catalog.payees,
            isExpanded = state.expandedField == ExpandedInlineField.PAYEE,
            onToggleExpand = { onIntent(TransactionIntent.ToggleInlineField(ExpandedInlineField.PAYEE)) },
            onQueryChanged = { onIntent(TransactionIntent.UpdatePayeeQuery(it)) },
            onSelectPayee = { onIntent(TransactionIntent.SelectPayee(it)) },
            onCreatePendingPayee = { onIntent(TransactionIntent.CreatePendingPayee(it)) },
            onClear = { onIntent(TransactionIntent.ClearPayee) },
        )
    }
    if (form.mode != TransactionMode.TRANSFER) {
        TransactionCategoryCardSlot(
            state = state,
            currencySymbol = currencySymbol,
            currencyDecimals = currencyDecimals,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun TransactionCategoryCardSlot(
    state: TransactionUiState.Ready,
    currencySymbol: String,
    currencyDecimals: Int,
    onIntent: (TransactionIntent) -> Unit,
) {
    val form = state.form
    if (form.isSplitMode) {
        val callbacks = remember(onIntent) {
            SplitCategoryCallbacks(
                onExitSplitMode = { onIntent(TransactionIntent.ToggleSplitMode) },
                onCreateCategoryClick = { onIntent(TransactionIntent.OpenExternalCategoryCreator) },
                onAddSplitClick = { onIntent(TransactionIntent.OpenNewSplitEditor) },
                onEditSplitClick = { onIntent(TransactionIntent.OpenEditSplitEditor(it)) },
                onRemoveSplitClick = { onIntent(TransactionIntent.RemoveSplitItem(it)) },
                onAdjustTotal = { onIntent(TransactionIntent.RebalanceAdjustTotal) },
                onDistributeEvenly = { onIntent(TransactionIntent.RebalanceDistributeEvenly) },
                onAdjustLast = { onIntent(TransactionIntent.RebalanceAdjustLast) },
                onCollapseSingleSplit = { onIntent(TransactionIntent.CollapseSingleSplit) },
            )
        }
        SplitCategoryCard(
            mode = form.mode,
            totalMagnitudeMinor = form.primaryAmountMinor,
            currencySymbol = currencySymbol,
            currencyDecimals = currencyDecimals,
            splits = form.splits,
            categories = state.catalog.categories,
            projects = state.catalog.projects,
            callbacks = callbacks,
        )
    } else {
        SingleCategoryCard(
            selectedCategoryId = form.categoryId,
            categories = state.catalog.categories,
            isIncomeMode = form.mode == TransactionMode.INCOME,
            isExpanded = state.expandedField == ExpandedInlineField.CATEGORY,
            allowSplit = true,
            onToggleExpand = { onIntent(TransactionIntent.ToggleInlineField(ExpandedInlineField.CATEGORY)) },
            onSelectCategory = { onIntent(TransactionIntent.SelectCategory(it)) },
            onClearCategory = { onIntent(TransactionIntent.ClearCategory) },
            onBrowseAllClick = { onIntent(TransactionIntent.OpenCategoryBrowser()) },
            onCreateCategoryClick = { onIntent(TransactionIntent.OpenExternalCategoryCreator) },
            onEnableSplitClick = { onIntent(TransactionIntent.ToggleSplitMode) },
        )
    }
}

@Composable
private fun TransactionSecondaryDetailsSection(
    state: TransactionUiState.Ready,
    resolveReceiptPath: (String) -> String?,
    onIntent: (TransactionIntent) -> Unit,
) {
    val form = state.form
    LocationFieldCard(
        query = form.locationQuery,
        selectedLocationId = form.locationId,
        locations = state.catalog.locations,
        isExpanded = state.expandedField == ExpandedInlineField.LOCATION,
        onToggleExpand = { onIntent(TransactionIntent.ToggleInlineField(ExpandedInlineField.LOCATION)) },
        onQueryChanged = { onIntent(TransactionIntent.UpdateLocationQuery(it)) },
        onSelectLocation = { onIntent(TransactionIntent.SelectLocation(it)) },
        onClear = { onIntent(TransactionIntent.ClearLocation) },
        onCreateNewLocation = { onIntent(TransactionIntent.OpenExternalLocationCreator) },
    )
    if (state.prefs.isShowNote || form.note.isNotBlank()) {
        NoteFieldCard(
            note = form.note,
            recentNotes = state.catalog.recentNotes,
            onNoteChanged = { onIntent(TransactionIntent.UpdateNote(it)) },
            onSelectRecentNote = { onIntent(TransactionIntent.SelectRecentNote(it)) },
            onClear = { onIntent(TransactionIntent.ClearNote) },
        )
    }
    if (!form.isSplitMode) {
        ProjectFieldCard(
            selectedProjectId = form.projectId,
            pendingProjectName = form.pendingProjectName,
            projects = state.catalog.projects,
            onOpenPicker = { onIntent(TransactionIntent.OpenProjectPicker()) },
            onClear = { onIntent(TransactionIntent.ClearProject) },
        )
    }
    if (state.prefs.isShowTakePicture || form.attachedPictureFileName != null) {
        ReceiptAttachmentCard(
            attachedFileName = form.attachedPictureFileName,
            resolvedFilePath = form.attachedPictureFileName?.let(resolveReceiptPath),
            onCameraClick = { onIntent(TransactionIntent.RequestCameraPhoto) },
            onBrowseClick = { onIntent(TransactionIntent.RequestGalleryPhoto) },
            onRemoveClick = { onIntent(TransactionIntent.RemoveReceipt) },
        )
    }
    if (state.prefs.isShowIsCCardPayment || form.isCCardPayment) {
        CreditCardPaymentCard(
            checked = form.isCCardPayment,
            onCheckedChange = { onIntent(TransactionIntent.ToggleCCardPayment(it)) },
        )
    }
}

private val PreviewReadyState = TransactionUiState.Ready(
    form = TransactionFormState(
        mode = TransactionMode.EXPENSE,
        dateTimeMillis = 1715774400000L,
        status = TransactionStatusOption.CLEARED,
        fromAccountId = 1L,
        primaryAmountInput = "42.50",
        primaryAmountMinor = 4250L,
        payeeId = 1L,
        categoryId = 2L,
        locationId = 1L,
        note = "Weekly groceries",
        projectId = 1L,
    ),
    initialFormSnapshot = TransactionFormState(),
    catalog = TransactionCatalog(
        accounts = listOf(
            AccountOption(
                id = 1L,
                title = "Main Checking",
                typeLabel = "Bank",
                currencyId = 1L,
                currencyCode = "USD",
                currencySymbol = "$",
                currencyDecimals = 2,
                balanceMinor = 245075L,
            ),
        ),
        currencies = listOf(
            CurrencyOption(id = 1L, code = "USD", symbol = "$", decimals = 2, isDefault = true),
            CurrencyOption(id = 2L, code = "EUR", symbol = "€", decimals = 2),
        ),
        categories = listOf(
            CategoryOption(
                id = 2L,
                parentId = 1L,
                title = "Groceries",
                fullPath = "Food > Groceries",
                level = 1,
                isIncome = false,
            ),
        ),
        payees = listOf(PayeeOption(id = 1L, title = "Supermarket")),
        projects = listOf(ProjectOption(id = 1L, title = "Household")),
        locations = listOf(LocationOption(id = 1L, title = "Downtown")),
        recentNotes = listOf("Weekly groceries", "Coffee"),
    ),
    prefs = TransactionFormPreferences(),
)

@Preview(name = "Transaction Screen - Light", showBackground = true)
@Composable
private fun TransactionScreenLightPreview() {
    FinancistoTheme(darkTheme = false) {
        ReadyTransactionContent(
            state = PreviewReadyState,
            resolveReceiptPath = { null },
            onIntent = {},
        )
    }
}

@Preview(name = "Transaction Screen - Dark", showBackground = true)
@Composable
private fun TransactionScreenDarkPreview() {
    FinancistoTheme(darkTheme = true) {
        ReadyTransactionContent(
            state = PreviewReadyState,
            resolveReceiptPath = { null },
            onIntent = {},
        )
    }
}
