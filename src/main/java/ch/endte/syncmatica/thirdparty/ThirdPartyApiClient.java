package ch.endte.syncmatica.thirdparty;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ThirdPartyApiClient
{
    private static final Gson GSON = new Gson();

    private final HttpClient client;
    private final String baseUrl;
    private final String token;
    private final int timeoutMs;

    public ThirdPartyApiClient(final String baseUrl, final String token, final int timeoutMs)
    {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.token = token == null ? "" : token;
        this.timeoutMs = timeoutMs;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public boolean isConfigured()
    {
        return !baseUrl.isBlank();
    }

    public JsonObject importProject(final JsonObject body) throws IOException, InterruptedException
    {
        return postJson("/api/v1/projects/import", body);
    }

    public JsonObject listProjects() throws IOException, InterruptedException
    {
        return getJson("/api/v1/projects");
    }

    public JsonObject getProject(final String projectId) throws IOException, InterruptedException
    {
        return getJson("/api/v1/projects/" + projectId);
    }

    public JsonObject upsertClaim(final String projectId, final JsonObject body) throws IOException, InterruptedException
    {
        return postJson("/api/v1/projects/" + projectId + "/claims", body);
    }

    public JsonObject deleteClaim(final String claimId) throws IOException, InterruptedException
    {
        final HttpRequest.Builder builder = baseRequest("/api/v1/claims/" + claimId).DELETE();
        return sendJson(builder);
    }

    public JsonObject upsertZone(final String projectId, final JsonObject body) throws IOException, InterruptedException
    {
        return postJson("/api/v1/projects/" + projectId + "/zones", body);
    }

    public JsonObject uploadCollected(final String projectId, final JsonObject collected) throws IOException, InterruptedException
    {
        return postJson("/api/v1/projects/" + projectId + "/collected", collected);
    }

    private JsonObject getJson(final String path) throws IOException, InterruptedException
    {
        return sendJson(baseRequest(path).GET());
    }

    private JsonObject postJson(final String path, final JsonObject body) throws IOException, InterruptedException
    {
        final HttpRequest.Builder builder = baseRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        return sendJson(builder);
    }

    private JsonObject sendJson(final HttpRequest.Builder builder) throws IOException, InterruptedException
    {
        final HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        final int status = response.statusCode();
        if (status < 200 || status >= 300)
        {
            throw new IOException("Third-party API returned HTTP " + status);
        }

        final String body = response.body();
        if (body == null || body.isBlank())
        {
            return new JsonObject();
        }

        final JsonElement parsed = JsonParser.parseString(body);
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }

    private HttpRequest.Builder baseRequest(final String path)
    {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");

        if (!token.isBlank())
        {
            builder.header("Authorization", "Bearer " + token);
        }

        return builder;
    }

    private static String trimTrailingSlash(final String value)
    {
        if (value == null)
        {
            return "";
        }

        String result = value.trim();
        while (result.endsWith("/"))
        {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
