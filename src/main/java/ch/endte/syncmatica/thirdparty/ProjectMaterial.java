package ch.endte.syncmatica.thirdparty;

import com.google.gson.JsonObject;

public class ProjectMaterial
{
    public String materialKey = "";
    public String itemId = "";
    public String nbtHash = "";
    public String displayName = "";
    public int required = 0;
    public int collected = 0;
    public int reserved = 0;
    public int missing = 0;

    public JsonObject toJson()
    {
        final JsonObject obj = new JsonObject();
        obj.addProperty("materialKey", materialKey);
        obj.addProperty("itemId", itemId);
        obj.addProperty("nbtHash", nbtHash);
        obj.addProperty("displayName", displayName);
        obj.addProperty("required", required);
        obj.addProperty("collected", collected);
        obj.addProperty("reserved", reserved);
        obj.addProperty("missing", missing);
        return obj;
    }

    public static ProjectMaterial fromJson(final JsonObject obj)
    {
        final ProjectMaterial material = new ProjectMaterial();
        material.materialKey = Project.readString(obj, "materialKey");
        material.itemId = Project.readString(obj, "itemId");
        material.nbtHash = Project.readString(obj, "nbtHash");
        material.displayName = Project.readString(obj, "displayName");
        material.required = Project.readInt(obj, "required");
        material.collected = Project.readInt(obj, "collected");
        material.reserved = Project.readInt(obj, "reserved");
        material.missing = Project.readInt(obj, "missing");
        return material;
    }
}
