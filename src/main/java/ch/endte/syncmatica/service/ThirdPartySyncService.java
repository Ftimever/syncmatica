package ch.endte.syncmatica.service;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.Syncmatica;
import ch.endte.syncmatica.data.RedirectFileStorage;
import ch.endte.syncmatica.data.ServerPlacement;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.litematica.ScreenHelper;
import ch.endte.syncmatica.util.SyncmaticaUtil;
import ch.endte.syncmatica.thirdparty.ThirdPartyApiClient;
import ch.endte.syncmatica.thirdparty.ThirdPartyLocalStore;
import ch.endte.syncmatica.thirdparty.ThirdPartyProjectRecord;
import ch.endte.syncmatica.thirdparty.MaterialClaim;
import ch.endte.syncmatica.thirdparty.MergedHudEntry;
import ch.endte.syncmatica.thirdparty.Project;
import ch.endte.syncmatica.thirdparty.ProjectMaterial;
import ch.endte.syncmatica.thirdparty.StorageZone;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.data.ItemType;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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

    private String shareButtonLabel = "share";
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
    private JsonObject claimsCache = new JsonObject();
    private long lastAggregateRecomputeMs = 0L;
    private long lastStorageZoneScanMs = 0L;
    private long lastAdvancedScanMs = 0L;
    private long lastPendingFlushMs = 0L;
    private String connectedNotificationBaseUrl = "";

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
        configuration.loadString("shareButtonLabel", value -> shareButtonLabel = value);
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
        claimsCache = store.loadClaims();
        projects.clear();
        for (final ThirdPartyProjectRecord record : store.loadProjects().values())
        {
            if (record.getProjectKey().isBlank())
            {
                record.setProjectKey(getRecordKey(record));
            }
            projects.put(record.getProjectKey(), record);
        }
        for (final ThirdPartyProjectRecord record : projects.values())
        {
            if (record.getPlacement() != null)
            {
                context.getSyncmaticManager().addPlacement(record.getPlacement());
            }
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

    public String getShareButtonLabel()
    {
        return shareButtonLabel;
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
        final ThirdPartyProjectRecord local = projects.get(getLocalRecordKey(placement));
        if (local != null)
        {
            return local;
        }
        for (final ThirdPartyProjectRecord record : projects.values())
        {
            if (placement.getId().toString().equals(record.getPlacementId())
                    || placement.getHash().toString().equals(record.getSchematicHash()))
            {
                return record;
            }
        }
        return null;
    }

    public boolean isProjectDownloaded(final ThirdPartyProjectRecord record)
    {
        if (record == null || record.getPlacement() == null)
        {
            return false;
        }
        return isPlacementDownloaded(record.getPlacement());
    }

    public boolean isPlacementDownloaded(final ServerPlacement placement)
    {
        if (placement == null)
        {
            return false;
        }
        if (isSyncCacheReady(placement))
        {
            return true;
        }
        if (context.getFileStorage().getLocalState(placement).isLocalFileReady())
        {
            return ensureSyncCache(placement);
        }
        return false;
    }

    private boolean isParticipating(final ThirdPartyProjectRecord record)
    {
        return isProjectDownloaded(record);
    }

    private Path syncCacheDirectory()
    {
        return context.getLitematicFolder().resolve("sync").normalize();
    }

    private Path syncCachePath(final ServerPlacement placement)
    {
        return syncCacheDirectory().resolve(placement.getHash().toString() + ".litematic").normalize();
    }

    private boolean isSyncCacheReady(final ServerPlacement placement)
    {
        if (placement == null)
        {
            return false;
        }

        final Path cached = syncCachePath(placement);
        if (!Files.isRegularFile(cached))
        {
            return false;
        }

        try (InputStream input = Files.newInputStream(cached))
        {
            if (placement.getHash().equals(SyncmaticaUtil.createChecksum(input)))
            {
                addSyncCacheRedirect(cached);
                return true;
            }
        }
        catch (final Exception e)
        {
            Syncmatica.LOGGER.warn("Failed to validate third-party sync cache '{}': {}", cached, e.getLocalizedMessage());
        }
        return false;
    }

    private void addSyncCacheRedirect(final Path cached)
    {
        if (context.getFileStorage() instanceof RedirectFileStorage redirect)
        {
            redirect.addRedirect(cached);
        }
    }

    private boolean ensureSyncCache(final ServerPlacement placement)
    {
        if (placement == null)
        {
            return false;
        }
        if (isSyncCacheReady(placement))
        {
            return true;
        }

        Path source = placement.getFile();
        if (source == null || !Files.isRegularFile(source))
        {
            source = context.getFileStorage().getLocalLitematic(placement);
        }
        if (source == null || !Files.isRegularFile(source))
        {
            return false;
        }

        try
        {
            Files.createDirectories(syncCacheDirectory());
            final Path target = syncCachePath(placement);
            if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize()))
            {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            addSyncCacheRedirect(target);
            return isSyncCacheReady(placement);
        }
        catch (final Exception e)
        {
            Syncmatica.LOGGER.warn("Failed to create third-party sync cache for '{}': {}", placement.getName(), e.getLocalizedMessage());
            return false;
        }
    }

    private void writeSyncCache(final ServerPlacement placement, final byte[] data) throws java.io.IOException
    {
        Files.createDirectories(syncCacheDirectory());
        final Path target = syncCachePath(placement);
        Files.write(target, data);
        addSyncCacheRedirect(target);
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
        ensureSyncCache(placement);
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
                try
                {
                    mergeProjectList(apiClient.listProjects());
                    notifyThirdPartyConnectionSucceeded();
                }
                catch (final Exception e)
                {
                    Syncmatica.LOGGER.warn("Third-party project list refresh failed: {}", e.getLocalizedMessage());
                }
                for (final ThirdPartyProjectRecord record : projects.values())
                {
                    if (isParticipating(record))
                    {
                        refreshProject(record);
                    }
                }
                saveProjects();
            }
        });
    }

    public void refreshProjectDetails(final ThirdPartyProjectRecord record)
    {
        if (!isThirdPartyMode() || record == null || executor == null || apiClient == null || !apiClient.isConfigured())
        {
            return;
        }

        executor.submit(() -> {
            synchronized (this)
            {
                if (!isParticipating(record))
                {
                    showMessage(Message.MessageType.WARNING, "syncmatica.error.third_party_project_not_downloaded");
                    return;
                }
                refreshProject(record);
                saveProjects();
            }
        });
    }

    public void downloadProjectSchematic(final ThirdPartyProjectRecord record)
    {
        if (!isThirdPartyMode() || record == null || record.getProjectId().isBlank()
                || executor == null || apiClient == null || !apiClient.isConfigured())
        {
            return;
        }

        executor.submit(() -> {
            synchronized (this)
            {
                try
                {
                    final ServerPlacement placement = record.getPlacement();
                    if (placement != null && isSyncCacheReady(placement))
                    {
                        context.getSyncmaticManager().addPlacement(placement);
                        saveProjects();
                        showMessage(Message.MessageType.SUCCESS, "syncmatica.success.third_party_schematic_downloaded", placement.getName());
                        LitematicManager.getInstance().renderSyncmatic(placement);
                        return;
                    }

                    final JsonObject response = apiClient.downloadSchematic(record.getProjectId());
                    notifyThirdPartyConnectionSucceeded();
                    mergeServerPlacement(record, response);

                    final ServerPlacement downloadedPlacement = record.getPlacement();
                    if (downloadedPlacement == null)
                    {
                        showMessage(Message.MessageType.ERROR, "syncmatica.error.third_party_schematic_download_failed", "missing placement");
                        return;
                    }

                    if (isSyncCacheReady(downloadedPlacement))
                    {
                        refreshProject(record);
                        saveProjects();
                        showMessage(Message.MessageType.SUCCESS, "syncmatica.success.third_party_schematic_downloaded", downloadedPlacement.getName());
                        LitematicManager.getInstance().renderSyncmatic(downloadedPlacement);
                        return;
                    }

                    final String encoded = readString(response, "data");
                    if (encoded.isBlank())
                    {
                        showMessage(Message.MessageType.ERROR, "syncmatica.error.third_party_schematic_download_failed", "empty file");
                        return;
                    }

                    writeSyncCache(downloadedPlacement, Base64.getDecoder().decode(encoded));

                    if (!isSyncCacheReady(downloadedPlacement))
                    {
                        showMessage(Message.MessageType.ERROR, "syncmatica.error.third_party_schematic_download_failed", "hash mismatch");
                        return;
                    }

                    context.getSyncmaticManager().addPlacement(downloadedPlacement);
                    refreshProject(record);
                    saveProjects();
                    showMessage(Message.MessageType.SUCCESS, "syncmatica.success.third_party_schematic_downloaded", downloadedPlacement.getName());
                    LitematicManager.getInstance().renderSyncmatic(downloadedPlacement);
                }
                catch (final Exception e)
                {
                    record.setLastSyncMessage(e.getMessage());
                    showMessage(Message.MessageType.ERROR, "syncmatica.error.third_party_schematic_download_failed", e.getMessage());
                    saveProjects();
                }
            }
        });
    }

    public void recomputeLocalCollectedNow()
    {
        if (!isThirdPartyMode() || !inventoryAggregationEnabled)
        {
            return;
        }

        final long nowMs = System.currentTimeMillis();
        recomputeLocalCollected(nowMs);
        lastAggregateRecomputeMs = nowMs;
    }

    public void claimMaterial(final String projectId, final String materialKey, final int requestedAmount)
    {
        if (!claimsEnabled || projectId == null || materialKey == null)
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
                if (!isParticipating(record))
                {
                    showMessage(Message.MessageType.WARNING, "syncmatica.error.third_party_project_not_downloaded");
                    return;
                }

                final List<MaterialClaim> previousClaims = new ArrayList<>(record.getClaims());
                final List<MaterialClaim> existingClaims = findClaims(record, materialKey, currentPlayerName());
                final int existingAmount = existingClaims.stream().mapToInt(claim -> claim.targetAmount).sum();
                final int amount = resolveClaimAmount(record, materialKey, requestedAmount, existingAmount);
                if (amount <= 0 && !allowClaimOverRemaining)
                {
                    for (final MaterialClaim claim : existingClaims)
                    {
                        record.removeClaim(claim.claimId);
                        try
                        {
                            apiClient.deleteClaim(claim.claimId);
                            notifyThirdPartyConnectionSucceeded();
                        }
                        catch (final Exception e)
                        {
                            record.getClaims().clear();
                            record.getClaims().addAll(previousClaims);
                            record.setLastSyncMessage(e.getMessage());
                            showMessage(Message.MessageType.WARNING, "syncmatica.error.third_party_claim_conflict", materialKey);
                            saveProjects();
                            return;
                        }
                    }
                    saveProjects();
                    return;
                }

                record.getClaims().removeIf(claim -> claim.materialKey.equals(materialKey) && claim.assignee.equalsIgnoreCase(currentPlayerName()));
                final MaterialClaim optimistic = new MaterialClaim();
                optimistic.claimId = existingClaims.isEmpty() ? "local-" + UUID.randomUUID() : existingClaims.get(0).claimId;
                optimistic.projectId = projectId;
                optimistic.materialKey = materialKey;
                optimistic.assignee = currentPlayerName();
                optimistic.targetAmount = amount;
                optimistic.fulfilledAmount = 0;
                optimistic.colorTag = materialKey;
                optimistic.updatedAt = now();
                record.getClaims().add(optimistic);

                final JsonObject body = new JsonObject();
                body.addProperty("materialKey", materialKey);
                body.addProperty("targetAmount", amount);
                body.addProperty("assignee", currentPlayerName());
                body.addProperty("claimAmountMode", defaultClaimAmountMode);
                body.addProperty("colorMode", claimColorMode);
                if (!existingClaims.isEmpty())
                {
                    body.addProperty("claimId", existingClaims.get(0).claimId);
                }

                try
                {
                    final JsonObject response = apiClient.upsertClaim(projectId, body);
                    notifyThirdPartyConnectionSucceeded();
                    record.mergeProjectDetails(response);
                }
                catch (final Exception e)
                {
                    record.getClaims().clear();
                    record.getClaims().addAll(previousClaims);
                    record.setLastSyncMessage(e.getMessage());
                    if (offlineQueueEnabled && !isConflict(e))
                    {
                        queueClaim(projectId, body);
                        record.getClaims().add(optimistic);
                    }
                    else
                    {
                        refreshProject(record);
                    }
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
                    notifyThirdPartyConnectionSucceeded();
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

    public void upsertStorageZone(final String projectId, final String dimension, final BlockPos first, final BlockPos second)
    {
        if (!storageZonesEnabled || projectId == null || projectId.isBlank() || first == null || second == null)
        {
            return;
        }

        executor.submit(() -> {
            synchronized (this)
            {
                final ThirdPartyProjectRecord record = getProjectByProjectId(projectId);
                if (record == null || !isParticipating(record) || record.getZones().size() >= maxZonesPerProject)
                {
                    if (record != null && !isParticipating(record))
                    {
                        showMessage(Message.MessageType.WARNING, "syncmatica.error.third_party_project_not_downloaded");
                    }
                    return;
                }

                final StorageZone zone = new StorageZone();
                zone.zoneId = "local-zone-" + UUID.randomUUID();
                zone.projectId = projectId;
                zone.dimension = dimension == null || dimension.isBlank() ? currentDimension() : dimension;
                zone.minX = Math.min(first.getX(), second.getX());
                zone.minY = Math.min(first.getY(), second.getY());
                zone.minZ = Math.min(first.getZ(), second.getZ());
                zone.maxX = Math.max(first.getX(), second.getX());
                zone.maxY = Math.max(first.getY(), second.getY());
                zone.maxZ = Math.max(first.getZ(), second.getZ());
                zone.enabled = true;
                record.getZones().add(zone);

                try
                {
                    if (apiClient != null && apiClient.isConfigured())
                    {
                        record.mergeProjectDetails(apiClient.upsertZone(projectId, zone.toJson()));
                        notifyThirdPartyConnectionSucceeded();
                    }
                }
                catch (final Exception e)
                {
                    record.setLastSyncMessage(e.getMessage());
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
                if (!isParticipating(record))
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
                    final int reservedAmount = reservedAmount(record, material);
                    entry.required += material.required;
                    entry.collected += collectedAmount;
                    entry.reserved += reservedAmount;
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
                if (!isParticipating(record))
                {
                    return;
                }
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
            final int rows = Math.min(10, entries.size());
            final int lineHeight = 16;
            final int margin = 2;
            final int titleHeight = 12;
            final int width = calculateHudWidth(mc, entries, rows);
            final int x = gui.guiWidth() - width - 8;
            final int y = 8;

            gui.fill(x - margin, y - margin, x + width + margin, y + titleHeight + rows * lineHeight + margin, 0xA0000000);
            gui.text(mc.font, "Syncmatica", x + 2, y + 2, 0xFFFFFFFF);

            int rowY = y + titleHeight;
            for (int i = 0; i < rows; i++)
            {
                final MergedHudEntry entry = entries.get(i);
                renderHudEntry(gui, mc, entry, x, rowY, width);
                rowY += lineHeight;
            }
        }

        renderAdvancedHud(gui, mc);
    }

    private int calculateHudWidth(final Minecraft mc, final List<MergedHudEntry> entries, final int rows)
    {
        int maxNameWidth = mc.font.width("Syncmatica");
        int maxCountWidth = 0;
        int maxTagWidth = 0;
        for (int i = 0; i < rows; i++)
        {
            final MergedHudEntry entry = entries.get(i);
            maxNameWidth = Math.max(maxNameWidth, mc.font.width(trimHudLine(entry.displayName, 26)));
            maxCountWidth = Math.max(maxCountWidth, mc.font.width(formatHudCounts(entry)));
            maxTagWidth = Math.max(maxTagWidth, mc.font.width(trimHudLine(projectTags(entry), 24)));
        }
        return Math.max(176, Math.min(320, 24 + maxNameWidth + 12 + maxCountWidth + 8 + maxTagWidth));
    }

    private void renderHudEntry(final GuiGraphicsExtractor gui, final Minecraft mc, final MergedHudEntry entry, final int x, final int y, final int width)
    {
        final ItemStack stack = stackForItemId(entry.itemId);
        final String name = trimHudLine(entry.displayName, 26);
        final String counts = formatHudCounts(entry);
        final String tags = trimHudLine(projectTags(entry), 24);
        final int tagWidth = mc.font.width(tags);
        final int countWidth = mc.font.width(counts);
        final int tagX = x + width - tagWidth - 2;
        final int countX = tagX - countWidth - 8;

        gui.fill(x, y, x + 16, y + 16, 0x20FFFFFF);
        if (!stack.isEmpty())
        {
            gui.item(stack, x, y);
        }

        gui.text(mc.font, name, x + 20, y + 4, entry.claimedByMe ? 0xFF77DD77 : 0xFFFFFFFF);
        renderColoredCounts(gui, mc, entry, countX, y + 4);
        gui.text(mc.font, tags, tagX, y + 4, 0xFFB8B8B8);
    }

    private void renderColoredCounts(final GuiGraphicsExtractor gui, final Minecraft mc, final MergedHudEntry entry, final int x, final int y)
    {
        int cursor = x;
        final String collected = String.valueOf(entry.collected);
        final String reserved = String.valueOf(entry.reserved);
        final String required = String.valueOf(entry.required);

        cursor = drawHudCountSegment(gui, mc, collected, cursor, y, collectedColor(entry));
        cursor = drawHudCountSegment(gui, mc, "/", cursor, y, 0xFFAAAAAA);
        cursor = drawHudCountSegment(gui, mc, reserved, cursor, y, entry.claimedByMe ? 0xFF77DD77 : 0xFFFFD54F);
        cursor = drawHudCountSegment(gui, mc, "/", cursor, y, 0xFFAAAAAA);
        drawHudCountSegment(gui, mc, required, cursor, y, 0xFFE0E0E0);
    }

    private int drawHudCountSegment(final GuiGraphicsExtractor gui, final Minecraft mc, final String text, final int x, final int y, final int color)
    {
        gui.text(mc.font, text, x, y, color);
        return x + mc.font.width(text);
    }

    private String formatHudCounts(final MergedHudEntry entry)
    {
        return entry.collected + "/" + entry.reserved + "/" + entry.required;
    }

    private int collectedColor(final MergedHudEntry entry)
    {
        if (entry.collected >= entry.required)
        {
            return 0xFF55FF55;
        }
        return entry.collected > 0 ? 0xFFFFD54F : 0xFFFF5555;
    }

    private ItemStack stackForItemId(final String itemId)
    {
        if (itemId == null || itemId.isBlank())
        {
            return ItemStack.EMPTY;
        }

        final Identifier id = Identifier.tryParse(itemId);
        if (id == null)
        {
            return ItemStack.EMPTY;
        }
        final Item item = BuiltInRegistries.ITEM.getValue(id);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    public int getClaimHighlightColor(final ItemStack stack, final String playerName)
    {
        if (!advancedStockingEnabled || !highlightClaimedItems || stack == null || stack.isEmpty())
        {
            return 0;
        }

        final String key = materialKey(stack);
        final String stackItemId = itemId(stack);
        synchronized (this)
        {
            for (final ThirdPartyProjectRecord record : projects.values())
            {
                if (!isParticipating(record))
                {
                    continue;
                }
                String materialKey = key;
                String itemId = stackItemId;
                for (final ProjectMaterial material : record.getMaterials())
                {
                    if (material.materialKey.equals(key) || material.itemId.equals(stackItemId))
                    {
                        materialKey = material.materialKey;
                        itemId = material.itemId;
                        break;
                    }
                }
                for (final MaterialClaim claim : record.getClaims())
                {
                    if ((claim.materialKey.equals(materialKey) || claim.materialKey.equals(itemId))
                            && (playerName == null || playerName.isBlank() || claim.assignee.equalsIgnoreCase(playerName)))
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
                    if (!advancedMode && !isWithinStorageActivationDistance(pos))
                    {
                        continue;
                    }
                    final BlockEntity blockEntity = mc.level.getBlockEntity(pos);
                    final boolean inAnyZone = isInsideAnyEnabledZone(dimension, pos);
                    if (!(blockEntity instanceof Container container) || !isReachableContainer(player, pos) || (!advancedMode && !inAnyZone))
                    {
                        continue;
                    }

                    final String containerId = containerId(dimension, pos);
                    if (isContainerInCooldown(containerId, nowMs))
                    {
                        continue;
                    }

                    final Map<String, Integer> contents;
                    try
                    {
                        contents = collectContainerContents(container);
                    }
                    catch (final Exception e)
                    {
                        recordScanFailure(containerId, dimension, pos, e.getLocalizedMessage(), nowMs);
                        cacheChanged = true;
                        continue;
                    }
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

        final Object2IntOpenHashMap<ItemType> playerCounts = includePlayerInventory ? countPlayerInventory(mc.player) : new Object2IntOpenHashMap<>();
        final List<ThirdPartyProjectRecord> snapshot;
        synchronized (this)
        {
            snapshot = new ArrayList<>(projects.values());
        }

        for (final ThirdPartyProjectRecord record : snapshot)
        {
            if (!isParticipating(record) || record.getMaterials().isEmpty())
            {
                continue;
            }

            final Map<String, Integer> collected = new LinkedHashMap<>();
            for (final ProjectMaterial material : record.getMaterials())
            {
                int amount = 0;
                if (includePlayerInventory)
                {
                    amount += countMaterialAmount(playerCounts, material);
                }
                if (includeStorageZones && countZoneContentsForProject)
                {
                    amount += countStorageZoneMaterial(record, material);
                }
                if (includeNonZoneContainers && nonZoneScansAffectProjectCollected)
                {
                    amount += countAdvancedMaterial(material);
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

    private int countStorageZoneMaterial(final ThirdPartyProjectRecord record, final ProjectMaterial material)
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
            amount += countMaterialAmount(entry.getValue(), material);
        }
        return amount;
    }

    private int countAdvancedMaterial(final ProjectMaterial material)
    {
        int amount = 0;
        for (final AdvancedContainerHit hit : advancedHits.values())
        {
            amount += countMaterialAmount(hit.matchingMaterials, material);
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
                if (!isParticipating(record))
                {
                    continue;
                }
                for (final ProjectMaterial material : record.getMaterials())
                {
                    if (!isClaimedBy(record, material.materialKey, playerName))
                    {
                        continue;
                    }
                    final int amount = countMaterialAmount(contents, material);
                    if (amount > 0)
                    {
                        matches.merge(material.materialKey, amount, Integer::sum);
                    }
                }
            }
        }
        return matches;
    }

    private Object2IntOpenHashMap<ItemType> countPlayerInventory(final Player player)
    {
        final Object2IntOpenHashMap<ItemType> counts = new Object2IntOpenHashMap<>();
        if (player == null)
        {
            return counts;
        }

        final Inventory inventory = player.getInventory();
        if (inventory == null)
        {
            return counts;
        }

        if (includeShulkerContents)
        {
            counts.putAll(MaterialListUtils.getInventoryItemCounts(inventory));
        }
        else
        {
            for (int i = 0; i < inventory.getContainerSize(); i++)
            {
                final ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty())
                {
                    counts.addTo(new ItemType(stack, true, false), stack.getCount());
                }
            }
        }

        return counts;
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

        final String key = materialKey(stack);
        final String itemId = itemId(stack);
        counts.merge(key, stack.getCount(), Integer::sum);
        if (!key.equals(itemId))
        {
            counts.merge(itemId, stack.getCount(), Integer::sum);
        }
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

    private int countMaterialAmount(final Map<String, Integer> counts, final ProjectMaterial material)
    {
        if (counts == null || material == null)
        {
            return 0;
        }
        if ("item_id".equals(materialKeyMode))
        {
            return counts.getOrDefault(material.itemId, 0);
        }

        final int exact = counts.getOrDefault(material.materialKey, 0);
        if (exact > 0)
        {
            return exact;
        }
        return counts.getOrDefault(material.itemId, 0);
    }

    private int countMaterialAmount(final Object2IntOpenHashMap<ItemType> counts, final ProjectMaterial material)
    {
        if (counts == null || material == null || material.itemId == null || material.itemId.isBlank())
        {
            return 0;
        }

        final ItemStack materialStack = stackForItemId(material.itemId);
        if (materialStack.isEmpty())
        {
            return 0;
        }

        final int exact = counts.getInt(new ItemType(materialStack, true, false));
        if (exact > 0)
        {
            return exact;
        }

        int amount = 0;
        for (final ItemType type : counts.keySet())
        {
            final ItemStack stack = type.getStack();
            if (!stack.isEmpty() && material.itemId.equals(itemId(stack)))
            {
                amount += counts.getInt(type);
            }
        }
        return amount;
    }

    private boolean isDefaultComponentMaterial(final ProjectMaterial material)
    {
        if (material == null || material.itemId == null || material.itemId.isBlank())
        {
            return false;
        }
        final ItemStack stack = stackForItemId(material.itemId);
        return !stack.isEmpty() && nbtHash(stack).equals(material.nbtHash);
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

    private boolean isContainerInCooldown(final String containerId, final long nowMs)
    {
        final JsonObject containers = getScanCacheSection();
        if (!containers.has(containerId) || !containers.get(containerId).isJsonObject())
        {
            return false;
        }

        final JsonObject entry = containers.getAsJsonObject(containerId);
        if (!"denied".equals(readString(entry, "scanState")))
        {
            return false;
        }
        final long retryAfter = entry.has("retryAfterMs") && entry.get("retryAfterMs").isJsonPrimitive()
                ? entry.get("retryAfterMs").getAsLong()
                : 0L;
        return nowMs < retryAfter;
    }

    private void recordScanFailure(final String containerId, final String dimension, final BlockPos pos, final String reason, final long nowMs)
    {
        final JsonObject containers = getScanCacheSection();
        final JsonObject entry = containers.has(containerId) && containers.get(containerId).isJsonObject()
                ? containers.getAsJsonObject(containerId)
                : new JsonObject();
        entry.addProperty("containerId", containerId);
        entry.addProperty("dimension", dimension);
        entry.addProperty("x", pos.getX());
        entry.addProperty("y", pos.getY());
        entry.addProperty("z", pos.getZ());
        entry.addProperty("scanState", "denied");
        entry.addProperty("failureReason", reason == null ? "" : reason);
        entry.addProperty("lastFailedAt", Instant.ofEpochMilli(nowMs).toString());
        entry.addProperty("retryAfterMs", nowMs + Math.max(denyRetryCooldownMs, permissionFailCooldownMs));
        containers.add(containerId, entry);
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
            if (!getClaimQueue().isEmpty())
            {
                return true;
            }
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
        if (!advancedStockingEnabled || advancedHits.isEmpty())
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
            if (showContainerPreview)
            {
                final String line = "@" + hit.position.getX() + " " + hit.position.getY() + " " + hit.position.getZ() + " " + trimHudLine(hit.preview(), 26);
                gui.fill(labelX - 3, labelY - 3, labelX + Math.min(170, mc.font.width(line) + 6), labelY + 10, 0x77000000);
                gui.text(mc.font, line, labelX, labelY, 0xFF66CCFF);
            }
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
                flushPendingClaims();
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
        if (record.getProjectId().isBlank() || !isParticipating(record))
        {
            return;
        }
        try
        {
            final JsonObject response = apiClient.getProject(record.getProjectId());
            record.mergeProjectDetails(response);
            mergeServerPlacement(record, response);
            notifyThirdPartyConnectionSucceeded();
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
                notifyThirdPartyConnectionSucceeded();
                record.mergeProjectDetails(response);
                mergeServerPlacement(record, response);
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

            if (record.isPendingCollected() && !record.getProjectId().isBlank() && isParticipating(record))
            {
                apiClient.uploadCollected(record.getProjectId(), createCollectedBody(record));
                notifyThirdPartyConnectionSucceeded();
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
        body.add("serverPlacement", placement.toJson());
        addSchematicFile(body, placement);
        body.add("materials", serializeMaterials(record.getMaterials()));
        return body;
    }

    private void addSchematicFile(final JsonObject body, final ServerPlacement placement)
    {
        if (!uploadSchematicFile)
        {
            return;
        }

        try
        {
            Path file = placement.getFile();
            if (file == null || !Files.isRegularFile(file))
            {
                file = context.getFileStorage().getLocalLitematic(placement);
            }
            if (file == null || !Files.isRegularFile(file))
            {
                return;
            }

            final JsonObject schematicFile = new JsonObject();
            schematicFile.addProperty("fileName", placement.getNormalFileName());
            schematicFile.addProperty("hash", placement.getHash().toString());
            schematicFile.addProperty("encoding", "base64");
            schematicFile.addProperty("data", Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
            body.add("schematicFile", schematicFile);
        }
        catch (final Exception e)
        {
            Syncmatica.LOGGER.warn("Failed to attach third-party schematic file '{}': {}", placement.getName(), e.getLocalizedMessage());
        }
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

    private void mergeProjectList(final JsonObject response)
    {
        if (response == null)
        {
            return;
        }

        final JsonArray arr;
        if (response.has("projects") && response.get("projects").isJsonArray())
        {
            arr = response.getAsJsonArray("projects");
        }
        else if (response.has("items") && response.get("items").isJsonArray())
        {
            arr = response.getAsJsonArray("items");
        }
        else
        {
            return;
        }

        for (final JsonElement element : arr)
        {
            if (element == null || !element.isJsonObject())
            {
                continue;
            }
            final JsonObject obj = element.getAsJsonObject();
            final Project project = Project.fromJson(obj.has("project") && obj.get("project").isJsonObject() ? obj.getAsJsonObject("project") : obj);
            if (project.projectId.isBlank())
            {
                continue;
            }

            final String key = remoteRecordKey(project.projectId);
            final ThirdPartyProjectRecord record = projects.computeIfAbsent(key, ignored -> new ThirdPartyProjectRecord(project.projectId, null));
            record.setProjectKey(key);
            record.setProject(project);
            record.setProjectId(project.projectId);
            record.setUpdatedAt(project.updatedAt);
            if (!project.status.isBlank())
            {
                record.setStatus(project.status);
            }
            mergeServerPlacement(record, obj);
            if (isParticipating(record))
            {
                record.mergeProjectDetails(obj);
            }
        }
    }

    private void mergeServerPlacement(final ThirdPartyProjectRecord record, final JsonObject response)
    {
        if (record == null || response == null)
        {
            return;
        }

        JsonObject placementJson = null;
        if (response.has("serverPlacement") && response.get("serverPlacement").isJsonObject())
        {
            placementJson = response.getAsJsonObject("serverPlacement");
        }
        else if (response.has("placementData") && response.get("placementData").isJsonObject())
        {
            placementJson = response.getAsJsonObject("placementData");
        }
        else if (response.has("placement") && response.get("placement").isJsonObject()
                && response.getAsJsonObject("placement").has("id"))
        {
            placementJson = response.getAsJsonObject("placement");
        }
        else if (response.has("id") && response.has("file_name") && response.has("hash"))
        {
            placementJson = response;
        }

        if (placementJson == null)
        {
            return;
        }

        try
        {
            final ServerPlacement placement = ServerPlacement.fromJson(placementJson, context);
            if (placement != null)
            {
                record.setPlacement(placement);
                isSyncCacheReady(placement);
                context.getSyncmaticManager().addPlacement(placement);
            }
        }
        catch (final Exception e)
        {
            Syncmatica.LOGGER.warn("Failed to merge third-party placement for project '{}': {}", record.getProjectId(), e.getLocalizedMessage());
        }
    }

    private void flushPendingClaims()
    {
        final JsonArray queue = getClaimQueue();
        if (queue.isEmpty() || apiClient == null || !apiClient.isConfigured())
        {
            return;
        }

        final JsonArray remaining = new JsonArray();
        for (final JsonElement element : queue)
        {
            if (element == null || !element.isJsonObject())
            {
                continue;
            }
            final JsonObject queued = element.getAsJsonObject();
            final String projectId = readString(queued, "projectId");
            final JsonObject body = queued.has("body") && queued.get("body").isJsonObject()
                    ? queued.getAsJsonObject("body")
                    : new JsonObject();
            final ThirdPartyProjectRecord record = getProjectByProjectId(projectId);
            if (record == null || !isParticipating(record))
            {
                remaining.add(queued);
                continue;
            }
            try
            {
                final JsonObject response = apiClient.upsertClaim(projectId, body);
                notifyThirdPartyConnectionSucceeded();
                record.mergeProjectDetails(response);
            }
            catch (final Exception e)
            {
                if (!isConflict(e))
                {
                    remaining.add(queued);
                }
                record.setLastSyncMessage(e.getMessage());
                if (isConflict(e))
                {
                    refreshProject(record);
                }
            }
        }
        claimsCache.add("claims", remaining);
        saveClaims();
    }

    private void queueClaim(final String projectId, final JsonObject body)
    {
        final JsonObject queued = new JsonObject();
        queued.addProperty("projectId", projectId);
        queued.add("body", body.deepCopy());
        queued.addProperty("queuedAt", now());
        getClaimQueue().add(queued);
        saveClaims();
    }

    private JsonArray getClaimQueue()
    {
        if (!claimsCache.has("claims") || !claimsCache.get("claims").isJsonArray())
        {
            claimsCache.add("claims", new JsonArray());
        }
        return claimsCache.getAsJsonArray("claims");
    }

    private void saveClaims()
    {
        if (store != null)
        {
            store.saveClaims(claimsCache);
        }
    }

    private int resolveClaimAmount(final ThirdPartyProjectRecord record, final String materialKey, final int requestedAmount, final int existingAmount)
    {
        if (requestedAmount > 0)
        {
            final int editableMaximum = getRemainingClaimable(record, materialKey) + Math.max(0, existingAmount);
            return allowClaimOverRemaining ? requestedAmount : Math.min(requestedAmount, editableMaximum);
        }

        final int remaining = getRemainingClaimable(record, materialKey) + Math.max(0, existingAmount);
        if ("smart_stack".equals(defaultClaimAmountMode) && remaining > 64)
        {
            return 64;
        }
        return remaining;
    }

    private boolean isWithinStorageActivationDistance(final BlockPos pos)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || pos == null)
        {
            return false;
        }
        return mc.player.blockPosition().distSqr(pos) <= (double) activationDistance * activationDistance;
    }

    private String getRecordKey(final ThirdPartyProjectRecord record)
    {
        if (record == null)
        {
            return "";
        }
        if (record.getPlacement() != null)
        {
            return getLocalRecordKey(record.getPlacement());
        }
        return remoteRecordKey(record.getProjectId());
    }

    private String remoteRecordKey(final String projectId)
    {
        return "remote:" + projectId;
    }

    private static boolean isConflict(final Exception e)
    {
        return e != null && e.getMessage() != null && e.getMessage().contains("HTTP 409");
    }

    private static String readString(final JsonObject obj, final String key)
    {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private String currentPlayerName()
    {
        final Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.getName().getString() : "";
    }

    private String currentDimension()
    {
        final Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? mc.level.dimension().identifier().toString() : "";
    }

    private int getRemainingClaimable(final ThirdPartyProjectRecord record, final String materialKey)
    {
        for (final ProjectMaterial material : record.getMaterials())
        {
            if (material.materialKey.equals(materialKey))
            {
                return Math.max(0, material.required - material.collected - reservedAmount(record, material));
            }
        }
        return 0;
    }

    private int reservedAmount(final ThirdPartyProjectRecord record, final ProjectMaterial material)
    {
        int amount = 0;
        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.materialKey.equals(material.materialKey))
            {
                amount += claim.targetAmount;
            }
        }
        return Math.max(material.reserved, amount);
    }

    private List<MaterialClaim> findClaims(final ThirdPartyProjectRecord record, final String materialKey, final String playerName)
    {
        final List<MaterialClaim> claims = new ArrayList<>();
        if (record == null || playerName == null)
        {
            return claims;
        }
        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.materialKey.equals(materialKey) && claim.assignee.equalsIgnoreCase(playerName))
            {
                claims.add(claim);
            }
        }
        return claims;
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
        if ("name".equals(sortMode))
        {
            return Comparator.comparing(entry -> entry.displayName, String.CASE_INSENSITIVE_ORDER);
        }
        if ("claimed_first".equals(sortMode))
        {
            return Comparator
                    .comparing((MergedHudEntry entry) -> !entry.claimedByMe)
                    .thenComparing(Comparator.comparingInt((MergedHudEntry entry) -> entry.missing).reversed())
                    .thenComparing(entry -> entry.displayName, String.CASE_INSENSITIVE_ORDER);
        }
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

    private void notifyThirdPartyConnectionSucceeded()
    {
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.equals(connectedNotificationBaseUrl))
        {
            return;
        }
        connectedNotificationBaseUrl = baseUrl;
        showMessage(Message.MessageType.SUCCESS, "syncmatica.success.third_party_connected", baseUrl);
    }
}
