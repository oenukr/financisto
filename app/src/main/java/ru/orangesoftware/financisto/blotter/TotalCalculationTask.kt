// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.blotter

import android.content.Context
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.utils.Logger
import ru.orangesoftware.financisto.utils.Utils

abstract class TotalCalculationTask(
    protected val context: Context?,
    protected val totalText: TextView?,
    protected val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger: Logger = DependenciesHolder().logger

    @Volatile
    private var isRunning: Boolean = true

    var job: Job? = null
        private set

    @Volatile
    var lastResult: Total? = null
        private set

    val isCancelled: Boolean
        get() = !isRunning || job?.isCancelled == true

    abstract fun getTotalInHomeCurrency(): Total
    abstract fun getTotals(): Array<Total>

    open fun execute(scope: CoroutineScope): Job {
        val currentJob = job
        currentJob?.cancel()
        val newJob = scope.launch {
            if (!isRunning) return@launch
            val result = try {
                withContext(ioDispatcher) {
                    coroutineContext.ensureActive()
                    getTotalInHomeCurrency()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (ex: Exception) {
                logger.e(ex, "Unexpected error")
                Total.ZERO
            }
            coroutineContext.ensureActive()
            lastResult = result
            if (isRunning) {
                onPostExecute(result)
            }
        }
        job = newJob
        return newJob
    }

    protected open fun onPostExecute(result: Total) {
        val ctx = context ?: return
        if (result.currency == Currency.EMPTY) {
            Toast.makeText(ctx, R.string.currency_make_default_warning, Toast.LENGTH_LONG).show()
        }
        val u = Utils(ctx)
        u.setTotal(totalText, result)
    }

    fun stop() {
        cancel()
    }

    fun cancel(mayInterruptIfRunning: Boolean = true): Boolean {
        isRunning = false
        val currentJob = job
        currentJob?.cancel()
        return currentJob != null
    }
}
