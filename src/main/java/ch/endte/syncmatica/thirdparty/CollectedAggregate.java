package ch.endte.syncmatica.thirdparty;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

public class CollectedAggregate
{
    public String projectId = "";
    public String materialKey = "";
    public int collectedAmount = 0;
    public Set<String> sourcesUsed = new HashSet<>();
    public String computedAt = "";

    public JsonObject toJson()
    {
        final JsonObject obj = new JsonObject();
        obj.addProperty("projectId", projectId);
        obj.addProperty("materialKey", materialKey);
        obj.addProperty("collectedAmount", collectedAmount);
        final JsonArray sources = new JsonArray();
        sourcesUsed.forEach(sources::add);
        obj.add("sourcesUsed", sources);
        obj.addProperty("computedAt", computedAt);
        return obj;
    }

    public static CollectedAggregate fromJson(final JsonObject obj)
    {
        final CollectedAggregate aggregate = new CollectedAggregate();
        aggregate.projectId = ThirdPartyJson.string(obj, "projectId");
        aggregate.materialKey = ThirdPartyJson.string(obj, "materialKey");
        aggregate.collectedAmount = ThirdPartyJson.integer(obj, "collectedAmount");
        aggregate.computedAt = ThirdPartyJson.string(obj, "computedAt");
        if (obj.has("sourcesUsed") && obj.get("sourcesUsed").isJsonArray())
        {
            for (final JsonElement source : obj.getAsJsonArray("sourcesUsed"))
            {
                aggregate.sourcesUsed.add(source.getAsString());
            }
        }
        return aggregate;
    }
}
