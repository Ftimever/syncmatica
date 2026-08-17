package ch.endte.syncmatica.thirdparty;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class ProjectJson
{
    private ProjectJson()
    {
    }

    static JsonArray writeMaterials(final List<ProjectMaterial> materials)
    {
        final JsonArray arr = new JsonArray();
        for (final ProjectMaterial material : materials)
        {
            final JsonObject obj = new JsonObject();
            obj.addProperty("materialKey", material.materialKey);
            obj.addProperty("itemId", material.itemId);
            obj.addProperty("nbtHash", material.nbtHash);
            obj.addProperty("displayName", material.displayName);
            obj.addProperty("required", material.required);
            obj.addProperty("collected", material.collected);
            obj.addProperty("reserved", material.reserved);
            obj.addProperty("missing", material.missing);
            arr.add(obj);
        }
        return arr;
    }

    static List<ProjectMaterial> readMaterials(final JsonArray arr)
    {
        final List<ProjectMaterial> materials = new ArrayList<>();
        if (arr == null)
        {
            return materials;
        }
        for (final JsonElement elem : arr)
        {
            if (elem == null || !elem.isJsonObject())
            {
                continue;
            }
            final JsonObject obj = elem.getAsJsonObject();
            final ProjectMaterial material = new ProjectMaterial();
            material.materialKey = readString(obj, "materialKey");
            material.itemId = readString(obj, "itemId");
            material.nbtHash = readString(obj, "nbtHash");
            material.displayName = readString(obj, "displayName");
            material.required = readInt(obj, "required");
            material.collected = readInt(obj, "collected");
            material.reserved = readInt(obj, "reserved");
            material.missing = readInt(obj, "missing");
            materials.add(material);
        }
        return materials;
    }

    static JsonArray writeClaims(final List<MaterialClaim> claims)
    {
        final JsonArray arr = new JsonArray();
        for (final MaterialClaim claim : claims)
        {
            final JsonObject obj = new JsonObject();
            obj.addProperty("claimId", claim.claimId);
            obj.addProperty("projectId", claim.projectId);
            obj.addProperty("materialKey", claim.materialKey);
            obj.addProperty("assignee", claim.assignee);
            obj.addProperty("targetAmount", claim.targetAmount);
            obj.addProperty("fulfilledAmount", claim.fulfilledAmount);
            obj.addProperty("colorTag", claim.colorTag);
            obj.addProperty("updatedAt", claim.updatedAt);
            arr.add(obj);
        }
        return arr;
    }

    static List<MaterialClaim> readClaims(final JsonArray arr)
    {
        final List<MaterialClaim> claims = new ArrayList<>();
        if (arr == null)
        {
            return claims;
        }
        for (final JsonElement elem : arr)
        {
            if (elem == null || !elem.isJsonObject())
            {
                continue;
            }
            final JsonObject obj = elem.getAsJsonObject();
            final MaterialClaim claim = new MaterialClaim();
            claim.claimId = readString(obj, "claimId");
            claim.projectId = readString(obj, "projectId");
            claim.materialKey = readString(obj, "materialKey");
            claim.assignee = readString(obj, "assignee");
            claim.targetAmount = readInt(obj, "targetAmount");
            claim.fulfilledAmount = readInt(obj, "fulfilledAmount");
            claim.colorTag = readString(obj, "colorTag");
            claim.updatedAt = readString(obj, "updatedAt");
            claims.add(claim);
        }
        return claims;
    }

    static JsonArray writeZones(final List<StorageZone> zones)
    {
        final JsonArray arr = new JsonArray();
        for (final StorageZone zone : zones)
        {
            final JsonObject obj = new JsonObject();
            obj.addProperty("zoneId", zone.zoneId);
            obj.addProperty("projectId", zone.projectId);
            obj.addProperty("dimension", zone.dimension);
            obj.addProperty("minX", zone.minX);
            obj.addProperty("minY", zone.minY);
            obj.addProperty("minZ", zone.minZ);
            obj.addProperty("maxX", zone.maxX);
            obj.addProperty("maxY", zone.maxY);
            obj.addProperty("maxZ", zone.maxZ);
            obj.addProperty("enabled", zone.enabled);
            arr.add(obj);
        }
        return arr;
    }

    static List<StorageZone> readZones(final JsonArray arr)
    {
        final List<StorageZone> zones = new ArrayList<>();
        if (arr == null)
        {
            return zones;
        }
        for (final JsonElement elem : arr)
        {
            if (elem == null || !elem.isJsonObject())
            {
                continue;
            }
            final JsonObject obj = elem.getAsJsonObject();
            final StorageZone zone = new StorageZone();
            zone.zoneId = readString(obj, "zoneId");
            zone.projectId = readString(obj, "projectId");
            zone.dimension = readString(obj, "dimension");
            zone.minX = readInt(obj, "minX");
            zone.minY = readInt(obj, "minY");
            zone.minZ = readInt(obj, "minZ");
            zone.maxX = readInt(obj, "maxX");
            zone.maxY = readInt(obj, "maxY");
            zone.maxZ = readInt(obj, "maxZ");
            zone.enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
            zones.add(zone);
        }
        return zones;
    }

    private static String readString(final JsonObject obj, final String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private static int readInt(final JsonObject obj, final String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : 0;
    }
}
