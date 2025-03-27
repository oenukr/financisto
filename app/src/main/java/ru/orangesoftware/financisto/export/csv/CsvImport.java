package ru.orangesoftware.financisto.export.csv;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.app.DependenciesHolder;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.export.CategoryCache;
import ru.orangesoftware.financisto.export.CategoryInfo;
import ru.orangesoftware.financisto.export.ImportExportException;
import ru.orangesoftware.financisto.export.ProgressListener;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.Category;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.model.MyEntity;
import ru.orangesoftware.financisto.model.Payee;
import ru.orangesoftware.financisto.model.Project;
import ru.orangesoftware.financisto.model.Transaction;
import ru.orangesoftware.financisto.model.TransactionAttribute;
import ru.orangesoftware.financisto.utils.Logger;
import ru.orangesoftware.financisto.utils.Utils;

public class CsvImport {

    private final Logger logger = new DependenciesHolder().getLogger();

    private final DatabaseAdapter db;
    private final CsvImportOptions options;
    private final Account account;
    private final char decimalSeparator;
    private final char groupSeparator;
    private ProgressListener progressListener;
    private final Context context;

    public CsvImport(Context context, DatabaseAdapter db, CsvImportOptions options) {
        this.db = db;
        this.options = options;
        this.account = db.getAccount(options.getSelectedAccountId());
        this.decimalSeparator = options.getCurrency().decimalSeparator.charAt(1);
        this.groupSeparator = options.getCurrency().groupSeparator.charAt(1);
        this.context = context;
    }

    public Object doImport() throws Exception {
        long t0 = System.currentTimeMillis();
        List<CsvTransaction> transactions = parseTransactions();
        long t1 = System.currentTimeMillis();
        logger.i("Parsing transactions =" + (t1 - t0) + "ms");
        Map<String, Category> categories = collectAndInsertCategories(transactions);
        long t2 = System.currentTimeMillis();
        logger.i("Collecting categories =" + (t2 - t1) + "ms");
        Map<String, Project> projects = collectAndInsertProjects(transactions);
        long t3 = System.currentTimeMillis();
        logger.i("Collecting projects =" + (t3 - t2) + "ms");
        Map<String, Payee> payees = collectAndInsertPayees(transactions);
        long t4 = System.currentTimeMillis();
        logger.i("Collecting payees =" + (t4 - t3) + "ms");
        Map<String, Currency> currencies = collectAndInsertCurrencies(transactions);
        long t5 = System.currentTimeMillis();
        logger.i("Collecting currencies =" + (t5 - t4) + "ms");
        importTransactions(transactions, currencies, categories, projects, payees);
        long t6 = System.currentTimeMillis();
        logger.i("Inserting transactions =" + (t6 - t5) + "ms");
        logger.i("Overall csv import =" + ((t6 - t0) / 1000) + "s");
        return options.getFilename() + " imported!";
    }

    public Map<String, Project> collectAndInsertProjects(List<CsvTransaction> transactions) {
        Map<String, Project> map = db.getAllProjectsByTitleMap(false);
        for (CsvTransaction transaction : transactions) {
            String project = transaction.getProject();
            if (isNewProject(map, project)) {
                Project p = new Project();
                p.title = project;
                p.isActive = true;
                db.saveOrUpdate(p);
                map.put(project, p);
            }
        }
        return map;
    }

    private boolean isNewProject(Map<String, Project> map, String project) {
        return Utils.isNotEmpty(project) && !"No project".equals(project) && !map.containsKey(project);
    }

    public Map<String, Payee> collectAndInsertPayees(List<CsvTransaction> transactions) {
        Map<String, Payee> map = db.getAllPayeeByTitleMap();
        for (CsvTransaction transaction : transactions) {
            String payee = transaction.getPayee();
            if (isNewEntity(map, payee)) {
                Payee p = new Payee();
                p.title = payee;
                db.saveOrUpdate(p);
                map.put(payee, p);
            }
        }
        return map;
    }

    private boolean isNewEntity(Map<String, ? extends MyEntity> map, String name) {
        return Utils.isNotEmpty(name) && !map.containsKey(name);
    }

    public Map<String, Category> collectAndInsertCategories(List<CsvTransaction> transactions) {
        Set<CategoryInfo> categories = collectCategories(transactions);
        CategoryCache cache = new CategoryCache();
        cache.loadExistingCategories(db);
        cache.insertCategories(db, categories);
        return cache.categoryNameToCategory;
    }

    private Map<String, Currency> collectAndInsertCurrencies(List<CsvTransaction> transactions) {
        Map<String, Currency> map = db.getAllCurrenciesByTtitleMap();
        for (CsvTransaction transaction : transactions) {
            String currency = transaction.getOriginalCurrency();
            if (isNewEntity(map, currency)) {
                Currency c = new Currency();
                c.name = currency;
                c.symbol = currency;
                c.title = currency;
                c.decimalSeparator = Currency.EMPTY.decimalSeparator;
                c.groupSeparator = Currency.EMPTY.groupSeparator;
                c.isDefault = false;
                db.saveOrUpdate(c);
                map.put(currency, c);
            }
        }
        return map;
    }

