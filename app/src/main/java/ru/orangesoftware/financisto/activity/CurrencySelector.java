package ru.orangesoftware.financisto.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.app.DependenciesHolder; // Keep for Logger for now
import ru.orangesoftware.financisto.db.dao.CurrencyDao;
import ru.orangesoftware.financisto.db.entity.CurrencyEntity;
import ru.orangesoftware.financisto.export.csv.Csv;
// import ru.orangesoftware.financisto.utils.CurrencyCache; // Commented out
import ru.orangesoftware.financisto.utils.Logger;
import ru.orangesoftware.financisto.utils.Utils; // For Utils.isTrue if used for isDefault, though CSV parsing is different now

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.launch;
import kotlinx.coroutines.withContext;
import kotlin.Unit;

public class CurrencySelector {

    private final Logger logger = new DependenciesHolder().getLogger(); // Keep logger for now

    public interface OnCurrencyCreatedListener {
        void onCreated(long currencyId);
    }

    private final Context context;
    // private final MyEntityManager em; // Replaced
    private final CurrencyDao currencyDao; // Added
    private final List<List<String>> currenciesFromAsset; // Renamed for clarity
    private final OnCurrencyCreatedListener listener;

    private int selectedCurrencyIndex = 0; // Index in the displayed list

    public CurrencySelector(Context context, CurrencyDao currencyDao, OnCurrencyCreatedListener listener) {
        this.context = context;
        // this.em = em; // Replaced
        this.currencyDao = currencyDao; // Added
        this.listener = listener;
        this.currenciesFromAsset = readCurrenciesFromAsset();
    }

    public void show() {
        String[] items = createDisplayItemsList(currenciesFromAsset);
        new AlertDialog.Builder(context)
                .setTitle(R.string.currencies) // This was R.string.select_currency, but R.string.currencies is used in original
                .setIcon(R.drawable.ic_dialog_currency)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    // selectedCurrencyIndex is 0 for "New Currency", >0 for asset list
                    if (selectedCurrencyIndex == 0) { // "New Currency" was selected
                        if (listener != null) {
                            listener.onCreated(0); // Signal to caller to open CurrencyActivity
                        }
                    } else {
                        // An existing currency from assets was chosen to be added
                        addSelectedCurrencyFromAsset(selectedCurrencyIndex -1); // Adjust index for asset list
                    }
                    dialogInterface.dismiss();
                })
                .setSingleChoiceItems(items, selectedCurrencyIndex, (dialogInterface, i) -> selectedCurrencyIndex = i)
                .show();
    }

    // This method is called when an item from the ASSET LIST (currencies.csv) is chosen
    private void addSelectedCurrencyFromAsset(int assetListIndex) {
        if (assetListIndex < 0 || assetListIndex >= currenciesFromAsset.size()) {
            if (listener != null) {
                 listener.onCreated(0); // Invalid selection from asset
            }
            return;
        }

        List<String> csvLine = currenciesFromAsset.get(assetListIndex);
        CurrencyEntity c = new CurrencyEntity();
        // CSV format: Name,ISO_Code,Symbol,Decimals,DecimalSeparator,GroupSeparator
        c.setName(csvLine.get(0));
        c.setIsoCode(csvLine.get(1));
        c.setSymbol(csvLine.get(2));

        try {
            // int decimals = Integer.parseInt(csvLine.get(3)); // Decimals not directly in CurrencyEntity
            // For now, we don't have a direct field for 'decimals'.
            // It's often implicit (e.g., 2 for most currencies).
        } catch (NumberFormatException e) { /* ignore malformed decimal count */ }

        // Separators are Char in Entity. CSV stores them as "COMMA", "PERIOD", etc.
        c.setDecimalSeparator(decodeSeparatorChar(csvLine.get(4)));
        c.setGroupSeparator(decodeSeparatorChar(csvLine.get(5)));

        // isDefault logic
        GlobalScope.launch(Dispatchers.IO, () -> {
            List<CurrencyEntity> allCurrenciesInDb = currencyDao.getAllSuspendable();
            c.setDefault(allCurrenciesInDb.isEmpty());

            long generatedId = currencyDao.insert(c);

            // CurrencyCache.initialize(em); // TODO: Refactor or re-initialize CurrencyCache

            withContext(Dispatchers.getMain(), () -> {
                if (listener != null) {
                    listener.onCreated(generatedId);
                }
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

    private Character decodeSeparatorChar(String s) {
        if ("COMMA".equals(s)) {
            return ',';
        } else if ("PERIOD".equals(s)) {
            return '.';
        } else if ("SPACE".equals(s)) { // Note: original was endsWith
            return ' ';
        }
        return null; // Or a default like '.' or ','
    }

    private List<List<String>> readCurrenciesFromAsset() {
        try {
            try (InputStreamReader r = new InputStreamReader(context.getAssets().open("currencies.csv"), StandardCharsets.UTF_8)) {
                Csv.Reader csv = new Csv.Reader(r).delimiter(',').ignoreComments(true).ignoreEmptyLines(true);
                List<List<String>> allLines = new ArrayList<>();
                List<String> line;
                while ((line = csv.readLine()) != null) {
                    if (line.size() == 6) { // Name,ISO_Code,Symbol,Decimals,DecimalSeparator,GroupSeparator
                        allLines.add(line);
                    }
                }
                return allLines;
            }
        } catch (IOException e) {
            logger.e(e, "IO error while reading currencies");
            Toast.makeText(context, e.getClass().getSimpleName() + ":" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return Collections.emptyList();
    }

    private String[] createDisplayItemsList(List<List<String>> currencies) {
        int size = currencies.size();
        String[] items = new String[size + 1]; // +1 for "New Currency" option
        items[0] = context.getString(R.string.new_currency); // User can choose to create a new one manually
        for (int i = 0; i < size; i++) {
            List<String> c = currencies.get(i);
            items[i + 1] = c.get(0) + " (" + c.get(1) + ")"; // Name (ISO_Code)
        }
        return items;
    }
}
