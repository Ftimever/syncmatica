package ch.endte.syncmatica.thirdparty;

import com.google.gson.JsonObject;

public class Project
{
    public String projectId = "";
    public String placementId = "";
    public String schematicHash = "";
    public String name = "";
    public String dimension = "";
    public int originX = 0;
    public int originY = 0;
    public int originZ = 0;
    public String owner = "";
    public String status = "";
    public String updatedAt = "";

    public JsonObject toJson()
    {
        final JsonObject obj = new JsonObject();
        obj.addProperty("projectId", projectId);
        obj.addProperty("placementId", placementId);
        obj.addProperty("schematicHash", schematicHash);
        obj.addProperty("name", name);
        obj.addProperty("dimension", dimension);
        obj.addProperty("originX", originX);
        obj.addProperty("originY", originY);
        obj.addProperty("originZ", originZ);
        obj.addProperty("owner", owner);
        obj.addProperty("status", status);
        obj.addProperty("updatedAt", updatedAt);
        return obj;
    }

    public static Project fromJson(final JsonObject obj)
    {
        final Project project = new Project();
        if (obj == null)
        {
            return project;
        }
        project.projectId = readString(obj, "projectId");
        project.placementId = readString(obj, "placementId");
        project.schematicHash = readString(obj, "schematicHash");
        project.name = readString(obj, "name");
        project.dimension = readString(obj, "dimension");
        project.originX = readInt(obj, "originX");
        project.originY = readInt(obj, "originY");
        project.originZ = readInt(obj, "originZ");
        project.owner = readString(obj, "owner");
        project.status = readString(obj, "status");
        project.updatedAt = readString(obj, "updatedAt");
        return project;
    }

    static String readString(final JsonObject obj, final String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    static int readInt(final JsonObject obj, final String key)
    {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : 0;
    }
}
