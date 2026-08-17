package ch.endte.syncmatica.thirdparty;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.data.ServerPlacement;
import ch.endte.syncmatica.data.ServerPosition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ThirdPartyProjectRecord
{
    public static final String STATUS_LOCAL = "local";
    public static final String STATUS_SYNCED = "synced";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_FAILED = "failed";

    private String projectKey = "";
    private String projectId = "";
    private Project project = new Project();
    private ServerPlacement placement;
    private String status = STATUS_LOCAL;
    private String updatedAt = "";
    private String lastSyncMessage = "";
    private boolean pendingImport = false;
    private boolean pendingCollected = false;
    private final Map<String, Integer> collected = new LinkedHashMap<>();
    private final List<ProjectMaterial> materials = new ArrayList<>();
    private final List<MaterialClaim> claims = new ArrayList<>();
    private final List<StorageZone> zones = new ArrayList<>();

    public ThirdPartyProjectRecord(final String projectId, final ServerPlacement placement)
    {
        this.projectId = projectId == null ? "" : projectId;
        setPlacement(placement);
    }

    public String getProjectKey()
    {
        return projectKey;
    }

    public void setProjectKey(final String projectKey)
    {
        this.projectKey = projectKey == null ? "" : projectKey;
    }

    public String getProjectId()
    {
        return projectId;
    }

    public void setProjectId(final String projectId)
    {
        this.projectId = projectId == null ? "" : projectId;
        project.projectId = this.projectId;
    }

    public Project getProject()
    {
        return project;
    }

    public void setProject(final Project project)
    {
        if (project != null)
        {
            this.project = project;
            if (!project.projectId.isBlank())
            {
                this.projectId = project.projectId;
            }
        }
    }

    public ServerPlacement getPlacement()
    {
        return placement;
    }

    public void setPlacement(final ServerPlacement placement)
    {
        this.placement = placement;
        if (placement != null)
        {
            project.placementId = placement.getId().toString();
            project.schematicHash = placement.getHash().toString();
            project.name = placement.getName();
            project.dimension = placement.getDimension();
            project.originX = placement.getPosition().getX();
            project.originY = placement.getPosition().getY();
            project.originZ = placement.getPosition().getZ();
            project.owner = placement.getOwner() != null ? placement.getOwner().getName() : "";
            if (project.projectId == null || project.projectId.isBlank())
            {
                project.projectId = projectId;
            }
        }
    }

    public String getPlacementId()
    {
        return placement != null ? placement.getId().toString() : project.placementId;
    }

    public String getSchematicHash()
    {
        return placement != null ? placement.getHash().toString() : project.schematicHash;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(final String status)
    {
        this.status = status == null ? STATUS_LOCAL : status;
        project.status = this.status;
    }

    public String getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(final String updatedAt)
    {
        this.updatedAt = updatedAt == null ? "" : updatedAt;
        project.updatedAt = this.updatedAt;
    }

    public String getLastSyncMessage()
    {
        return lastSyncMessage;
    }

    public void setLastSyncMessage(final String lastSyncMessage)
    {
        this.lastSyncMessage = lastSyncMessage == null ? "" : lastSyncMessage;
    }

    public boolean isPendingImport()
    {
        return pendingImport;
    }

    public void setPendingImport(final boolean pendingImport)
    {
        this.pendingImport = pendingImport;
    }

    public boolean isPendingCollected()
    {
        return pendingCollected;
    }

    public void setPendingCollected(final boolean pendingCollected)
    {
        this.pendingCollected = pendingCollected;
    }

    public Map<String, Integer> getCollected()
    {
        return collected;
    }

    public List<ProjectMaterial> getMaterials()
    {
        return materials;
    }

    public List<MaterialClaim> getClaims()
    {
        return claims;
    }

    public List<StorageZone> getZones()
    {
        return zones;
    }

    public void removeClaim(final String claimId)
    {
        if (claimId == null)
        {
            return;
        }
        claims.removeIf(claim -> claimId.equals(claim.claimId));
    }

    public void mergeProjectDetails(final JsonObject response)
    {
        if (response == null)
        {
            return;
        }

        if (response.has("project") && response.get("project").isJsonObject())
        {
            final JsonObject projectObj = response.getAsJsonObject("project");
            final Project nextProject = Project.fromJson(projectObj);
            setProject(nextProject);
        }
        if (response.has("projectId"))
        {
            setProjectId(response.get("projectId").getAsString());
        }
        if (response.has("status"))
        {
            setStatus(response.get("status").getAsString());
        }
        if (response.has("updatedAt"))
        {
            setUpdatedAt(response.get("updatedAt").getAsString());
        }

        if (response.has("materials") && response.get("materials").isJsonArray())
        {
            replaceMaterials(ProjectJson.readMaterials(response.getAsJsonArray("materials")));
        }
        if (response.has("claims") && response.get("claims").isJsonArray())
        {
            replaceClaims(ProjectJson.readClaims(response.getAsJsonArray("claims")));
        }
        if (response.has("zones") && response.get("zones").isJsonArray())
        {
            replaceZones(ProjectJson.readZones(response.getAsJsonArray("zones")));
        }
        if (response.has("storageZones") && response.get("storageZones").isJsonArray())
        {
            replaceZones(ProjectJson.readZones(response.getAsJsonArray("storageZones")));
        }

        if (response.has("collected") && response.get("collected").isJsonObject())
        {
            collected.clear();
            final JsonObject aggregated = response.getAsJsonObject("collected");
            for (final Map.Entry<String, JsonElement> entry : aggregated.entrySet())
            {
                if (entry.getValue().isJsonPrimitive())
                {
                    collected.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }
        }
    }

    public JsonObject toJson()
    {
        final JsonObject obj = new JsonObject();
        obj.addProperty("projectKey", projectKey);
        obj.addProperty("projectId", projectId);
        obj.addProperty("status", status);
        obj.addProperty("updatedAt", updatedAt);
        obj.addProperty("lastSyncMessage", lastSyncMessage);
        obj.addProperty("pendingImport", pendingImport);
        obj.addProperty("pendingCollected", pendingCollected);
        obj.add("project", project.toJson());
        if (placement != null)
        {
            obj.add("placement", placement.toJson());
        }
        final JsonObject collectedJson = new JsonObject();
        collected.forEach(collectedJson::addProperty);
        obj.add("collected", collectedJson);
        obj.add("materials", ProjectJson.writeMaterials(materials));
        obj.add("claims", ProjectJson.writeClaims(claims));
        obj.add("zones", ProjectJson.writeZones(zones));
        return obj;
    }

    public static ThirdPartyProjectRecord fromJson(final JsonObject obj, final Context context)
    {
        if (obj == null)
        {
            return null;
        }

        final ServerPlacement placement = obj.has("placement") && obj.get("placement").isJsonObject()
                ? ServerPlacement.fromJson(obj.getAsJsonObject("placement"), context)
                : null;
        final ThirdPartyProjectRecord record = new ThirdPartyProjectRecord(ThirdPartyJson.string(obj, "projectId"), placement);
        record.setProjectKey(ThirdPartyJson.string(obj, "projectKey"));
        record.setStatus(ThirdPartyJson.string(obj, "status").isBlank() ? STATUS_LOCAL : ThirdPartyJson.string(obj, "status"));
        record.setUpdatedAt(ThirdPartyJson.string(obj, "updatedAt"));
        record.setLastSyncMessage(ThirdPartyJson.string(obj, "lastSyncMessage"));
        record.setPendingImport(ThirdPartyJson.bool(obj, "pendingImport", false));
        record.setPendingCollected(ThirdPartyJson.bool(obj, "pendingCollected", false));

        if (obj.has("project") && obj.get("project").isJsonObject())
        {
            record.setProject(Project.fromJson(obj.getAsJsonObject("project")));
        }
        if (obj.has("collected") && obj.get("collected").isJsonObject())
        {
            for (final Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("collected").entrySet())
            {
                if (entry.getValue().isJsonPrimitive())
                {
                    record.collected.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }
        }
        if (obj.has("materials") && obj.get("materials").isJsonArray())
        {
            record.replaceMaterials(ProjectJson.readMaterials(obj.getAsJsonArray("materials")));
        }
        if (obj.has("claims") && obj.get("claims").isJsonArray())
        {
            record.replaceClaims(ProjectJson.readClaims(obj.getAsJsonArray("claims")));
        }
        if (obj.has("zones") && obj.get("zones").isJsonArray())
        {
            record.replaceZones(ProjectJson.readZones(obj.getAsJsonArray("zones")));
        }
        return record;
    }

    private void replaceMaterials(final Collection<ProjectMaterial> next)
    {
        materials.clear();
        if (next != null)
        {
            materials.addAll(next);
        }
    }

    private void replaceClaims(final Collection<MaterialClaim> next)
    {
        claims.clear();
        if (next != null)
        {
            claims.addAll(next);
        }
    }

    private void replaceZones(final Collection<StorageZone> next)
    {
        zones.clear();
        if (next != null)
        {
            zones.addAll(next);
        }
    }

}
