// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.orangesoftware.financisto.transaction.domain.TransactionValidator
import ru.orangesoftware.financisto.transaction.model.TransactionMode
import ru.orangesoftware.financisto.transaction.mvi.CalculatorTarget
import ru.orangesoftware.financisto.transaction.mvi.ExpandedInlineField
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
import ru.orangesoftware.financisto.transaction.ui.components.SplitCategoryCard
import ru.orangesoftware.financisto.transaction.ui.components.TransactionBottomBar
import ru.orangesoftware.financisto.transaction.ui.components.TransactionTopBar
import ru.orangesoftware.financisto.transaction.ui.components.TransferAccountsGroupCard
import ru.orangesoftware.financisto.transaction.ui.theme.StitchColors
import ru.orangesoftware.financisto.transaction.ui.theme.StitchDimens
import ru.orangesoftware.financisto.transaction.ui.theme.TransactionStitchTheme

private const val DEFAULT_DECIMALS = 2

@Composable
fun TransactionScreen(
    uiState: TransactionUiState,
    resolveReceiptPath: (String) -> String?,
    onIntent: (TransactionIntent) -> Unit,
    onCloseRequested: () -> Unit,
) {
    TransactionStitchTheme {
        when (uiState) {
            is TransactionUiState.Loading,
            is TransactionUiState.LegacyRedirecting -> CenteredLoadingOrMask(showSpinner = true)
            is TransactionUiState.PinLockedMask -> CenteredLoadingOrMask(showSpinner = false)
            is TransactionUiState.NotFound -> SimpleBlockingMessage(
                title = "Transaction not found",
                body = "This transaction may have been deleted.",
                onClose = onCloseRequested,
            )
            is TransactionUiState.NoAccounts -> SimpleBlockingMessage(
                title = "No active accounts",
                body = "Create or activate an account before recording a transaction.",
                onClose = onCloseRequested,
            )
            is TransactionUiState.Ready -> ReadyTransactionContent(
                state = uiState,
                resolveReceiptPath = resolveReceiptPath,
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun ReadyTransactionContent(
    state: TransactionUiState.Ready,
    resolveReceiptPath: (String) -> String?,
    onIntent: (TransactionIntent) -> Unit,
) {
    BackHandler { onIntent(TransactionIntent.CancelOrBackClicked) }

    Scaffold(
        containerColor = StitchColors.ScreenBg,
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
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
                    onOpenLegacyClick = { onIntent(TransactionIntent.OpenInLegacyClicked) },
                )
            }
        },
        bottomBar = {
            TransactionBottomBar(
                isBlankNew = state.form.isFromNewBlankLaunch,
                mode = state.form.mode,
                isSaving = state.isSaving,
                onSecondaryAction = {
                    if (state.form.isFromNewBlankLaunch) {
                        onIntent(TransactionIntent.SaveAndNewClicked)
                    } else {
                        onIntent(TransactionIntent.CancelOrBackClicked)
                    }
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
private fun TransactionFormScrollBody(
    state: TransactionUiState.Ready,
    resolveReceiptPath: (String) -> String?,
    onIntent: (TransactionIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val catalog = state.catalog
    val fromAccount = catalog.accounts.firstOrNull { it.id == form.fromAccountId }
    val toAccount = catalog.accounts.firstOrNull { it.id == form.toAccountId }
    val isDualCurrency = TransactionValidator.isDualCurrencyActive(form, fromAccount, catalog)
    val (pCur, sCur) = TransactionValidator.resolveCurrencyMeta(form, catalog)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StitchDimens.ScreenHorizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(StitchDimens.CardSpacing),
    ) {
        CapturedAnimatedVisibility(visible = state.validationErrors.isNotEmpty()) {
            ValidationErrorsBanner(state.validationErrors)
        }
        HeroAmountCard(
            model = HeroAmountDisplayModel(
                mode = form.mode,
                isDualCurrency = isDualCurrency,
                primaryInput = form.primaryAmountInput,
                primaryCurrencyCode = pCur?.code.orEmpty(),
                primaryCurrencySymbol = pCur?.symbol ?: pCur?.code.orEmpty(),
                primaryAccountLabel = fromAccount?.title?.let { "From $it" }.orEmpty(),
                secondaryInput = form.secondaryAmountInput,
                secondaryCurrencyCode = sCur?.code.orEmpty(),
                secondaryCurrencySymbol = sCur?.symbol ?: sCur?.code.orEmpty(),
                secondaryAccountLabel = if (form.mode == TransactionMode.TRANSFER) {
                    toAccount?.title?.let { "To $it" }.orEmpty()
                } else {
                    fromAccount?.title.orEmpty()
                },
            ),
            onSwitchMode = { onIntent(TransactionIntent.SwitchMode(it)) },
            onCycleSignOrMode = { onIntent(TransactionIntent.CycleSignOrMode) },
            onPrimaryAmountChanged = { onIntent(TransactionIntent.UpdatePrimaryAmount(it)) },
            onSecondaryAmountChanged = { onIntent(TransactionIntent.UpdateSecondaryAmount(it)) },
            onOpenPrimaryCalculator = {
                onIntent(TransactionIntent.OpenCalculator(CalculatorTarget.PRIMARY_AMOUNT))
            },
            onOpenSecondaryCalculator = {
                onIntent(TransactionIntent.OpenCalculator(CalculatorTarget.SECONDARY_AMOUNT))
            },
        )
        CapturedAnimatedVisibility(visible = isDualCurrency && pCur != null && sCur != null) {
            if (pCur != null && sCur != null) {
                ExchangeRateCard(
                    rateInput = form.exchangeRateInput,
                    rateValue = form.exchangeRate,
                    fromCode = pCur.code,
                    toCode = sCur.code,
                    isFetchingRate = state.isFetchingRate,
                    onRateInputChange = { onIntent(TransactionIntent.UpdateExchangeRate(it)) },
                    onSyncRateClick = { onIntent(TransactionIntent.FetchLiveRate) },
                    onOpenRateCalculator = {
                        onIntent(TransactionIntent.OpenCalculator(CalculatorTarget.EXCHANGE_RATE))
                    },
                )
            }
        }
        TransactionAccountsSection(state = state, isDualCurrency = isDualCurrency, onIntent = onIntent)
        TransactionDetailsSection(state = state, resolveReceiptPath = resolveReceiptPath, onIntent = onIntent)
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun TransactionAccountsSection(
    state: TransactionUiState.Ready,
    isDualCurrency: Boolean,
    onIntent: (TransactionIntent) -> Unit,
) {
    val form = state.form
    val fromAcc = state.catalog.accounts.firstOrNull { it.id == form.fromAccountId }
    val toAcc = state.catalog.accounts.firstOrNull { it.id == form.toAccountId }
    if (form.mode == TransactionMode.TRANSFER) {
        val inMinor = if (isDualCurrency) form.secondaryAmountMinor else form.primaryAmountMinor
        TransferAccountsGroupCard(
            fromAccount = fromAcc,
            toAccount = toAcc,
            outAmountMinor = form.primaryAmountMinor,
            inAmountMinor = inMinor,
            onFromAccountClick = { onIntent(TransactionIntent.OpenAccountPicker(isDestination = false)) },
            onToAccountClick = { onIntent(TransactionIntent.OpenAccountPicker(isDestination = true)) },
            onSwapClick = { onIntent(TransactionIntent.SwapTransferAccounts) },
        )
    } else {
        val showCurrency = state.prefs.isShowCurrency || form.originalCurrencyId != null
        val selectedOrig = form.originalCurrencyId?.let { id -> state.catalog.currencies.firstOrNull { it.id == id } }
        SingleAccountAndCurrencyPills(
            account = fromAcc,
            selectedCurrency = selectedOrig,
            showCurrencySelector = showCurrency,
            onAccountClick = { onIntent(TransactionIntent.OpenAccountPicker(isDestination = false)) },
            onCurrencyClick = { onIntent(TransactionIntent.OpenCurrencyPicker) },
        )
    }
}

@Composable
private fun TransactionDetailsSection(
    state: TransactionUiState.Ready,
    resolveReceiptPath: (String) -> String?,
    onIntent: (TransactionIntent) -> Unit,
) {
    val form = state.form
    val (pCur, _) = TransactionValidator.resolveCurrencyMeta(form, state.catalog)
    val currencySymbol = pCur?.symbol ?: pCur?.code.orEmpty()
    val currencyDecimals = pCur?.decimals ?: DEFAULT_DECIMALS

    val payeePrefVisible = if (form.mode == TransactionMode.TRANSFER) {
        state.prefs.isShowPayeeInTransfers
    } else {
        state.prefs.isShowPayee
    }
    val showPayee = payeePrefVisible || form.payeeId > 0L || !form.pendingPayeeName.isNullOrBlank()
    if (showPayee) {
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
        if (form.isSplitMode) {
            SplitCategoryCard(
                mode = form.mode,
                totalMagnitudeMinor = form.primaryAmountMinor,
                currencySymbol = currencySymbol,
                currencyDecimals = currencyDecimals,
                splits = form.splits,
                categories = state.catalog.categories,
                projects = state.catalog.projects,
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

@Composable
private fun ValidationErrorsBanner(errors: List<String>) {
    if (errors.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(StitchDimens.RadiusTile),
        color = StitchColors.ErrorBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            errors.forEach { msg ->
                Text(text = "• $msg", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = StitchColors.ErrorText)
            }
        }
    }
}

@Composable
private fun CenteredLoadingOrMask(showSpinner: Boolean) {
    Surface(color = StitchColors.ScreenBg, modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            if (showSpinner) {
                CircularProgressIndicator(color = StitchColors.Primary)
            }
        }
    }
}

@Composable
private fun SimpleBlockingMessage(title: String, body: String, onClose: () -> Unit) {
    Surface(color = StitchColors.ScreenBg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StitchColors.TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = body, fontSize = 14.sp, color = StitchColors.TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onClose) { Text("Close") }
        }
    }
}
