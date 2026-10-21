// SPDX-License-Identifier: GPL-2.0-only

package ru.orangesoftware.financisto.export;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import ru.orangesoftware.financisto.export.qif.QifBufferedReader;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 10/11/11 10:13 PM
 */
public class QifBufferedReaderTest {

    @Test
    public void should_read_qif_file_line_by_line_trimming_each_line_and_skipping_empty_lines() throws IOException {
        String content =
                "!Account \n"+
                "\n"+
                "NMy Cash Account\n"+
                "\tTCash\n"+
                "\n"+
                "^\n"+
                "\n"+
                " \t!Type:Cash\n"+
                "MSome note here... \n"+
                "^\n";
        QifBufferedReader r = new QifBufferedReader(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content.getBytes()), StandardCharsets.UTF_8)));

        assertEquals("!Account", r.peekLine());
        assertEquals("!Account", r.readLine());

        assertEquals("NMy Cash Account", r.peekLine());
        assertEquals("NMy Cash Account", r.readLine());

        assertEquals("TCash", r.peekLine());
        assertEquals("TCash", r.readLine());

        assertEquals("^", r.peekLine());
        assertEquals("^", r.readLine());

        assertEquals("!Type:Cash", r.peekLine());
        assertEquals("!Type:Cash", r.readLine());

        assertEquals("MSome note here...", r.peekLine());
        assertEquals("MSome note here...", r.readLine());

        assertEquals("^", r.peekLine());
        assertEquals("^", r.readLine());
    }

}
