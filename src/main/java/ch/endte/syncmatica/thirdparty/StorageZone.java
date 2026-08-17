package ch.endte.syncmatica.thirdparty;

import com.google.gson.JsonObject;

public class StorageZone
{
    public String zoneId = "";
    public String projectId = "";
    public String dimension = "";
    public int minX = 0;
    public int minY = 0;
    public int minZ = 0;
    public int maxX = 0;
    public int maxY = 0;
    public int maxZ = 0;
    public boolean enabled = true;

    public JsonObject toJson()
    {
        final JsonObject obj = new JsonObject();
        obj.addProperty("zoneId", zoneId);
        obj.addProperty("projectId", projectId);
        obj.addProperty("dimension", dimension);
        obj.addProperty("minX", minX);
        obj.addProperty("minY", minY);
        obj.addProperty("minZ", minZ);
        obj.addProperty("maxX", maxX);
        obj.addProperty("maxY", maxY);
        obj.addProperty("maxZ", maxZ);
        obj.addProperty("enabled", enabled);
        return obj;
    }

    public static StorageZone fromJson(final JsonObject obj)
    {
        final StorageZone zone = new StorageZone();
        zone.zoneId = ThirdPartyJson.string(obj, "zoneId");
        zone.projectId = ThirdPartyJson.string(obj, "projectId");
        zone.dimension = ThirdPartyJson.string(obj, "dimension");
        zone.minX = ThirdPartyJson.integer(obj, "minX");
        zone.minY = ThirdPartyJson.integer(obj, "minY");
        zone.minZ = ThirdPartyJson.integer(obj, "minZ");
        zone.maxX = ThirdPartyJson.integer(obj, "maxX");
        zone.maxY = ThirdPartyJson.integer(obj, "maxY");
        zone.maxZ = ThirdPartyJson.integer(obj, "maxZ");
        zone.enabled = ThirdPartyJson.bool(obj, "enabled", true);
        return zone;
    }
}
