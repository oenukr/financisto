// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.core.designsystem.theme.FinancistoTheme
import ru.orangesoftware.financisto.db.DatabaseHelper.ReportColumns
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.graph.GraphComposable
import ru.orangesoftware.financisto.graph.GraphUnit
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.report.PeriodReport
import ru.orangesoftware.financisto.report.Report
import ru.orangesoftware.financisto.reports.ReportLaunchParams
import ru.orangesoftware.financisto.reports.ReportUiState
import ru.orangesoftware.financisto.reports.ReportViewModel
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto.utils.Utils
import kotlin.math.abs

class ReportActivity : ComponentActivity(), RefreshSupportedActivity {

    private val reportsViewModel: ReportViewModel by viewModel {
        parametersOf(ReportLaunchParams.fromIntent(intent, resources.displayMetrics.density))
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyPreferences.switchLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
        setContent {
            val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
            val context = LocalContext.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> PinProtection.lock(context)
                        Lifecycle.Event.ON_RESUME -> PinProtection.unlock(context)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val uiState by reportsViewModel.uiState

            FinancistoTheme {
                ReportScreen(uiState = uiState)
            }
        }
    }

    @Composable
    private fun ReportScreen(uiState: ReportUiState) {
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val currentFilter = uiState.filter
        val currentReport = uiState.currentReport
        val incomeExpense = uiState.incomeExpense

        LaunchedEffect(uiState.errorMessage) {
            uiState.errorMessage?.let { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        val titleText = remember(currentFilter) {
            val criteria: Criteria? = currentFilter.get(ReportColumns.DATETIME)
            if (criteria != null) {
                DateUtils.formatDateRange(
                    context,
                    criteria.longValue1,
                    criteria.longValue2,
                    DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_SHOW_TIME or
                        DateUtils.FORMAT_ABBREV_MONTH,
                )
            } else {
                ContextCompat.getString(context, R.string.no_filter)
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                ReportTopBar(
                    titleText = if (currentReport != null && currentReport !is PeriodReport) titleText else null,
                )
            },
            bottomBar = {
                ReportBottomBar(
                    incomeExpenseTitleId = incomeExpense.titleId,
                    currentFilter = currentFilter,
                    currentReport = currentReport,
                    onActivityResult = { result ->
                        reportsViewModel.updateFilter(result.resultCode, result.data)
                    },
                    onToggleIncomeExpense = { reportsViewModel.toggleIncomeExpense() },
                    incomeExpenseIcon = incomeExpense.iconId,
                    incomeExpenseTitle = incomeExpense.titleId,
                    shouldDisplayTotal = currentReport?.shouldDisplayTotal() == true,
                    total = uiState.total,
                    onPieButtonClick = {
                        context.startActivity(reportsViewModel.createPieChartIntent(context))
                    },
                )
            },
        ) { innerPadding ->
            ReportGraphList(
                isLoading = uiState.isLoading,
                graphUnits = uiState.graphUnits,
                innerPadding = innerPadding,
            )
        }
    }

    @Composable
    private fun ReportGraphList(
        isLoading: Boolean,
        graphUnits: List<GraphUnit>,
        innerPadding: PaddingValues,
    ) {
        val context = LocalContext.current
        val (maxAmount, maxAmountWidth) = remember(graphUnits) {
            val rect = Rect()
            var maxAmt = 0L
            var maxAmtWidth = 0L
            val primaryColor = ContextCompat.getColor(context, R.color.colorPrimary)
            graphUnits.forEach { unit ->
                unit.style.namePaint.color = primaryColor
                unit.forEach { amount ->
                    val amountText = amount.getAmountText()
                    unit.style.amountPaint.getTextBounds(amountText, 0, amountText.length, rect)
                    amount.amountTextWidth = rect.width()
                    amount.amountTextHeight = rect.height()
                    maxAmt = maxAmt.coerceAtLeast(abs(amount.amount))
                    maxAmtWidth = maxAmtWidth.coerceAtLeast(amount.amountTextWidth.toLong())
                }
            }
            maxAmt to maxAmtWidth
        }

        LazyColumn(
            modifier = Modifier.consumeWindowInsets(innerPadding),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isLoading) {
                item { CircularProgressIndicator() }
            } else {
                items(graphUnits) { item ->
                    GraphComposable(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(5.dp, 10.dp, 5.dp, 5.dp),
                        unit = item,
                        maxAmount = maxAmount,
                        maxAmountWidth = maxAmountWidth,
                        onItemClick = { id ->
                            reportsViewModel.createRowClickIntent(id, context)?.let(context::startActivity)
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun ReportTopBar(
        modifier: Modifier = Modifier,
        titleText: String? = null,
    ) {
        val context: Context = LocalContext.current
        CenterAlignedTopAppBar(
            modifier = modifier,
            title = {
                if (!titleText.isNullOrBlank()) Text(text = titleText)
            },
            navigationIcon = {
                IconButton(onClick = { (context as Activity).finish() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Localized description",
                    )
                }
            },
        )
    }

    @Composable
    private fun ReportBottomBar(
        modifier: Modifier = Modifier,
        @StringRes incomeExpenseTitleId: Int,
        currentFilter: WhereFilter,
        currentReport: Report?,
        onActivityResult: (ActivityResult) -> Unit,
        onToggleIncomeExpense: () -> Unit,
        @DrawableRes incomeExpenseIcon: Int,
        @StringRes incomeExpenseTitle: Int,
        shouldDisplayTotal: Boolean = false,
        total: Total,
        onPieButtonClick: () -> Unit,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.background),
        ) {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = stringResource(id = incomeExpenseTitleId),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FilterIconButton(
                    currentFilter = currentFilter,
                    currentReport = currentReport,
                    onActivityResult = onActivityResult,
                )
                IconButton(
                    onClick = onToggleIncomeExpense,
                    enabled = currentReport !is PeriodReport,
                ) {
                    Icon(
                        painter = painterResource(id = incomeExpenseIcon),
                        tint = null,
                        contentDescription = stringResource(id = incomeExpenseTitle),
                    )
                }
                IconButton(onClick = onPieButtonClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tab_budgets_selected),
                        tint = null,
                        contentDescription = "Pie chart",
                    )
                }
                if (shouldDisplayTotal) {
                    ReportTotalRow(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        total = total,
                    )
                }
            }
        }
    }

    @Composable
    private fun ReportTotalRow(
        modifier: Modifier = Modifier,
        total: Total,
    ) {
        val colorForAmount: @Composable (Long) -> Int = { amount ->
            when {
                amount == 0L -> R.color.holo_gray_bright
                amount > 0 -> R.color.positive_amount
                else -> R.color.negative_amount
            }
        }
        Row(modifier = modifier) {
            when {
                total.isError -> {
                    Text(text = stringResource(id = R.string.not_available))
                    Icon(
                        painter = painterResource(id = R.drawable.total_error),
                        contentDescription = stringResource(id = R.string.not_available),
                    )
                }

                total.showAmount -> {
                    val annotatedString = buildAnnotatedString {
                        withStyle(SpanStyle(color = colorResource(colorForAmount(total.amount)))) {
                            append(Utils.amountToString(total.currency, total.amount, false))
                        }
                        append(" | ")
                        withStyle(SpanStyle(color = colorResource(colorForAmount(total.balance)))) {
                            append(Utils.amountToString(total.currency, total.balance, false))
                        }
                    }
                    Text(text = annotatedString)
                }

                total.showIncomeExpense -> {
                    val annotatedString = buildAnnotatedString {
                        withStyle(SpanStyle(color = colorResource(colorForAmount(total.income)))) {
                            append(Utils.amountToString(total.currency, total.income, false))
                        }
                        append(" | ")
                        withStyle(SpanStyle(color = colorResource(colorForAmount(total.expenses)))) {
                            append(Utils.amountToString(total.currency, total.expenses, false))
                        }
                    }
                    Text(text = annotatedString)
                }

                else -> {
                    Text(
                        text = Utils.amountToString(
                            StringBuilder(),
                            total.currency,
                            total.balance,
                            false,
                        ).toString(),
                        color = when {
                            total.balance == 0L -> MaterialTheme.colorScheme.secondary
                            total.balance > 0 -> colorResource(R.color.positive_amount)
                            else -> colorResource(R.color.negative_amount)
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun FilterIconButton(
        modifier: Modifier = Modifier,
        currentFilter: WhereFilter,
        currentReport: Report?,
        onActivityResult: (ActivityResult) -> Unit,
    ) {
        val context = LocalContext.current

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            onActivityResult(result)
        }

        IconButton(
            modifier = modifier,
            onClick = {
                val intent = Intent(context, ReportFilterActivity::class.java)
                currentFilter.toIntent(intent)
                launcher.launch(intent)
            },
            enabled = currentReport !is PeriodReport,
        ) {
            val tintId = if (currentFilter.isEmpty) R.color.bottom_bar_tint else R.color.holo_blue_dark
            Icon(
                painter = painterResource(id = R.drawable.actionbar_filter),
                contentDescription = "Filter",
                tint = colorResource(id = tintId),
            )
        }
    }

    override fun recreateCursor() {
        reportsViewModel.refresh()
    }

    override fun integrityCheck() = Unit

    companion object {
        const val FILTER_INCOME_EXPENSE: String = "FILTER_INCOME_EXPENSE"
    }
}
