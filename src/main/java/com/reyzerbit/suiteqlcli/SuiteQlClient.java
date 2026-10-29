package com.reyzerbit.suiteqlcli;

import com.reyzerbit.suiteqlcli.model.Profile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SuiteQlClient
{
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_BACKOFF_MS = 1000;
    private static final long MAX_BACKOFF_MS = 30000;

    private final Profile profile;
    private final NetSuiteSigner signer;
    private final HttpClient http = HttpClient.newHttpClient();

    public SuiteQlClient(Profile profile)
    {
        this.profile = profile;
        this.signer = new NetSuiteSigner(profile);
    }

    /** Runs the query, following hasMore/offset until exhausted or maxRows (0 = unlimited) rows are collected. */
    public List<JsonNode> run(String query, int maxRows) throws Exception
    {
        List<JsonNode> rows = new ArrayList<>();
        int offset = 0;

        while (true)
        {
            int limit = maxRows > 0 ? Math.min(PAGE_SIZE, maxRows - rows.size()) : PAGE_SIZE;
            JsonNode page = fetchPage(query, limit, offset);

            for (JsonNode item : page.path("items"))
            {
                ((ObjectNode) item).remove("links");
                rows.add(item);
            }

            int count = page.path("count").asInt(0);
            offset += count;

            boolean done = !page.path("hasMore").asBoolean(false) || count == 0;
            if (done || (maxRows > 0 && rows.size() >= maxRows)) return rows;
        }
    }

    private JsonNode fetchPage(String query, int limit, int offset) throws Exception
    {
        URI uri = URI.create("https://" + profile.host() + ".suitetalk.api.netsuite.com/services/rest/query/v1/suiteql?limit=" + limit + "&offset=" + offset);
        String body = MAPPER.writeValueAsString(MAPPER.createObjectNode().put("q", query));

        for (int attempt = 1; ; attempt++)
        {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", signer.authorizationHeader("POST", uri))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Prefer", "transient")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 429 && attempt < MAX_ATTEMPTS)
            {
                long delay = backoffMs(response, attempt);
                System.err.println("NetSuite returned 429; retrying in " + delay + "ms (attempt " + attempt + " of " + MAX_ATTEMPTS + ").");
                Thread.sleep(delay);
                continue;
            }

            if (status / 100 != 2) throw new IllegalStateException("NetSuite returned HTTP " + status + ":\n" + response.body());

            return MAPPER.readTree(response.body());
        }
    }

    private long backoffMs(HttpResponse<?> response, int attempt)
    {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);

        if (retryAfter != null)
        {
            try { return Math.min(Long.parseLong(retryAfter.trim()) * 1000, MAX_BACKOFF_MS); }
            catch (NumberFormatException ignored) {}
        }

        long exponential = Math.min(BASE_BACKOFF_MS << (attempt - 1), MAX_BACKOFF_MS);
        return exponential + ThreadLocalRandom.current().nextLong(exponential / 2 + 1);
    }
}
