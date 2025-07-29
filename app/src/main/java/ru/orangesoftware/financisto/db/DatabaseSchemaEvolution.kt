package ru.orangesoftware.financisto.db

import android.content.ContentValues
import android.content.Context
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.utils.Logger
import java.io.IOException
import java.util.Scanner

/**
 * Schema evolution helper.
 * Put sql files into assets/database directory as following:
 * - All create scripts into /create directory,
 * - All view scripts into /view directory,
 * - All alter scripts in /alter directory.
 * The algorithm is as follows:
 * On initial database create (when SQLiteOpenHelper.onCreate invoked),
 * the helper executes scripts in the following order: /create, /alter, /view.
 * On every database upgrade, the helper executes scripts from /alter which
 * haven't been yet executed, then all scripts from /view.
 *
 * @author Denis Solonenko
 */
open class DatabaseSchemaEvolution(
    context: Context,
    name: String?,
    factory: SQLiteDatabase.CursorFactory?,
    version: Int,
) : SupportSQLiteOpenHelper.Callback(version), SupportSQLiteOpenHelper {

    private val logger: Logger = DependenciesHolder().logger

    companion object {
        private const val ALTERLOG = "alterlog"
        private const val DATABASE_PATH = "database"
        private const val CREATE_PATH = "$DATABASE_PATH/create"
        private const val VIEW_PATH = "$DATABASE_PATH/view"
        private const val ALTER_PATH = "$DATABASE_PATH/alter"
        private val projection = arrayOf("1")
    }

    private val assetManager: AssetManager = context.assets
    var autoDropViews: Boolean = false
    private val openHelper: SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(this)
            .build()
    )

    override val databaseName: String? = openHelper.databaseName


    override val writableDatabase: SupportSQLiteDatabase = openHelper.writableDatabase

    override val readableDatabase: SupportSQLiteDatabase = openHelper.readableDatabase

    override fun close() {
        openHelper.close()
    }

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        openHelper.setWriteAheadLoggingEnabled(enabled) // Corrected based on interface
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        try {
            logger.i("Creating ALTERLOG table")
            db.execSQL("CREATE TABLE $ALTERLOG (script TEXT NOT NULL, datetime LONG NOT NULL)")
            db.execSQL("CREATE INDEX ${ALTERLOG}_script_idx ON $ALTERLOG (script)")
            logger.i("Running create scripts...")
            runAllScripts(db, CREATE_PATH, false)
            logger.i("Running alter scripts...")
            runAllScripts(db, ALTER_PATH, true)
            logger.i("Running create view scripts...")
            runAllScripts(db, VIEW_PATH, false)
        } catch (ex: Exception) {
            throw RuntimeException("Failed to create database", ex)
        }
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            logger.i("Upgrading database from version $oldVersion to version $newVersion...")
            logger.i("Running alter scripts...")
            runAllScripts(db, ALTER_PATH, true)
            logger.i("Running create view scripts...")
            runAllScripts(db, VIEW_PATH, false)
        } catch (ex: Exception) {
            throw RuntimeException("Failed to upgrade database", ex)
        }
    }

    @Throws(IOException::class)
    fun runAlterScript(db: SupportSQLiteDatabase, name: String) {
        runAlterScript(db, ALTER_PATH, name)
    }

    @Throws(IOException::class)
    private fun runAlterScript(db: SupportSQLiteDatabase, path: String, name: String) {
        val script = "$path/$name"
        runScript(db, script)
    }

    @Throws(IOException::class)
    private fun runAllScripts(db: SupportSQLiteDatabase, path: String, checkAlterlog: Boolean) {
        val scripts = sortScripts(assetManager.list(path) ?: emptyArray())
        for (scriptFile in scripts) {
            val script = "$path/$scriptFile"
            if (checkAlterlog) {
                if (alreadyRun(db, script)) {
                    logger.d("Skipping $script")
                    continue
                }
            }
            if (autoDropViews && VIEW_PATH == path) {
                val viewName = getViewNameFromScriptName(scriptFile)
                db.execSQL("DROP VIEW IF EXISTS $viewName")
            }
            logger.i("Running $script")
            runScript(db, script)
            if (checkAlterlog) {
                saveScriptToAlterlog(db, script)
            }
        }
    }

    @Throws(IOException::class)
    private fun runScript(db: SupportSQLiteDatabase, script: String) {
        val content = readFile(script).split(";")
        content.forEach { string ->
            val sql = string.trim()
            if (sql.length > 1) {
                try {
                    db.execSQL(sql)
                } catch (ex: SQLiteException) {
                    logger.e(ex, "Unable to run sql: $sql")
                    throw ex
                }
            }
        }
    }

    /**
     * Sorts array of scripts' names
     * @param scripts scripts list
     * @return scripts array sorted with natural order
     */
    internal fun sortScripts(scripts: Array<String>): Array<String> {
        scripts.sort()
        return scripts
    }

    protected open fun getViewNameFromScriptName(scriptFileName: String): String? {
        val i = scriptFileName.indexOf('.')
        return if (i == -1) scriptFileName else scriptFileName.substring(0, i)
    }

    private fun alreadyRun(db: SupportSQLiteDatabase, script: String): Boolean {
        val query = SupportSQLiteQueryBuilder
            .builder(ALTERLOG)
            .columns(projection)
            .selection("script=?", arrayOf(script))
            .create()
        db.query(query).use { c ->
            return c.moveToFirst()
        }
    }

    private fun saveScriptToAlterlog(db: SupportSQLiteDatabase, script: String) {
        val values = ContentValues().apply {
            put("script", script)
            put("datetime", System.currentTimeMillis())
        }
        db.insert(ALTERLOG, SQLiteDatabase.CONFLICT_NONE, values)
    }

    @Throws(IOException::class)
    private fun readFile(scriptFile: String): String {
        val sb = StringBuilder()
        assetManager.open(scriptFile).use { inputStream ->
            Scanner(inputStream).use { scanner ->
                while (scanner.hasNextLine()) {
                    sb.append(scanner.nextLine().trim()).append(" ")
                }
            }
        }
        return sb.toString().trim()
    }
}
