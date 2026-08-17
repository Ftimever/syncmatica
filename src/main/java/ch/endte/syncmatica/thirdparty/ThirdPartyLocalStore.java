package ch.endte.syncmatica.thirdparty;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.Syncmatica;
import ch.endte.syncmatica.util.SyncmaticaUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThirdPartyLocalStore
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String PROJECTS = "projects";
    private static final String AGGREGATES = "aggregates";
    private static final String SCAN_CACHE = "scanCache";
    private static final String CLAIMS = "claims";

    private final Context context;
    private final Path directory;

    public ThirdPartyLocalStore(final Context context)
    {
        this.context = context;
        this.directory = context.getConfigFolder().resolve("third_party");
    }

    public Map<String, ThirdPartyProjectRecord> loadProjects()
    {
        final Map<String, ThirdPartyProjectRecord> records = new LinkedHashMap<>();
        final JsonObject root = readObject(directory.resolve("projects.json"));
        if (root == null || !root.has(PROJECTS))
        {
            return records;
        }

        final JsonArray arr = root.getAsJsonArray(PROJECTS);
        for (final JsonElement elem : arr)
        {
            if (elem == null || !elem.isJsonObject())
            {
                continue;
            }

            final ThirdPartyProjectRecord record = ThirdPartyProjectRecord.fromJson(elem.getAsJsonObject(), context);
            if (record != null)
            {
                records.put(record.getProjectKey(), record);
            }
        }
        return records;
    }

    public void saveProjects(final Collection<ThirdPartyProjectRecord> records)
    {
        final JsonObject root = new JsonObject();
        final JsonArray arr = new JsonArray();
        for (final ThirdPartyProjectRecord record : records)
        {
            arr.add(record.toJson());
        }
        root.add(PROJECTS, arr);
        writeObject(directory.resolve("projects.json"), root);
    }

    public JsonObject loadAggregateCache()
    {
        final JsonObject root = readObject(directory.resolve("aggregate_cache.json"));
        return root == null ? new JsonObject() : root;
    }

    public void saveAggregateCache(final JsonObject aggregateCache)
    {
        writeObject(directory.resolve("aggregate_cache.json"), aggregateCache);
    }

    public JsonObject loadScanCache()
    {
        final JsonObject root = readObject(directory.resolve("scan_cache.json"));
        return root == null ? new JsonObject() : root;
    }

    public void saveScanCache(final JsonObject scanCache)
    {
        writeObject(directory.resolve("scan_cache.json"), scanCache);
    }

    public JsonObject loadClaims()
    {
        final JsonObject root = readObject(directory.resolve("claims.json"));
        return root == null ? new JsonObject() : root;
    }

    public void saveClaims(final JsonObject claims)
    {
        writeObject(directory.resolve("claims.json"), claims);
    }

    public void ensureCacheFiles()
    {
        ensureDirectory();
        if (!Files.exists(directory.resolve("projects.json")))
        {
            final JsonObject root = new JsonObject();
            root.add(PROJECTS, new JsonArray());
            writeObject(directory.resolve("projects.json"), root);
        }
        if (!Files.exists(directory.resolve("claims.json")))
        {
            final JsonObject root = new JsonObject();
            root.add(CLAIMS, new JsonArray());
            writeObject(directory.resolve("claims.json"), root);
        }
        if (!Files.exists(directory.resolve("scan_cache.json")))
        {
            final JsonObject root = new JsonObject();
            root.add(SCAN_CACHE, new JsonObject());
            writeObject(directory.resolve("scan_cache.json"), root);
        }
        if (!Files.exists(directory.resolve("aggregate_cache.json")))
        {
            final JsonObject root = new JsonObject();
            root.add(AGGREGATES, new JsonObject());
            writeObject(directory.resolve("aggregate_cache.json"), root);
        }
    }

    private JsonObject readObject(final Path file)
    {
        if (!Files.exists(file) || !Files.isReadable(file))
        {
            return null;
        }

        try (final BufferedReader reader = new BufferedReader(new FileReader(file.toFile())))
        {
            final JsonElement parsed = JsonParser.parseReader(reader);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }
        catch (final Exception e)
        {
            Syncmatica.LOGGER.error("ThirdPartyLocalStore: failed to read '{}'; {}", file.getFileName().toString(), e.getLocalizedMessage());
            return null;
        }
    }

    private void writeObject(final Path current, final JsonObject obj)
    {
        ensureDirectory();
        final Path backup = current.resolveSibling(current.getFileName().toString() + ".bak");
        final Path incoming = current.resolveSibling(current.getFileName().toString() + ".new");

        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(incoming.toFile())))
        {
            writer.write(GSON.toJson(obj));
        }
        catch (final Exception e)
        {
            Syncmatica.LOGGER.error("ThirdPartyLocalStore: failed to write '{}'; {}", incoming.getFileName().toString(), e.getLocalizedMessage());
            return;
        }

        SyncmaticaUtil.backupAndReplace(backup, current, incoming);
    }

    private void ensureDirectory()
    {
        try
        {
            if (!Files.exists(context.getConfigFolder()))
            {
                Files.createDirectory(context.getConfigFolder());
            }
            if (!Files.exists(directory))
            {
                Files.createDirectory(directory);
            }
        }
        catch (final Exception e)
        {
            throw new RuntimeException("Failed to create third-party sync cache directory", e);
        }
    }
}
