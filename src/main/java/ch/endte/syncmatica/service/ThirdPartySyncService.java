package ch.endte.syncmatica.service;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.Syncmatica;
import ch.endte.syncmatica.data.ServerPlacement;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.litematica.ScreenHelper;
import ch.endte.syncmatica.thirdparty.ThirdPartyApiClient;
import ch.endte.syncmatica.thirdparty.ThirdPartyLocalStore;
import ch.endte.syncmatica.thirdparty.ThirdPartyProjectRecord;
import ch.endte.syncmatica.thirdparty.MaterialClaim;
import ch.endte.syncmatica.thirdparty.MergedHudEntry;
import ch.endte.syncmatica.thirdparty.ProjectMaterial;
import ch.endte.syncmatica.thirdparty.StorageZone;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThirdPartySyncService extends AbstractService
{
    public enum Mode
    {
        LEGACY_SERVER("legacy_server"),
        THIRD_PARTY("third_party");

        private final String configValue;

        Mode(final String configValue)
        {
            this.configValue = configValue;
        }

        public String getConfigValue()
        {
            return configValue;
        }

        public static Mode fromConfig(final String value)
        {
            if (LEGACY_SERVER.configValue.equalsIgnoreCase(value))
            {
                return LEGACY_SERVER;
            }
            return THIRD_PARTY;
        }
    }

    private Mode mode = Mode.THIRD_PARTY;
    private boolean legacyServerFallback = false;
    private boolean requireManualShareConfirm = true;

    private String baseUrl = "";
    private String apiToken = "";
    private int requestTimeoutMs = 5000;
    private int syncIntervalMs = 30000;
    private boolean uploadSchematicFile = true;
    private String projectIdentityMode = "placement_hash";
    private String materialKeyMode = "item_nbt";
    private boolean offlineQueueEnabled = true;

    private boolean projectListEntry = true;
    private boolean statusInPlacementList = true;
    private boolean autoRefreshProjectOnOpen = true;

    private boolean claimsEnabled = true;
    private String defaultClaimAmountMode = "smart_stack";
    private boolean allowClaimOverRemaining = false;
    private boolean showOnlyMyClaimsByDefault = false;
    private String claimColorMode = "per_material";

    private boolean hudEnabled = true;
    private String hudAggregationMode = "local_only";
    private int maxVisibleProjectTags = 2;
    private boolean hoverExpandProjects = true;
    private boolean hideCompleted = true;
    private boolean onlyMine = false;
    private String sortMode = "missing_desc";

    private boolean storageZonesEnabled = true;
    private int maxZonesPerProject = 8;
    private int activationDistance = 32;
    private int storageZoneScanIntervalMs = 1500;
    private int staleAfterMs = 300000;
    private int denyRetryCooldownMs = 30000;
    private boolean countZoneContentsForProject = true;

    private boolean inventoryAggregationEnabled = true;
    private boolean includePlayerInventory = true;
    private boolean includeShulkerContents = true;
    private boolean includeStorageZones = true;
    private boolean includeNonZoneContainers = false;
    private int recomputeIntervalMs = 5000;
    private boolean uploadOnlyAggregatedCollected = true;

    private boolean advancedStockingEnabled = false;
    private boolean advancedScanContainers = true;
    private int advancedScanIntervalMs = 1000;
    private int maxContainersPerCycle = 6;
    private boolean rayLineEnabled = true;
    private int lineMaxDistanceTenths = 45;
    private boolean showContainerPreview = true;
    private boolean highlightClaimedItems = true;
    private boolean useClaimColors = true;
    private int permissionFailCooldownMs = 60000;
    private boolean nonZoneScansAffectProjectCollected = false;

    private boolean logThirdPartyRequests = false;
    private boolean showScanOverlay = false;
    private boolean logAggregateRecompute = false;

    private ThirdPartyLocalStore store;
    private ThirdPartyApiClient apiClient;
    private ExecutorService executor;
    private final Map<String, ThirdPartyProjectRecord> projects = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> scannedZoneContents = new LinkedHashMap<>();
    private final Map<String, AdvancedContainerHit> advancedHits = new LinkedHashMap<>();
    private JsonObject scanCache = new JsonObject();
    private long lastAggregateRecomputeMs = 0L;
    private long lastStorageZoneScanMs = 0L;
    private long lastAdvancedScanMs = 0L;
    private long lastPendingFlushMs = 0L;

    @Override
    public void getDefaultConfiguration(final IServiceConfiguration configuration)
    {
        configuration.saveString("mode", Mode.THIRD_PARTY.getConfigValue());
        configuration.saveBoolean("legacyServerFallback", false);
        configuration.saveBoolean("requireManualShareConfirm", true);
        configuration.saveString("baseUrl", "");
        configuration.saveString("apiToken", "");
        configuration.saveInteger("requestTimeoutMs", 5000);
        configuration.saveInteger("syncIntervalMs", 30000);
        configuration.saveBoolean("uploadSchematicFile", true);
        configuration.saveString("projectIdentityMode", "placement_hash");
        configuration.saveString("materialKeyMode", "item_nbt");
        configuration.saveBoolean("offlineQueueEnabled", true);
        configuration.saveString("shareButtonLabel", "share");
        configuration.saveBoolean("showProjectListEntry", true);
        configuration.saveBoolean("showProjectStatusInPlacementList", true);
        configuration.saveBoolean("autoRefreshProjectOnOpen", true);
        configuration.saveBoolean("claimsEnabled", true);
        configuration.saveString("defaultClaimAmountMode", "smart_stack");
        configuration.saveBoolean("allowClaimOverRemaining", false);
        configuration.saveBoolean("showOnlyMyClaimsByDefault", false);
        configuration.saveString("claimColorMode", "per_material");
        configuration.saveBoolean("hudEnabled", true);
        configuration.saveString("aggregationMode", "local_only");
        configuration.saveInteger("maxVisibleProjectTags", 2);
        configuration.saveBoolean("hoverExpandProjects", true);
        configuration.saveBoolean("hideCompleted", true);
        configuration.saveBoolean("onlyMine", false);
        configuration.saveString("sortMode", "missing_desc");
        configuration.saveBoolean("storageZonesEnabled", true);
        configuration.saveInteger("maxZonesPerProject", 8);
        configuration.saveInteger("activationDistance", 32);
        configuration.saveInteger("storageZoneScanIntervalMs", 1500);
        configuration.saveInteger("staleAfterMs", 300000);
        configuration.saveInteger("denyRetryCooldownMs", 30000);
        configuration.saveBoolean("countZoneContentsForProject", true);
        configuration.saveBoolean("inventoryAggregationEnabled", true);
        configuration.saveBoolean("includePlayerInventory", true);
        configuration.saveBoolean("includeShulkerContents", true);
        configuration.saveBoolean("includeStorageZones", true);
        configuration.saveBoolean("includeNonZoneContainers", false);
        configuration.saveInteger("recomputeIntervalMs", 5000);
        configuration.saveBoolean("uploadOnlyAggregatedCollected", true);
        configuration.saveBoolean("advancedStockingEnabled", false);
        configuration.saveBoolean("advancedScanContainers", true);
        configuration.saveInteger("advancedScanIntervalMs", 1000);
        configuration.saveInteger("maxContainersPerCycle", 6);
        configuration.saveBoolean("rayLineEnabled", true);
        configuration.saveInteger("lineMaxDistanceTenths", 45);
        configuration.saveBoolean("showContainerPreview", true);
        configuration.saveBoolean("highlightClaimedItems", true);
        configuration.saveBoolean("useClaimColors", true);
        configuration.saveInteger("permissionFailCooldownMs", 60000);
        configuration.saveBoolean("nonZoneScansAffectProjectCollected", false);
        configuration.saveBoolean("logThirdPartyRequests", false);
        configuration.saveBoolean("showScanOverlay", false);
        configuration.saveBoolean("logAggregateRecompute", false);
    }

    @Override
    public String getConfigKey()
    {
        return "thirdParty";
    }

    @Override
    public void configure(final IServiceConfiguration configuration)
    {
        configuration.loadString("mode", value -> mode = Mode.fromConfig(value));
        configuration.loadBoolean("legacyServerFallback", value -> legacyServerFallback = value);
        configuration.loadBoolean("requireManualShareConfirm", value -> requireManualShareConfirm = value);
        configuration.loadString("baseUrl", value -> baseUrl = value);
        configuration.loadString("apiToken", value -> apiToken = value);
        configuration.loadInteger("requestTimeoutMs", value -> requestTimeoutMs = value);
        configuration.loadInteger("syncIntervalMs", value -> syncIntervalMs = value);
        configuration.loadBoolean("uploadSchematicFile", value -> uploadSchematicFile = value);
        configuration.loadString("projectIdentityMode", value -> projectIdentityMode = value);
        configuration.loadString("materialKeyMode", value -> materialKeyMode = value);
        configuration.loadBoolean("offlineQueueEnabled", value -> offlineQueueEnabled = value);
        configuration.loadBoolean("showProjectListEntry", value -> projectListEntry = value);
        configuration.loadBoolean("showProjectStatusInPlacementList", value -> statusInPlacementList = value);
        configuration.loadBoolean("autoRefreshProjectOnOpen", value -> autoRefreshProjectOnOpen = value);
        configuration.loadBoolean("claimsEnabled", value -> claimsEnabled = value);
        configuration.loadString("defaultClaimAmountMode", value -> defaultClaimAmountMode = value);
        configuration.loadBoolean("allowClaimOverRemaining", value -> allowClaimOverRemaining = value);
        configuration.loadBoolean("showOnlyMyClaimsByDefault", value -> showOnlyMyClaimsByDefault = value);
        configuration.loadString("claimColorMode", value -> claimColorMode = value);
        configuration.loadBoolean("hudEnabled", value -> hudEnabled = value);
        configuration.loadString("aggregationMode", value -> hudAggregationMode = value);
        configuration.loadInteger("maxVisibleProjectTags", value -> maxVisibleProjectTags = value);
        configuration.loadBoolean("hoverExpandProjects", value -> hoverExpandProjects = value);
        configuration.loadBoolean("hideCompleted", value -> hideCompleted = value);
        configuration.loadBoolean("onlyMine", value -> onlyMine = value);
        configuration.loadString("sortMode", value -> sortMode = value);
        configuration.loadBoolean("storageZonesEnabled", value -> storageZonesEnabled = value);
        configuration.loadInteger("maxZonesPerProject", value -> maxZonesPerProject = value);
        configuration.loadInteger("activationDistance", value -> activationDistance = value);
        configuration.loadInteger("storageZoneScanIntervalMs", value -> storageZoneScanIntervalMs = value);
        configuration.loadInteger("staleAfterMs", value -> staleAfterMs = value);
        configuration.loadInteger("denyRetryCooldownMs", value -> denyRetryCooldownMs = value);
        configuration.loadBoolean("countZoneContentsForProject", value -> countZoneContentsForProject = value);
        configuration.loadBoolean("inventoryAggregationEnabled", value -> inventoryAggregationEnabled = value);
        configuration.loadBoolean("includePlayerInventory", value -> includePlayerInventory = value);
        configuration.loadBoolean("includeShulkerContents", value -> includeShulkerContents = value);
        configuration.loadBoolean("includeStorageZones", value -> includeStorageZones = value);
        configuration.loadBoolean("includeNonZoneContainers", value -> includeNonZoneContainers = value);
        configuration.loadInteger("recomputeIntervalMs", value -> recomputeIntervalMs = value);
        configuration.loadBoolean("uploadOnlyAggregatedCollected", value -> uploadOnlyAggregatedCollected = value);
        configuration.loadBoolean("advancedStockingEnabled", value -> advancedStockingEnabled = value);
        configuration.loadBoolean("advancedScanContainers", value -> advancedScanContainers = value);
        configuration.loadInteger("advancedScanIntervalMs", value -> advancedScanIntervalMs = value);
        configuration.loadInteger("maxContainersPerCycle", value -> maxContainersPerCycle = value);
        configuration.loadBoolean("rayLineEnabled", value -> rayLineEnabled = value);
        configuration.loadInteger("lineMaxDistanceTenths", value -> lineMaxDistanceTenths = value);
        configuration.loadBoolean("showContainerPreview", value -> showContainerPreview = value);
        configuration.loadBoolean("highlightClaimedItems", value -> highlightClaimedItems = value);
        configuration.loadBoolean("useClaimColors", value -> useClaimColors = value);
        configuration.loadInteger("permissionFailCooldownMs", value -> permissionFailCooldownMs = value);
        configuration.loadBoolean("nonZoneScansAffectProjectCollected", value -> nonZoneScansAffectProjectCollected = value);
        configuration.loadBoolean("logThirdPartyRequests", value -> logThirdPartyRequests = value);
        configuration.loadBoolean("showScanOverlay", value -> showScanOverlay = value);
        configuration.loadBoolean("logAggregateRecompute", value -> logAggregateRecompute = value);
    }

    @Override
    public void startup()
    {
        if (context == null || context.isServer())
        {
            return;
        }

        store = new ThirdPartyLocalStore(context);
        store.ensureCacheFiles();
        scanCache = store.loadScanCache();
        projects.clear();
        for (final ThirdPartyProjectRecord record : store.loadProjects().values())
        {
            if (record.getPlacement() == null)
            {
                continue;
            }
            if (record.getProjectKey().isBlank())
            {
                record.setProjectKey(getLocalRecordKey(record.getPlacement()));
            }
            projects.put(record.getProjectKey(), record);
        }
        for (final ThirdPartyProjectRecord record : projects.values())
        {
            context.getSyncmaticManager().addPlacement(record.getPlacement());
        }
        apiClient = new ThirdPartyApiClient(baseUrl, apiToken, requestTimeoutMs);
        executor = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r, "Syncmatica ThirdPartySync");
            thread.setDaemon(true);
            return thread;
        });

        flushPendingOperations();
    }

    @Override
    public void shutdown()
    {
        if (store != null)
        {
            saveProjects();
        }
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isThirdPartyMode()
    {
        return mode == Mode.THIRD_PARTY;
    }

    public boolean isLegacyServerMode()
    {
        return mode == Mode.LEGACY_SERVER;
    }

    public boolean requiresManualShareConfirm()
    {
        return requireManualShareConfirm;
    }

    public boolean shouldShowProjectListEntry()
    {
        return projectListEntry;
    }

    public boolean shouldShowProjectStatusInPlacementList()
    {
        return statusInPlacementList;
    }

    public Collection<ThirdPartyProjectRecord> getProjects()
    {
        return Collections.unmodifiableCollection(projects.values());
    }

    public ThirdPartyProjectRecord getProjectForPlacement(final ServerPlacement placement)
    {
        if (placement == null)
        {
            return null;
        }
        return projects.get(getLocalRecordKey(placement));
    }

    public void sharePlacement(final SchematicPlacement schematicPlacement)
    {
        if (schematicPlacement == null)
        {
            return;
        }

        final ServerPlacement placement = LitematicManager.getInstance().syncmaticFromSchematic(schematicPlacement);
        if (placement == null)
        {
            showMessage(Message.MessageType.ERROR, "syncmatica.error.create_from_schematic", "missing placement");
            return;
        }

        sharePlacement(placement, extractMaterials(schematicPlacement));
    }

    public synchronized void sharePlacement(final ServerPlacement placement)
    {
        sharePlacement(placement, extractMaterials(placement));
    }

    private synchronized void sharePlacement(final ServerPlacement placement, final Collection<ProjectMaterial> extractedMaterials)
    {
        final String recordKey = getLocalRecordKey(placement);
        final ThirdPartyProjectRecord record = projects.computeIfAbsent(
                recordKey,
                key -> new ThirdPartyProjectRecord(localProjectId(placement), placement)
        );
        record.setProjectKey(recordKey);
        record.setPlacement(placement);
        if (extractedMaterials != null && !extractedMaterials.isEmpty())
        {
            record.getMaterials().clear();
            record.getMaterials().addAll(extractedMaterials);
        }
        else if (record.getMaterials().isEmpty())
        {
            record.getMaterials().addAll(extractMaterials(placement));
        }
        record.setUpdatedAt(now());
        record.setPendingImport(true);
        record.setStatus(ThirdPartyProjectRecord.STATUS_QUEUED);
        record.setLastSyncMessage("");

        projects.put(recordKey, record);
        context.getSyncmaticManager().addPlacement(placement);
        saveProjects();

        showMessage(Message.MessageType.SUCCESS, "syncmatica.success.third_party_project_queued", placement.getName());
        flushPendingOperations();
    }

    public synchronized void forgetProject(final ServerPlacement placement)
    {
        if (placement == null)
        {
            return;
        }
        projects.remove(getLocalRecordKey(placement));
        context.getSyncmaticManager().removePlacement(placement);
        saveProjects();
    }

    public void refreshProjects()
    {
        if (!isThirdPartyMode() || executor == null || apiClient == null || !apiClient.isConfigured() || !autoRefreshProjectOnOpen)
        {
            return;
        }

        executor.submit(() -> {
            synchronized (this)
            {
                for (final ThirdPartyProjectRecord record : projects.values())
                {
                    refreshProject(record);
                }
                saveProjects();
            }
        });
    }

    public void claimMaterial(final String projectId, final String materialKey, final int requestedAmount)
    {
        if (!claimsEnabled || projectId == null || materialKey == null || requestedAmount <= 0)
        {
            return;
        }

        executor.submit(() -> {
            synchronized (this)
            {
                final ThirdPartyProjectRecord record = getProjectByProjectId(projectId);
                if (record == null)
                {
                    return;
                }

                final int amount = Math.min(requestedAmount, getRemainingClaimable(record, materialKey));
                if (amount <= 0 && !allowClaimOverRemaining)
                {
                    refreshProject(record);
                    showMessage(Message.MessageType.WARNING, "syncmatica.error.third_party_claim_conflict", materialKey);
                    return;
                }

                final JsonObject body = new JsonObject();
                body.addProperty("materialKey", materialKey);
                body.addProperty("targetAmount", amount);
                body.addProperty("claimAmountMode", defaultClaimAmountMode);
                body.addProperty("colorMode", claimColorMode);

                try
                {
                    final JsonObject response = apiClient.upsertClaim(projectId, body);
                    record.mergeProjectDetails(response);
                }
                catch (final Exception e)
                {
                    record.setLastSyncMessage(e.getMessage());
                    refreshProject(record);
                    showMessage(Message.MessageType.WARNING, "syncmatica.error.third_party_claim_conflict", materialKey);
                }
                saveProjects();
            }
        });
    }

    public void cancelClaim(final String claimId)
    {
        if (!claimsEnabled || claimId == null || claimId.isBlank())
        {
            return;
        }

        executor.submit(() -> {
            synchronized (this)
            {
                try
                {
                    apiClient.deleteClaim(claimId);
                    for (final ThirdPartyProjectRecord record : projects.values())
                    {
                        record.removeClaim(claimId);
                    }
                }
                catch (final Exception e)
                {
                    Syncmatica.LOGGER.warn("Third-party claim cancel failed: {}", e.getLocalizedMessage());
                }
                saveProjects();
            }
        });
    }

    public List<MergedHudEntry> buildMergedHudEntries(final String currentDimension, final UUID currentPlacementId, final String playerName)
    {
        if (!hudEnabled || !"local_only".equals(hudAggregationMode))
        {
            return Collections.emptyList();
        }

        final Map<String, MergedHudEntry> merged = new HashMap<>();
        synchronized (this)
        {
            for (final ThirdPartyProjectRecord record : projects.values())
            {
                if (record.getPlacement() == null)
                {
                    continue;
                }
                if (currentDimension != null && !currentDimension.isBlank() && !currentDimension.equals(record.getPlacement().getDimension()))
                {
                    continue;
                }
                if (currentPlacementId != null && !record.getPlacementId().equals(currentPlacementId.toString()))
                {
                    continue;
                }

                for (final ProjectMaterial material : record.getMaterials())
                {
                    final boolean claimedByMe = isClaimedBy(record, material.materialKey, playerName);
                    if (onlyMine && !claimedByMe)
                    {
                        continue;
                    }

                    final MergedHudEntry entry = merged.computeIfAbsent(material.materialKey, key -> {
                        final MergedHudEntry created = new MergedHudEntry();
                        created.materialKey = material.materialKey;
                        created.itemId = material.itemId;
                        created.displayName = material.displayName;
                        return created;
                    });
                    final int collectedAmount = record.getCollected().getOrDefault(material.materialKey, material.collected);
                    entry.required += material.required;
                    entry.collected += collectedAmount;
                    entry.reserved += material.reserved;
                    entry.missing = Math.max(0, entry.required - entry.collected);
                    entry.claimedByMe |= claimedByMe;
                    entry.projectNames.add(record.getPlacement().getName());
                }
            }
        }

        return merged.values().stream()
                .filter(entry -> !hideCompleted || entry.missing > 0)
                .sorted(hudComparator())
                .toList();
    }

    public synchronized void recordCollectedAggregate(final String projectId, final Map<String, Integer> collected)
    {
        if (!inventoryAggregationEnabled || !uploadOnlyAggregatedCollected)
        {
            return;
        }

        for (final ThirdPartyProjectRecord record : projects.values())
        {
            if (record.getProjectId().equals(projectId))
            {
                if (record.getCollected().equals(collected))
                {
                    return;
                }
                record.getCollected().clear();
                record.getCollected().putAll(collected);
                record.setUpdatedAt(now());
                record.setPendingCollected(true);
                saveProjects();
                saveAggregateCache();
                flushPendingOperations();
                return;
            }
        }
    }

    public void clientTick()
    {
        if (!isThirdPartyMode() || context == null || context.isServer())
        {
            return;
        }

        final long nowMs = System.currentTimeMillis();
        final boolean shouldScanStorage = storageZonesEnabled && includeStorageZones && nowMs - lastStorageZoneScanMs >= storageZoneScanIntervalMs;
        final boolean shouldScanAdvanced = advancedStockingEnabled && advancedScanContainers && nowMs - lastAdvancedScanMs >= advancedScanIntervalMs;
        if (shouldScanStorage || shouldScanAdvanced)
        {
            scanReachableContainers(nowMs, shouldScanAdvanced);
            if (shouldScanStorage)
            {
                lastStorageZoneScanMs = nowMs;
            }
            if (shouldScanAdvanced)
            {
                lastAdvancedScanMs = nowMs;
            }
        }

        if (inventoryAggregationEnabled && nowMs - lastAggregateRecomputeMs >= recomputeIntervalMs)
        {
            recomputeLocalCollected(nowMs);
            lastAggregateRecomputeMs = nowMs;
        }

        if (nowMs - lastPendingFlushMs >= syncIntervalMs)
        {
            lastPendingFlushMs = nowMs;
            if (hasPendingOperations())
            {
                flushPendingOperations();
            }
        }
    }

    public void renderHud(final GuiGraphicsExtractor gui)
    {
        if (!hudEnabled || gui == null)
        {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null)
        {
            return;
        }

        final String dimension = mc.level.dimension().identifier().toString();
        final String playerName = mc.player.getName().getString();
        final List<MergedHudEntry> entries = buildMergedHudEntries(dimension, null, playerName);
        if (!entries.isEmpty())
        {
            final int width = 176;
            int y = 8;
            final int x = gui.guiWidth() - width - 8;
            final int rows = Math.min(10, entries.size());
            gui.fill(x - 4, y - 4, x + width, y + 12 + rows * 10, 0x66000000);
            gui.text(mc.font, "Syncmatica", x, y, 0xFFFFFFFF);
            y += 12;
            for (int i = 0; i < rows; i++)
            {
                final MergedHudEntry entry = entries.get(i);
                final String line = trimHudLine(entry.displayName + " " + entry.collected + "/" + entry.reserved + "/" + entry.required + " " + projectTags(entry), 28);
                gui.text(mc.font, line, x, y, entry.claimedByMe ? 0xFF77DD77 : 0xFFE0E0E0);
                y += 10;
            }
        }

        renderAdvancedHud(gui, mc);
    }

    public int getClaimHighlightColor(final ItemStack stack, final String playerName)
    {
        if (!advancedStockingEnabled || !highlightClaimedItems || stack == null || stack.isEmpty())
        {
            return 0;
        }

        final String key = materialKey(stack);
        synchronized (this)
        {
            for (final ThirdPartyProjectRecord record : projects.values())
            {
                for (final MaterialClaim claim : record.getClaims())
                {
                    if (claim.materialKey.equals(key) && (playerName == null || playerName.isBlank() || claim.assignee.equalsIgnoreCase(playerName)))
                    {
                        return useClaimColors ? colorForClaim(claim) : 0xFFFFD54F;
                    }
                }
            }
        }
        return 0;
    }

    private void scanReachableContainers(final long nowMs, final boolean advancedMode)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null)
        {
            return;
        }

        final Player player = mc.player;
        final String dimension = mc.level.dimension().identifier().toString();
        final int radius = Math.max(1, (int) Math.ceil(player.blockInteractionRange()));
        final BlockPos playerPos = player.blockPosition();
        int scanned = 0;
        boolean cacheChanged = false;

        if (advancedMode)
        {
            advancedHits.clear();
        }

        for (int dx = -radius; dx <= radius && scanned < maxContainersPerCycle; dx++)
        {
            for (int dy = -radius; dy <= radius && scanned < maxContainersPerCycle; dy++)
            {
                for (int dz = -radius; dz <= radius && scanned < maxContainersPerCycle; dz++)
                {
                    final BlockPos pos = playerPos.offset(dx, dy, dz);
                    final BlockEntity blockEntity = mc.level.getBlockEntity(pos);
                    final boolean inAnyZone = isInsideAnyEnabledZone(dimension, pos);
                    if (!(blockEntity instanceof Container container) || !isReachableContainer(player, pos) || (!advancedMode && !inAnyZone))
                    {
                        continue;
                    }

                    final Map<String, Integer> contents = collectContainerContents(container);
                    final String containerId = containerId(dimension, pos);
                    final String contentHash = Integer.toHexString(contents.hashCode());
                    cacheChanged |= updateScanCache(containerId, dimension, pos, contentHash, nowMs);
                    if (inAnyZone)
                    {
                        scannedZoneContents.put(containerId, contents);
                    }
                    if (advancedMode)
                    {
                        final Map<String, Integer> matches = findAdvancedMatches(contents, player.getName().getString());
                        if (!matches.isEmpty())
                        {
                            advancedHits.put(containerId, new AdvancedContainerHit(pos, matches, nowMs));
                        }
                    }
                    scanned++;
                }
            }
        }

        if (cacheChanged && store != null)
        {
            store.saveScanCache(scanCache);
        }
    }

    private void recomputeLocalCollected(final long nowMs)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null)
        {
            return;
        }

        final Map<String, Integer> playerCounts = includePlayerInventory ? countInventory(mc.player.getInventory()) : Collections.emptyMap();
        final List<ThirdPartyProjectRecord> snapshot;
        synchronized (this)
        {
            snapshot = new ArrayList<>(projects.values());
        }

        for (final ThirdPartyProjectRecord record : snapshot)
        {
            if (record.getMaterials().isEmpty())
            {
                continue;
            }

            final Map<String, Integer> collected = new LinkedHashMap<>();
            for (final ProjectMaterial material : record.getMaterials())
            {
                int amount = 0;
                if (includePlayerInventory)
                {
                    amount += playerCounts.getOrDefault(material.materialKey, 0);
                }
                if (includeStorageZones && countZoneContentsForProject)
                {
                    amount += countStorageZoneMaterial(record, material.materialKey);
                }
                if (includeNonZoneContainers && nonZoneScansAffectProjectCollected)
                {
                    amount += countAdvancedMaterial(material.materialKey);
                }
                collected.put(material.materialKey, Math.min(material.required, amount));
            }

            if (logAggregateRecompute)
            {
                Syncmatica.LOGGER.info("Third-party aggregate recomputed for {} at {}: {}", record.getProjectId(), nowMs, collected);
            }
            recordCollectedAggregate(record.getProjectId(), collected);
        }
    }

    private int countStorageZoneMaterial(final ThirdPartyProjectRecord record, final String materialKey)
    {
        if (record.getZones().isEmpty())
        {
            return 0;
        }

        int amount = 0;
        for (final Map.Entry<String, Map<String, Integer>> entry : scannedZoneContents.entrySet())
        {
            final ScannedPosition scanned = ScannedPosition.fromContainerId(entry.getKey());
            if (scanned == null || !isInsideAnyZone(record, scanned.dimension, scanned.position))
            {
                continue;
            }
            amount += entry.getValue().getOrDefault(materialKey, 0);
        }
        return amount;
    }

    private int countAdvancedMaterial(final String materialKey)
    {
        int amount = 0;
        for (final AdvancedContainerHit hit : advancedHits.values())
        {
            amount += hit.matchingMaterials.getOrDefault(materialKey, 0);
        }
        return amount;
    }

    private Map<String, Integer> findAdvancedMatches(final Map<String, Integer> contents, final String playerName)
    {
        final Map<String, Integer> matches = new LinkedHashMap<>();
        synchronized (this)
        {
            for (final ThirdPartyProjectRecord record : projects.values())
            {
                for (final ProjectMaterial material : record.getMaterials())
                {
                    if (!isClaimedBy(record, material.materialKey, playerName))
                    {
                        continue;
                    }
                    final int amount = contents.getOrDefault(material.materialKey, 0);
                    if (amount > 0)
                    {
                        matches.merge(material.materialKey, amount, Integer::sum);
                    }
                }
            }
        }
        return matches;
    }

    private Map<String, Integer> countInventory(final Inventory inventory)
    {
        if (inventory == null)
        {
            return Collections.emptyMap();
        }

        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++)
        {
            addStackCounts(counts, inventory.getItem(i), 0);
        }
        return counts;
    }

    private Map<String, Integer> collectContainerContents(final Container container)
    {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++)
        {
            addStackCounts(counts, container.getItem(i), 0);
        }
        return counts;
    }

    private void addStackCounts(final Map<String, Integer> counts, final ItemStack stack, final int depth)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        counts.merge(materialKey(stack), stack.getCount(), Integer::sum);
        if (!includeShulkerContents || depth > 3)
        {
            return;
        }

        final ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null)
        {
            return;
        }
        contents.nonEmptyItemCopyStream().forEach(nested -> addStackCounts(counts, nested, depth + 1));
    }

    private boolean isReachableContainer(final Player player, final BlockPos pos)
    {
        if (!player.isWithinBlockInteractionRange(pos, 0.0))
        {
            return false;
        }

        final Vec3 eye = player.getEyePosition();
        final Vec3 target = Vec3.atCenterOf(pos);
        final BlockHitResult hit = player.level().clip(new ClipContext(eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private boolean updateScanCache(final String containerId, final String dimension, final BlockPos pos, final String contentHash, final long nowMs)
    {
        final JsonObject containers = getScanCacheSection();
        final JsonObject entry = containers.has(containerId) && containers.get(containerId).isJsonObject()
                ? containers.getAsJsonObject(containerId)
                : new JsonObject();
        final String previousHash = entry.has("contentHash") ? entry.get("contentHash").getAsString() : "";
        if (contentHash.equals(previousHash))
        {
            return false;
        }

        entry.addProperty("containerId", containerId);
        entry.addProperty("dimension", dimension);
        entry.addProperty("x", pos.getX());
        entry.addProperty("y", pos.getY());
        entry.addProperty("z", pos.getZ());
        entry.addProperty("contentHash", contentHash);
        entry.addProperty("scanState", "accessible");
        entry.addProperty("lastAccessibleAt", Instant.ofEpochMilli(nowMs).toString());
        containers.add(containerId, entry);
        return true;
    }

    private JsonObject getScanCacheSection()
    {
        if (!scanCache.has("scanCache") || !scanCache.get("scanCache").isJsonObject())
        {
            scanCache.add("scanCache", new JsonObject());
        }
        return scanCache.getAsJsonObject("scanCache");
    }

    private boolean isInsideAnyZone(final ThirdPartyProjectRecord record, final String dimension, final BlockPos pos)
    {
        for (final StorageZone zone : record.getZones())
        {
            if (!zone.enabled || !zone.dimension.equals(dimension))
            {
                continue;
            }
            if (pos.getX() >= Math.min(zone.minX, zone.maxX) && pos.getX() <= Math.max(zone.minX, zone.maxX)
                    && pos.getY() >= Math.min(zone.minY, zone.maxY) && pos.getY() <= Math.max(zone.minY, zone.maxY)
                    && pos.getZ() >= Math.min(zone.minZ, zone.maxZ) && pos.getZ() <= Math.max(zone.minZ, zone.maxZ))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isInsideAnyEnabledZone(final String dimension, final BlockPos pos)
    {
        synchronized (this)
        {
            for (final ThirdPartyProjectRecord record : projects.values())
            {
                if (isInsideAnyZone(record, dimension, pos))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private String containerId(final String dimension, final BlockPos pos)
    {
        return dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private boolean hasPendingOperations()
    {
        synchronized (this)
        {
            for (final ThirdPartyProjectRecord record : projects.values())
            {
                if (record.isPendingImport() || record.isPendingCollected())
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderAdvancedHud(final GuiGraphicsExtractor gui, final Minecraft mc)
    {
        if (!advancedStockingEnabled || !showContainerPreview || advancedHits.isEmpty())
        {
            return;
        }

        final int centerX = gui.guiWidth() / 2;
        final int centerY = gui.guiHeight() / 2;
        int index = 0;
        for (final AdvancedContainerHit hit : new ArrayList<>(advancedHits.values()))
        {
            if (index >= Math.min(3, maxContainersPerCycle))
            {
                break;
            }

            final int labelX = centerX + 36;
            final int labelY = centerY + 22 + index * 22;
            if (rayLineEnabled)
            {
                drawLine(gui, centerX, centerY, labelX - 4, labelY + 4, 0xAA66CCFF);
            }
            final String line = "@" + hit.position.getX() + " " + hit.position.getY() + " " + hit.position.getZ() + " " + trimHudLine(hit.preview(), 26);
            gui.fill(labelX - 3, labelY - 3, labelX + Math.min(170, mc.font.width(line) + 6), labelY + 10, 0x77000000);
            gui.text(mc.font, line, labelX, labelY, 0xFF66CCFF);
            index++;
        }
    }

    private static void drawLine(final GuiGraphicsExtractor gui, final int x0, final int y0, final int x1, final int y1, final int color)
    {
        int x = x0;
        int y = y0;
        final int dx = Math.abs(x1 - x0);
        final int dy = Math.abs(y1 - y0);
        final int sx = x0 < x1 ? 1 : -1;
        final int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true)
        {
            gui.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1)
            {
                break;
            }
            final int e2 = err * 2;
            if (e2 > -dy)
            {
                err -= dy;
                x += sx;
            }
            if (e2 < dx)
            {
                err += dx;
                y += sy;
            }
        }
    }

    private String projectTags(final MergedHudEntry entry)
    {
        final int visible = Math.min(maxVisibleProjectTags, entry.projectNames.size());
        final String tags = String.join(",", entry.projectNames.subList(0, visible));
        final int hidden = entry.projectNames.size() - visible;
        return hidden > 0 ? "[" + tags + ",+" + hidden + "]" : "[" + tags + "]";
    }

    private static String trimHudLine(final String value, final int max)
    {
        if (value == null || value.length() <= max)
        {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "...";
    }

    private int colorForClaim(final MaterialClaim claim)
    {
        if (claim.colorTag != null && !claim.colorTag.isBlank())
        {
            return 0xFF000000 | claim.colorTag.hashCode() & 0x00FFFFFF;
        }
        return 0xFF000000 | claim.materialKey.hashCode() & 0x00FFFFFF;
    }

    private void flushPendingOperations()
    {
        if (!isThirdPartyMode() || executor == null || apiClient == null || !apiClient.isConfigured())
        {
            return;
        }

        executor.submit(() -> {
            synchronized (this)
            {
                for (final ThirdPartyProjectRecord record : projects.values())
                {
                    syncRecord(record);
                }
                saveProjects();
            }
        });
    }

    private void refreshProject(final ThirdPartyProjectRecord record)
    {
        if (record.getProjectId().isBlank())
        {
            return;
        }
        try
        {
            record.mergeProjectDetails(apiClient.getProject(record.getProjectId()));
        }
        catch (final Exception e)
        {
            record.setLastSyncMessage(e.getMessage());
        }
    }

    private void syncRecord(final ThirdPartyProjectRecord record)
    {
        try
        {
            if (record.isPendingImport())
            {
                final JsonObject response = apiClient.importProject(createImportBody(record));
                record.mergeProjectDetails(response);
                if (response.has("projectId"))
                {
                    record.setProjectId(response.get("projectId").getAsString());
                }
                record.setPendingImport(false);
                record.setStatus(ThirdPartyProjectRecord.STATUS_SYNCED);
                record.setLastSyncMessage("");
                record.setUpdatedAt(now());
                if (logThirdPartyRequests)
                {
                    Syncmatica.LOGGER.info("Third-party project import synced: {}", record.getProjectId());
                }
            }

            if (record.isPendingCollected() && !record.getProjectId().isBlank())
            {
                apiClient.uploadCollected(record.getProjectId(), createCollectedBody(record));
                record.setPendingCollected(false);
                record.setStatus(ThirdPartyProjectRecord.STATUS_SYNCED);
                record.setLastSyncMessage("");
                record.setUpdatedAt(now());
            }
        }
        catch (final Exception e)
        {
            record.setStatus(offlineQueueEnabled ? ThirdPartyProjectRecord.STATUS_QUEUED : ThirdPartyProjectRecord.STATUS_FAILED);
            record.setLastSyncMessage(e.getMessage());
            if (logThirdPartyRequests)
            {
                Syncmatica.LOGGER.warn("Third-party sync failed for project '{}': {}", record.getProjectId(), e.getLocalizedMessage());
            }
        }
    }

    private JsonObject createImportBody(final ThirdPartyProjectRecord record)
    {
        final ServerPlacement placement = record.getPlacement();
        final BlockPos pos = placement.getPosition();

        final JsonObject placementJson = new JsonObject();
        placementJson.addProperty("placementId", placement.getId().toString());
        placementJson.addProperty("schematicHash", placement.getHash().toString());
        placementJson.addProperty("name", placement.getName());
        placementJson.addProperty("dimension", placement.getDimension());
        placementJson.addProperty("originX", pos.getX());
        placementJson.addProperty("originY", pos.getY());
        placementJson.addProperty("originZ", pos.getZ());
        placementJson.addProperty("rotation", placement.getRotation().name());
        placementJson.addProperty("mirror", placement.getMirror().name());
        placementJson.addProperty("owner", placement.getOwner().getName());

        final JsonObject body = new JsonObject();
        body.addProperty("identityMode", projectIdentityMode);
        body.addProperty("materialKeyMode", materialKeyMode);
        body.addProperty("uploadSchematicFile", uploadSchematicFile);
        body.add("placement", placementJson);
        body.add("materials", serializeMaterials(record.getMaterials()));
        return body;
    }

    private JsonObject createCollectedBody(final ThirdPartyProjectRecord record)
    {
        final JsonObject collected = new JsonObject();
        record.getCollected().forEach(collected::addProperty);
        return collected;
    }

    private String localProjectId(final ServerPlacement placement)
    {
        final String identity = "schematic_hash".equals(projectIdentityMode)
                ? placement.getHash().toString()
                : placement.getHash() + ":" + placement.getId();
        return "local-" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private ThirdPartyProjectRecord getProjectByProjectId(final String projectId)
    {
        for (final ThirdPartyProjectRecord record : projects.values())
        {
            if (record.getProjectId().equals(projectId))
            {
                return record;
            }
        }
        return null;
    }

    private int getRemainingClaimable(final ThirdPartyProjectRecord record, final String materialKey)
    {
        for (final ProjectMaterial material : record.getMaterials())
        {
            if (material.materialKey.equals(materialKey))
            {
                return Math.max(0, material.required - material.collected - material.reserved);
            }
        }
        return 0;
    }

    private boolean isClaimedBy(final ThirdPartyProjectRecord record, final String materialKey, final String playerName)
    {
        if (playerName == null)
        {
            return false;
        }

        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.materialKey.equals(materialKey) && claim.assignee.equalsIgnoreCase(playerName))
            {
                return true;
            }
        }
        return false;
    }

    private Comparator<MergedHudEntry> hudComparator()
    {
        return Comparator
                .comparingInt((MergedHudEntry entry) -> entry.missing).reversed()
                .thenComparing(entry -> !entry.claimedByMe)
                .thenComparing(entry -> entry.displayName);
    }

    private List<ProjectMaterial> extractMaterials(final ServerPlacement placement)
    {
        if (placement == null)
        {
            return Collections.emptyList();
        }
        return extractMaterials(LitematicManager.getInstance().schematicFromSyncmatic(placement));
    }

    private List<ProjectMaterial> extractMaterials(final SchematicPlacement placement)
    {
        if (placement == null || placement.getSchematic() == null)
        {
            return Collections.emptyList();
        }

        final Map<String, ProjectMaterial> materials = new LinkedHashMap<>();
        for (final MaterialListEntry entry : MaterialListUtils.createMaterialListFor(placement.getSchematic()))
        {
            final ItemStack stack = entry.getStack();
            if (stack == null || stack.isEmpty())
            {
                continue;
            }

            final String materialKey = materialKey(stack);
            final ProjectMaterial material = materials.computeIfAbsent(materialKey, key -> {
                final ProjectMaterial created = new ProjectMaterial();
                created.materialKey = key;
                created.itemId = itemId(stack);
                created.nbtHash = nbtHash(stack);
                created.displayName = stack.getHoverName().getString();
                return created;
            });
            material.required += entry.getCountTotal();
            material.missing = material.required;
        }
        return new ArrayList<>(materials.values());
    }

    private JsonArray serializeMaterials(final Collection<ProjectMaterial> materials)
    {
        final JsonArray result = new JsonArray();
        for (final ProjectMaterial material : materials)
        {
            result.add(material.toJson());
        }
        return result;
    }

    private String materialKey(final ItemStack stack)
    {
        final String itemId = itemId(stack);
        return "item_id".equals(materialKeyMode) ? itemId : itemId + "#" + nbtHash(stack);
    }

    private static String itemId(final ItemStack stack)
    {
        final Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : "minecraft:air";
    }

    private static String nbtHash(final ItemStack stack)
    {
        return Integer.toUnsignedString(ItemStack.hashItemAndComponents(stack), 16);
    }

    private String getLocalRecordKey(final ServerPlacement placement)
    {
        if (placement == null)
        {
            return "";
        }
        if ("schematic_hash".equals(projectIdentityMode))
        {
            return placement.getHash().toString();
        }
        return placement.getHash() + ":" + placement.getId();
    }

    private static final class AdvancedContainerHit
    {
        private final BlockPos position;
        private final Map<String, Integer> matchingMaterials;
        private final long scannedAt;

        private AdvancedContainerHit(final BlockPos position, final Map<String, Integer> matchingMaterials, final long scannedAt)
        {
            this.position = position;
            this.matchingMaterials = matchingMaterials;
            this.scannedAt = scannedAt;
        }

        private String preview()
        {
            if (matchingMaterials.isEmpty())
            {
                return "";
            }
            final Map.Entry<String, Integer> first = matchingMaterials.entrySet().iterator().next();
            return first.getKey() + " x" + first.getValue();
        }
    }

    private static final class ScannedPosition
    {
        private final String dimension;
        private final BlockPos position;

        private ScannedPosition(final String dimension, final BlockPos position)
        {
            this.dimension = dimension;
            this.position = position;
        }

        private static ScannedPosition fromContainerId(final String containerId)
        {
            if (containerId == null)
            {
                return null;
            }
            final String[] parts = containerId.split("\\|");
            if (parts.length != 2)
            {
                return null;
            }
            final String dimension = parts[0];
            final String[] coords = parts[1].split(",");
            if (coords.length != 3)
            {
                return null;
            }
            try
            {
                return new ScannedPosition(
                        dimension,
                        new BlockPos(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2]))
                );
            }
            catch (final NumberFormatException ignored)
            {
                return null;
            }
        }
    }

    private void saveProjects()
    {
        if (store != null)
        {
            store.saveProjects(projects.values());
        }
    }

    private void saveAggregateCache()
    {
        if (store == null)
        {
            return;
        }

        final JsonObject root = new JsonObject();
        final JsonObject aggregates = new JsonObject();
        for (final ThirdPartyProjectRecord record : projects.values())
        {
            final JsonObject collected = new JsonObject();
            record.getCollected().forEach(collected::addProperty);
            aggregates.add(record.getProjectId(), collected);
        }
        root.add("aggregates", aggregates);
        store.saveAggregateCache(root);
    }

    private static String now()
    {
        return Instant.now().toString();
    }

    private void showMessage(final Message.MessageType type, final String key, final Object... args)
    {
        ScreenHelper.ifPresent(helper -> helper.addMessage(type, key, args));
    }
}