    private void importTransactions(List<CsvTransaction> transactions,
                                    Map<String, Currency> currencies,
                                    Map<String, Category> categories,
                                    Map<String, Project> projects,
                                    Map<String, Payee> payees) {
        SQLiteDatabase database = db.db();
        database.beginTransaction();
        try {
            List<TransactionAttribute> emptyAttributes = Collections.emptyList();
            int count = 0;
            int totalCount = transactions.size();
            for (CsvTransaction transaction : transactions) {
                Transaction t = transaction.createTransaction(currencies, categories, projects, payees);
                db.insertOrUpdateInTransaction(t, emptyAttributes);
                if (++count % 100 == 0) {
                    logger.i("Inserted " + count + " out of " + totalCount);
                    if (progressListener != null) {
                        progressListener.onProgress((int) (100f * count / totalCount));
                    }
                }
            }
            logger.i("Total transactions inserted: " + count);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private List<CsvTransaction> parseTransactions() throws Exception {
        String csvFilename = options.getFilename();
        boolean parseLine = false;
        List<String> header = null;
        if (!options.getUseHeaderFromFile()) {
            parseLine = true;
            header = Arrays.asList(CsvExport.HEADER);
        }
        try {
            long deltaTime = 0;
            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
            DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(options.getFilename()));
            InputStream inputStream = context.getContentResolver().openInputStream(file.getUri());
            Csv.Reader reader = new Csv.Reader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .delimiter(options.getFieldSeparator()).ignoreComments(true);
            List<CsvTransaction> transactions = new LinkedList<>();
            List<String> line;
            while ((line = reader.readLine()) != null) {
                if (parseLine) {
                    CsvTransaction transaction = new CsvTransaction();
                    transaction.setFromAccountId(this.account.id);
                    int countOfColumns = line.size();
                    for (int i = 0; i < countOfColumns; i++) {
                        String transactionField = myTrim(header.get(i));
                        if (!transactionField.isEmpty()) {
                            try {
                                String fieldValue = line.get(i);
                                if (!fieldValue.isEmpty()) {
                                    switch (transactionField) {
                                        case "date":
                                            transaction.setDate(options.getDateFormat().parse(fieldValue));
                                            break;
                                        case "time":
                                            transaction.setTime(format.parse(fieldValue));
                                            break;
                                        case "amount":
                                            Double fromAmountDouble = parseAmount(fieldValue);
                                            transaction.setFromAmount(fromAmountDouble.longValue());
                                            break;
                                        case "original amount":
                                            Double originalAmountDouble = parseAmount(fieldValue);
                                            transaction.setOriginalAmount(originalAmountDouble.longValue());
                                            break;
                                        case "original currency":
                                            transaction.setOriginalCurrency(fieldValue);
                                            break;
                                        case "payee":
                                            transaction.setPayee(fieldValue);
                                            break;
                                        case "category":
                                            transaction.setCategory(fieldValue);
                                            break;
                                        case "parent":
                                            transaction.setCategoryParent(fieldValue);
                                            break;
                                        case "note":
                                            transaction.setNote(fieldValue);
                                            break;
                                        case "project":
                                            transaction.setProject(fieldValue);
                                            break;
                                        case "currency":
                                            if (!account.currency.name.equals(fieldValue)) {
                                                throw new ImportExportException(R.string.import_wrong_currency_2, null, fieldValue);
                                            }
                                            transaction.setCurrency(fieldValue);
                                            break;
                                    }
                                }
                            } catch (IllegalArgumentException e) {
                                throw new Exception("IllegalArgumentException");
                            } catch (ParseException e) {
                                throw new Exception("ParseException");
                            }
                        }
                    }
                    transaction.setDelta(deltaTime++);
                    transactions.add(transaction);
                } else {
                    // first line of csv-file is table headline
                    parseLine = true;
                    header = line;
                }
            }
            return transactions;
        } catch (FileNotFoundException e) {
            if (csvFilename.contains(":")){
                throw new ImportExportException(R.string.import_file_not_found_2, null,
                        csvFilename.substring(0, csvFilename.indexOf(":")+1));
            }
            throw new Exception("Import file not found");
        }
    }

    private Double parseAmount(String fieldValue) {
        fieldValue = fieldValue.trim();
        if (!fieldValue.isEmpty()) {
            fieldValue = fieldValue.replace(groupSeparator + "", "");
            fieldValue = fieldValue.replace(decimalSeparator, '.');
            double fromAmount = Double.parseDouble(fieldValue);
            return fromAmount * 100.0;
        } else {
            return 0.0;
        }
    }

    public Set<CategoryInfo> collectCategories(List<CsvTransaction> transactions) {
        Set<CategoryInfo> categories = new HashSet<>();
        for (CsvTransaction transaction : transactions) {
            String category = transaction.getCategory();
            if (Utils.isNotEmpty(transaction.getCategoryParent())) {
                category = transaction.getCategoryParent() + CategoryInfo.SEPARATOR + category;
            }
            if (Utils.isNotEmpty(category)) {
                categories.add(new CategoryInfo(category, false));
                transaction.setCategory(category);
                transaction.setCategoryParent(null);
            }
        }
        return categories;
    }

    //Workaround function which is needed for reimport of CsvExport files
    private String myTrim(String s) {
        if (Character.isLetter(s.charAt(0))) {
            return s;
        } else {
            return s.substring(1);
        }

    }

    void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }
}
