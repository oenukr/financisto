package ru.orangesoftware.financisto.backup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.orangesoftware.financisto.backup.Backup.BACKUP_TABLES
import ru.orangesoftware.financisto.backup.Backup.tableHasOrder
import ru.orangesoftware.financisto.backup.Backup.tableHasSystemIds
import ru.orangesoftware.financisto.db.DatabaseHelper.ACCOUNT_TABLE
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto.utils.Utils
import ru.orangesoftware.orb.EntityManager.DEF_SORT_COL
import java.io.BufferedWriter

class DatabaseExport(
    context: Context,
    private val db: SupportSQLiteDatabase,
    useGZip: Boolean
) : Export(context, useGZip) {

    private val packageInfo = Utils.getPackageInfo(context)

    override fun getExtension(): String = ".backup"

    override fun writeHeader(bw: BufferedWriter) {
        packageInfo?.let {
            bw.write("PACKAGE:")
            bw.write(it.packageName)
            bw.write("\n")
            bw.write("LONG_VERSION_CODE:")
            bw.write(it.longVersionCode.toString())
            bw.write("\n")
            bw.write("VERSION_NAME:")
            bw.write(it.versionName)
            bw.write("\n")
            bw.write("DATABASE_VERSION:")
            bw.write(db.version.toString())
            bw.write("\n")
            bw.write("#START\n")
        }
    }

    override fun writeBody(bw: BufferedWriter) = BACKUP_TABLES.forEach {
        exportTable(bw, it)
    }

    private fun exportTable(bw: BufferedWriter, tableName: String) {
        val orderedTable = tableHasOrder(tableName)
        val customOrdered = ACCOUNT_TABLE == tableName
        val sql = "select * from $tableName" +
                if (tableHasSystemIds(tableName)) " WHERE _id > 0 " else " " +
                if (orderedTable) " order by $DEF_SORT_COL asc" else ""
        db.query(sql, emptyArray()).use { c ->
            val columnNames = c.columnNames
            val cols = columnNames.size
            while (c.moveToNext()) {
                bw.write("\$ENTITY:")
                bw.write(tableName)
                bw.write("\n")
                for (i in 0 until cols) {
                    val colName = columnNames[i]
                    if (DEF_SORT_COL != colName || customOrdered) {
                        val value = c.getString(i)
                        if (value != null) {
                            bw.write(colName)
                            bw.write(":")
                            bw.write(removeNewLine(value))
                            bw.write("\n")
                        }
                    }
                }
                bw.write("$$\n")
            }
        }
    }

//    public static void copy(File source, File dest) throws IOException {
//        FileChannel in = null, out = null;
//        try {
//            in = new FileInputStream(source).getChannel();
//            out = new FileOutputStream(dest).getChannel();
//
//            long size = in.size();
//            MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
//
//            out.write(buf);
//
//        } finally {
//            if (in != null) in.close();
//            if (out != null) out.close();
//        }
//    }

    override fun writeFooter(bw: BufferedWriter) = bw.write("#END")

    private fun removeNewLine(value: String): String = value.replace('\n', ' ')
}
