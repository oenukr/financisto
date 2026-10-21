// SPDX-License-Identifier: GPL-2.0-only

package ru.orangesoftware.financisto.export.qif;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 9/26/11 7:50 PM
 */
public class QifBufferedReader {

    private final BufferedReader r;

    public QifBufferedReader(BufferedReader r) {
        this.r = r;
    }

    public String readLine() throws IOException {
        while (true) {
            String line = r.readLine();
            if (line == null) {
                return null;
            }
            line = line.trim();
            if (!line.isEmpty()) {
                return line;
            }
        }
    }

    public String peekLine() throws IOException {
        r.mark(256);
        String peek = readLine();
        r.reset();
        return peek;
    }

    private String trim(String s) {
        return s != null ? s.trim() : null;
    }

}
