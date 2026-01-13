package ru.orangesoftware.financisto.http;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class FakeHttpClientWrapper extends HttpClientWrapper {

    public final Map<String, String> responses = new HashMap<>();
    public Exception error;

    public FakeHttpClientWrapper() {
        super(TestKoinHelper.INSTANCE.createDummyClient());
    }

    @Override
    public String getAsString(@NonNull String url) throws Exception {
        if (error != null) {
            throw error;
        }
        String response = responses.get(url);
        if (response == null) {
            response = responses.get("*");
        }
        return response;
    }

    public void givenResponse(String url, String response) {
        responses.put(url, response);
    }
}
