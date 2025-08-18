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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseHelper.ReportColumns
import ru.orangesoftware.financisto.db.FinancistoDatabase
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.graph.GraphComposable
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.report.PeriodReport
import ru.orangesoftware.financisto.report.Report
import ru.orangesoftware.financisto.reports.ReportViewModel
import ru.orangesoftware.financisto.reports.ReportViewModel.Companion.INTENT_KEY
import ru.orangesoftware.financisto.reports.ReportViewModel.Companion.SCREEN_DENTITY_KEY
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto.utils.Utils
import kotlin.math.abs

class ReportActivity : ComponentActivity(), RefreshSupportedActivity {

    companion object {
        const val FILTER_INCOME_EXPENSE: String = "FILTER_INCOME_EXPENSE"
    }
    
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyPreferences.switchLocale(newBase))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
        setContent {
            val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
            val snackbarHostState = remember { SnackbarHostState() }
            val context = LocalContext.current
            val screenDensity = LocalDensity.current

            val reportsViewModel: ReportViewModel = viewModel(
                factory = ReportViewModel.Factory,
                extras = MutableCreationExtras(defaultViewModelCreationExtras).apply {
                    set(APPLICATION_KEY, application)
//                    set(REPORT_NAME_KEY, intent.getStringExtra(EXTRA_REPORT_TYPE).orEmpty())
                    set(SCREEN_DENTITY_KEY, screenDensity.density)
                    set(INTENT_KEY, intent)
                },
            )

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> reportsViewModel.open()
                        Lifecycle.Event.ON_PAUSE -> PinProtection.lock(context)
                        Lifecycle.Event.ON_RESUME -> PinProtection.unlock(context)
                        Lifecycle.Event.ON_STOP -> reportsViewModel.close()
                        else -> Unit
                    }
                }

                // Add the observer to the lifecycle
                lifecycleOwner.lifecycle.addObserver(observer)

                // When the effect leaves the Composition, remove the observer
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val roomDb = Room.databaseBuilder(
                applicationContext,
                FinancistoDatabase::class.java,
                "financisto.db",
            ).build()
            val currencyCache by remember(roomDb) {
                mutableStateOf(CurrencyCache(roomDb.currencyDao()))
            }
            val incomeExpense by reportsViewModel.incomeExpenseState.collectAsStateWithLifecycle()
            val incomeExpenseIcon by remember(incomeExpense) { mutableIntStateOf(incomeExpense.iconId) }
            val incomeExpenseTitle by remember(incomeExpense) { mutableIntStateOf(incomeExpense.titleId) }
            val units by reportsViewModel.graphUnits.collectAsStateWithLifecycle()
            val currentReport by reportsViewModel.currentReport.collectAsStateWithLifecycle()
            val currentFilter by reportsViewModel.filter.collectAsStateWithLifecycle()
            val total by reportsViewModel.reportTotal.collectAsStateWithLifecycle()
            val titleText = remember(currentFilter) {
                val criteria: Criteria? = currentFilter.get(ReportColumns.DATETIME)
                if (criteria != null) {
                            DateUtils.formatDateRange(
                                context,
                                criteria.longValue1,
                                criteria.longValue2,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
                            )
                } else {
                    ContextCompat.getString(context, R.string.no_filter)
                }
            }

            var maxAmount by remember { mutableLongStateOf(0L) }
            var maxAmountWidth by remember { mutableLongStateOf(0L) }

            MaterialTheme {
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
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
                                reportsViewModel.updateFilter(
                                    result.resultCode,
                                    result.data,
                                )
                            },
                            onToggleIncomeExpense = {
                                reportsViewModel.toggleIncomeExpense(/*context*/)
                            },
                            incomeExpenseIcon = incomeExpenseIcon,
                            incomeExpenseTitle = incomeExpenseTitle,
                            shouldDisplayTotal = currentReport?.shouldDisplayTotal() == true,
                            total = total,
                            currencyCache = currencyCache,
                            onPieButtonClick = { reportsViewModel.showPieChart(this) },
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier.consumeWindowInsets(innerPadding),
                        contentPadding = innerPadding,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val rect = Rect()
                        units?.forEach { unit ->
                            unit.forEach { amount ->
                                val amountText = amount.getAmountText()
                                unit.style.amountPaint.getTextBounds(
                                    amountText,
                                    0,
                                    amountText.length,
                                    rect
                                )
                                unit.style.namePaint.color = resources.getColor(
                                    R.color.colorPrimary,
                                    context.theme,
                                )
                                amount.amountTextWidth = rect.width()
                                amount.amountTextHeight = rect.height()
                                maxAmount = maxAmount.coerceAtLeast(abs(amount.amount))
                                maxAmountWidth =
                                    maxAmountWidth.coerceAtLeast(amount.amountTextWidth.toLong())
                            }
                        }
                        units?.let {
                            items(it) { item ->
                                GraphComposable(
                                    modifier = Modifier
                                        .fillParentMaxWidth()
                                        .padding(5.dp, 10.dp, 5.dp, 5.dp),
                                    unit = item,
                                    maxAmount = maxAmount,
                                    maxAmountWidth = maxAmountWidth,
                                    onItemClick = { id ->
                                        reportsViewModel.onGraphRowClicked(id, context)
                                    },
                                )
                            }
                        } ?: item { CircularProgressIndicator() }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
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
                        contentDescription = "Localized description"
                    )
                }
            },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
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
        currencyCache: CurrencyCache,
        onPieButtonClick: () -> Unit,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.background)
        ) {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = stringResource(id = incomeExpenseTitleId)
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
                IncomeExpenseIconButton(
                    currentReport = currentReport,
                    incomeExpenseIcon = incomeExpenseIcon,
                    incomeExpenseTitle = incomeExpenseTitle,
                    onToggleIncomeExpense = onToggleIncomeExpense,
                )
                PieChartIconButton(
                    onPieButtonClick = onPieButtonClick,
                )
                if (shouldDisplayTotal == true) {
                    Row(modifier = Modifier.align(Alignment.CenterVertically)) {
                        if (total.isError) {
                            Text(text = resources.getString(R.string.not_available))
                            Icon(
                                painter = painterResource(id = R.drawable.total_error),
                                contentDescription = resources.getString(R.string.not_available),
                            )
                        } else {
                            if (total.showAmount) {
                                val annotatedString = buildAnnotatedString {
                                    withStyle(style = total.amount.toTextStyle()) {
                                        append(
                                            Utils.amountToString(
                                                currencyCache,
                                                total.currency,
                                                total.amount,
                                                false
                                            )
                                        )
                                    }
                                    append(" | ")
                                    withStyle(style = total.balance.toTextStyle()) {
                                        append(
                                            Utils.amountToString(
                                                currencyCache,
                                                total.currency,
                                                total.balance,
                                                false
                                            )
                                        )
                                    }
                                }
                                Text(text = annotatedString)
                            } else if (total.showIncomeExpense) {
                                val annotatedString = buildAnnotatedString {
                                    withStyle(style = total.income.toTextStyle()) {
                                        append(
                                            Utils.amountToString(
                                                currencyCache,
                                                total.currency,
                                                total.income,
                                                false
                                            )
                                        )
                                    }
                                    append(" | ")
                                    withStyle(style = total.expenses.toTextStyle()) {
                                        append(
                                            Utils.amountToString(
                                                currencyCache,
                                                total.currency,
                                                total.expenses,
                                                false
                                            )
                                        )
                                    }
                                }
                                Text(text = annotatedString)
                            } else {
                                Text(
                                    text = Utils.amountToString(
                                        currencyCache,
                                        StringBuilder(),
                                        total.currency,
                                        total.balance,
                                        false
                                    ).toString(),
                                    color = if (total.balance == 0L) {
                                        MaterialTheme.colorScheme.secondary
                                    } else if (total.balance > 0) {
                                        colorResource(R.color.positive_amount)
                                    } else {
                                        colorResource(R.color.negative_amount)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PieChartIconButton(
        modifier: Modifier = Modifier,
        onPieButtonClick: () -> Unit,
    ) {
        IconButton(
            modifier = modifier,
            onClick = onPieButtonClick,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_tab_budgets_selected),
                tint = null,
                contentDescription = "Pie chart",
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun IncomeExpenseIconButton(
        modifier: Modifier = Modifier,
        currentReport: Report?,
        @DrawableRes incomeExpenseIcon: Int,
        @StringRes incomeExpenseTitle: Int,
        onToggleIncomeExpense: () -> Unit,
    ) {
        IconButton(
            modifier = modifier,
            onClick = onToggleIncomeExpense,
            enabled = currentReport !is PeriodReport,
        ) {
            Icon(
                painter = painterResource(id = incomeExpenseIcon),
                tint = null,
                contentDescription = stringResource(id = incomeExpenseTitle),
            )
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
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            onActivityResult(result)
        }

        IconButton(
            modifier = modifier,
            onClick = {
                val intent =
                    Intent(context, ReportFilterActivity::class.java)
                currentFilter.toIntent(intent)
                launcher.launch(intent)
            },
            enabled = currentReport !is PeriodReport,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.actionbar_filter),
                contentDescription = "Filter",
                tint = colorResource(id = if (currentFilter.isEmpty) R.color.bottom_bar_tint else R.color.holo_blue_dark)
            )
        }
    }

    @Composable
    private fun Long.toTextStyle(): SpanStyle = SpanStyle(
        color = colorResource(
            if (this == 0L) {
                R.color.holo_gray_bright
            } else if (this > 0) {
                R.color.positive_amount
            } else {
                R.color.negative_amount
            }
        )
    )

    override fun recreateCursor() {
//        selectReport()
        // TODO: react to changes in database
    }

    override fun integrityCheck() = Unit
}
