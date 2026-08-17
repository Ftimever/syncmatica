package ch.endte.syncmatica.thirdparty;

import com.google.gson.JsonObject;

public class MaterialClaim
{
    public String claimId = "";
    public String projectId = "";
    public String materialKey = "";
    public String assignee = "";
    public int targetAmount = 0;
    public int fulfilledAmount = 0;
    public String colorTag = "";
    public String updatedAt = "";

    public JsonObject toJson()
    {
        final JsonObject obj = new JsonObject();
        obj.addProperty("claimId", claimId);
        obj.addProperty("projectId", projectId);
        obj.addProperty("materialKey", materialKey);
        obj.addProperty("assignee", assignee);
        obj.addProperty("targetAmount", targetAmount);
        obj.addProperty("fulfilledAmount", fulfilledAmount);
        obj.addProperty("colorTag", colorTag);
        obj.addProperty("updatedAt", updatedAt);
        return obj;
    }

    public static MaterialClaim fromJson(final JsonObject obj)
    {
        final MaterialClaim claim = new MaterialClaim();
        claim.claimId = ThirdPartyJson.string(obj, "claimId");
        claim.projectId = ThirdPartyJson.string(obj, "projectId");
        claim.materialKey = ThirdPartyJson.string(obj, "materialKey");
        claim.assignee = ThirdPartyJson.string(obj, "assignee");
        claim.targetAmount = ThirdPartyJson.integer(obj, "targetAmount");
        claim.fulfilledAmount = ThirdPartyJson.integer(obj, "fulfilledAmount");
        claim.colorTag = ThirdPartyJson.string(obj, "colorTag");
        claim.updatedAt = ThirdPartyJson.string(obj, "updatedAt");
        return claim;
    }
}
