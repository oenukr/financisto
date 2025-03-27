package ru.orangesoftware.financisto.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import timber.log.Timber;

public abstract class AbstractExportTest<T extends Export, O> extends AbstractImportExportTest {

    String exportAsString(O options) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        T export = createExport(options);
        export.export(bos);
        String s = bos.toString(StandardCharsets.UTF_8);
        Timber.d("Export: %s", s);
        return s;
    }

    protected abstract T createExport(O options);

}
