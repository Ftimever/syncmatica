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
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

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
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    private String advancedStockingMode = "v1_scan";
    private boolean advancedScanContainers = true;
    private int advancedScanIntervalMs = 1000;
    private int advancedSafeActionIntervalMs = 1600;
    private int maxContainersPerCycle = 6;
    private boolean rayLineEnabled = true;
    private int lineMaxDistanceTenths = 45;
    private boolean showContainerPreview = true;
    private int containerPreviewKeyCode = GLFW.GLFW_KEY_LEFT_ALT;
    private boolean highlightClaimedItems = true;
    private boolean useClaimColors = true;
    private int claimHighlightDefaultColorRgb = 0xFFD54F;
    private boolean advancedAutoTakeArmed = false;
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
    private final Map<String, Map<String, Integer>> advancedContainerSnapshots = new LinkedHashMap<>();
    private final Map<String, List<ItemStack>> advancedContainerSlotSnapshots = new LinkedHashMap<>();
    private final Map<String, Long> advancedContainerOpenTimes = new LinkedHashMap<>();
    private final Map<String, AdvancedContainerHit> advancedHits = new LinkedHashMap<>();
    private JsonObject scanCache = new JsonObject();
    private JsonObject claimsCache = new JsonObject();
    private long lastAggregateRecomputeMs = 0L;
    private long lastStorageZoneScanMs = 0L;
    private long lastAdvancedScanMs = 0L;
    private long lastAdvancedOpenMs = 0L;
    private long lastPendingFlushMs = 0L;
    private int advancedScanCursorIndex = 0;
    private String connectedNotificationBaseUrl = "";
    private String pendingAdvancedOpenContainerId = "";
    private String pendingAdvancedOpenDimension = "";
    private BlockPos pendingAdvancedOpenPos;
    private long pendingAdvancedOpenStartedMs = 0L;
    private long pendingAdvancedCloseAtMs = 0L;
    private boolean pendingAdvancedAutoTakeAttempted = false;
    private String observedOpenContainerId = "";
    private String observedOpenContentHash = "";
    private String storageZonePreviewProjectId = "";
    private String storageZonePreviewDimension = "";
    private BlockPos storageZonePreviewFirstCorner;
    private BlockPos storageZonePreviewSecondCorner;

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
        configuration.saveString("advancedStockingMode", "v1_scan");
        configuration.saveBoolean("advancedScanContainers", true);
        configuration.saveInteger("advancedScanIntervalMs", 1000);
        configuration.saveInteger("advancedSafeActionIntervalMs", 1600);
        configuration.saveInteger("maxContainersPerCycle", 6);
        configuration.saveBoolean("rayLineEnabled", true);
        configuration.saveInteger("lineMaxDistanceTenths", 45);
        configuration.saveBoolean("showContainerPreview", true);
        configuration.saveInteger("containerPreviewKeyCode", GLFW.GLFW_KEY_LEFT_ALT);
        configuration.saveBoolean("highlightClaimedItems", true);
        configuration.saveBoolean("useClaimColors", true);
        configuration.saveInteger("claimHighlightDefaultColorRgb", 0xFFD54F);
        configuration.saveBoolean("advancedAutoTakeArmed", false);
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
        configuration.loadString("advancedStockingMode", value -> advancedStockingMode = normalizeAdvancedMode(value));
        configuration.loadBoolean("advancedScanContainers", value -> advancedScanContainers = value);
        configuration.loadInteger("advancedScanIntervalMs", value -> advancedScanIntervalMs = value);
        configuration.loadInteger("advancedSafeActionIntervalMs", value -> advancedSafeActionIntervalMs = value);
        configuration.loadInteger("maxContainersPerCycle", value -> maxContainersPerCycle = value);
        configuration.loadBoolean("rayLineEnabled", value -> rayLineEnabled = value);
        configuration.loadInteger("lineMaxDistanceTenths", value -> lineMaxDistanceTenths = value);
        configuration.loadBoolean("showContainerPreview", value -> showContainerPreview = value);
        configuration.loadInteger("containerPreviewKeyCode", value -> containerPreviewKeyCode = value);
        configuration.loadBoolean("highlightClaimedItems", value -> highlightClaimedItems = value);
        configuration.loadBoolean("useClaimColors", value -> useClaimColors = value);
        configuration.loadInteger("claimHighlightDefaultColorRgb", value -> claimHighlightDefaultColorRgb = clampRgb(value));
        configuration.loadBoolean("advancedAutoTakeArmed", value -> advancedAutoTakeArmed = value);
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
            record.getCollected().clear();
            record.setPendingCollected(false);
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
        if (isBlankOrUnnamed(record.getProject().name))
        {
            record.setProjectName(defaultProjectName(placement));
        }
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
        final ThirdPartyProjectRecord record = getProjectForPlacement(placement);
        forgetProject(record);
    }

    public synchronized void forgetProject(final ThirdPartyProjectRecord record)
    {
        if (record == null)
        {
            return;
        }
        final ServerPlacement placement = record.getPlacement();
        if (placement != null)
        {
            LitematicManager.getInstance().unrenderSyncmatic(placement);
            try
            {
                Files.deleteIfExists(syncCachePath(placement));
            }
            catch (final Exception e)
            {
                Syncmatica.LOGGER.warn("Failed to delete third-party sync cache for '{}': {}", placement.getName(), e.getLocalizedMessage());
            }
            context.getSyncmaticManager().removePlacement(placement);
        }
        projects.remove(getRecordKey(record));
        projects.remove(remoteRecordKey(record.getProjectId()));
        if (placement != null)
        {
            projects.remove(getLocalRecordKey(placement));
        }
        saveProjects();
        showMessage(Message.MessageType.SUCCESS, "syncmatica.success.third_party_project_removed", recordDisplayName(record));
    }

    private String recordDisplayName(final ThirdPartyProjectRecord record)
    {
        if (record == null)
        {
            return "";
        }
        if (record.getPlacement() != null)
        {
            return record.getPlacement().getName();
        }
        return record.getProject().name == null || record.getProject().name.isBlank() ? record.getProjectId() : record.getProject().name;
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
                if (amount <= 0)
                {
                    for (final MaterialClaim claim : existingClaims)
                    {
                        record.removeClaim(claim.claimId);
                        try
                        {
                            final JsonObject response = apiClient.deleteClaim(claim.claimId);
                            notifyThirdPartyConnectionSucceeded();
                            record.mergeProjectDetails(response);
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
                    for (int i = 1; i < existingClaims.size(); i++)
                    {
                        record.mergeProjectDetails(apiClient.deleteClaim(existingClaims.get(i).claimId));
                    }
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
                    final JsonObject response = apiClient.deleteClaim(claimId);
                    notifyThirdPartyConnectionSucceeded();
                    for (final ThirdPartyProjectRecord record : projects.values())
                    {
                        if (response.has("projectId") && record.getProjectId().equals(readString(response, "projectId")))
                        {
                            record.mergeProjectDetails(response);
                        }
                        else
                        {
                            record.removeClaim(claimId);
                            recomputeMaterialReserved(record);
                        }
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
                    final int collectedAmount = collectedAmount(record, material);
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

    public void renameProject(final ThirdPartyProjectRecord record, final String name)
    {
        if (!isThirdPartyMode() || record == null || executor == null)
        {
            return;
        }
        final String cleanName = cleanProjectName(name, record.getPlacement());
        if (cleanName.isBlank())
        {
            return;
        }

        executor.submit(() -> {
            synchronized (this)
            {
                final String previousName = record.getProject().name;
                record.setProjectName(cleanName);
                if (record.getPlacement() != null)
                {
                    LitematicManager.getInstance().updateRenderedName(record.getPlacement());
                }
                saveProjects();

                if (apiClient == null || !apiClient.isConfigured() || record.getProjectId().isBlank())
                {
                    return;
                }

                try
                {
                    final JsonObject body = new JsonObject();
                    body.addProperty("name", cleanName);
                    final JsonObject response = apiClient.updateProject(record.getProjectId(), body);
                    notifyThirdPartyConnectionSucceeded();
                    record.mergeProjectDetails(response);
                    mergeServerPlacement(record, response);
                    if (record.getPlacement() != null)
                    {
                        LitematicManager.getInstance().updateRenderedName(record.getPlacement());
                    }
                    saveProjects();
                    showMessage(Message.MessageType.SUCCESS, "syncmatica.success.third_party_project_renamed", cleanName);
                }
                catch (final Exception e)
                {
                    record.setProjectName(previousName);
                    record.setLastSyncMessage(e.getMessage());
                    if (record.getPlacement() != null)
                    {
                        LitematicManager.getInstance().updateRenderedName(record.getPlacement());
                    }
                    saveProjects();
                    showMessage(Message.MessageType.ERROR, "syncmatica.error.third_party_project_rename_failed", e.getMessage());
                }
            }
        });
    }

    public void clientTick()
    {
        if (!isThirdPartyMode() || context == null || context.isServer())
        {
            return;
        }

        final long nowMs = System.currentTimeMillis();
        handleAdvancedOpenResult(nowMs);
        observeManuallyOpenedContainer(nowMs);
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

    public boolean isSuppressingAdvancedContainerScreen()
    {
        return !pendingAdvancedOpenContainerId.isBlank();
    }

    public void setStorageZonePreview(final String projectId, final String dimension, final BlockPos firstCorner, final BlockPos secondCorner)
    {
        storageZonePreviewProjectId = projectId == null ? "" : projectId;
        storageZonePreviewDimension = dimension == null ? "" : dimension;
        storageZonePreviewFirstCorner = firstCorner == null ? null : firstCorner.immutable();
        storageZonePreviewSecondCorner = secondCorner == null ? null : secondCorner.immutable();
    }

    public void clearStorageZonePreview(final String projectId)
    {
        if (projectId == null || projectId.isBlank() || projectId.equals(storageZonePreviewProjectId))
        {
            storageZonePreviewProjectId = "";
            storageZonePreviewDimension = "";
            storageZonePreviewFirstCorner = null;
            storageZonePreviewSecondCorner = null;
        }
    }

    public void renderHud(final GuiGraphicsExtractor gui)
    {
        if (gui == null)
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
        if (hudEnabled)
        {
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
        }

        renderAdvancedHud(gui, mc);
        renderStorageZonePreview(gui, mc);
    }

    public void renderStorageZonePreview(final GuiGraphicsExtractor gui)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (gui != null && mc != null)
        {
            renderStorageZonePreview(gui, mc);
        }
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
                        return useClaimColors ? colorForClaim(claim) : defaultClaimHighlightColor();
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
        final int radius = advancedMode ? advancedScanRadius(player) : Math.max(1, (int) Math.ceil(player.blockInteractionRange()));
        final BlockPos playerPos = player.blockPosition();
        boolean cacheChanged = false;

        if (advancedMode)
        {
            advancedHits.entrySet().removeIf(entry -> nowMs - entry.getValue().scannedAt > Math.max(staleAfterMs, 30000));
        }

        final Set<String> seenContainers = new HashSet<>();
        if (advancedMode)
        {
            final int reachRadius = Math.max(1, (int) Math.ceil(player.blockInteractionRange()) + 1);
            cacheChanged |= scanContainerCube(mc, player, dimension, playerPos, reachRadius, 0, Integer.MAX_VALUE, false, advancedMode, true, seenContainers, nowMs).cacheChanged;
        }

        final int side = radius * 2 + 1;
        final int plane = side * side;
        final int totalPositions = plane * side;
        final int startIndex = advancedMode ? Math.floorMod(advancedScanCursorIndex, totalPositions) : 0;
        final int positionBudget = advancedMode
                ? Math.min(totalPositions, Math.max(128, Math.max(1, maxContainersPerCycle) * 512))
                : totalPositions;
        final ContainerScanBatch batch = scanContainerCube(mc, player, dimension, playerPos, radius, startIndex, positionBudget, advancedMode, advancedMode, false, seenContainers, nowMs);
        cacheChanged |= batch.cacheChanged;

        if (advancedMode)
        {
            advancedScanCursorIndex = (startIndex + Math.max(1, batch.visited)) % totalPositions;
        }

        if (cacheChanged && store != null)
        {
            store.saveScanCache(scanCache);
        }
    }

    private ContainerScanBatch scanContainerCube(final Minecraft mc, final Player player, final String dimension, final BlockPos playerPos, final int radius,
                                                final int startIndex, final int positionBudget, final boolean useCursor, final boolean advancedMode,
                                                final boolean reachablePriorityPass, final Set<String> seenContainers, final long nowMs)
    {
        final int side = radius * 2 + 1;
        final int plane = side * side;
        final int totalPositions = plane * side;
        int visited = 0;
        int scanned = 0;
        boolean cacheChanged = false;
        final int budget = Math.min(totalPositions, Math.max(0, positionBudget));

        for (; visited < budget; visited++)
        {
            if (useCursor && scanned >= maxContainersPerCycle)
            {
                break;
            }
            final int scanIndex = useCursor ? (startIndex + visited) % totalPositions : visited;
            final int dx = scanIndex % side - radius;
            final int dy = (scanIndex / side) % side - radius;
            final int dz = scanIndex / plane - radius;
            final BlockPos pos = playerPos.offset(dx, dy, dz);
            final ContainerScanResult result = processContainerAt(mc, player, dimension, pos, advancedMode, reachablePriorityPass, seenContainers, nowMs);
            if (result.processed)
            {
                scanned++;
            }
            cacheChanged |= result.cacheChanged;
        }

        return new ContainerScanBatch(visited, cacheChanged);
    }

    private ContainerScanResult processContainerAt(final Minecraft mc, final Player player, final String dimension, final BlockPos pos, final boolean advancedMode,
                                                  final boolean reachablePriorityPass, final Set<String> seenContainers, final long nowMs)
    {
        if (!advancedMode && !isWithinStorageActivationDistance(pos))
        {
            return ContainerScanResult.SKIPPED;
        }

        final BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container))
        {
            return ContainerScanResult.SKIPPED;
        }

        final boolean reachable = isReachableContainer(player, pos);
        if (reachablePriorityPass && !reachable)
        {
            return ContainerScanResult.SKIPPED;
        }

        final boolean inAnyZone = isInsideAnyEnabledZone(dimension, pos);
        if (!advancedMode && (!reachable || !inAnyZone))
        {
            return ContainerScanResult.SKIPPED;
        }

        final String containerId = containerId(mc, dimension, pos);
        if (!seenContainers.add(containerId) || isContainerInCooldown(containerId, nowMs))
        {
            return ContainerScanResult.SKIPPED;
        }

        if (advancedMode && reachable && shouldOpenContainerForSnapshot(containerId, nowMs))
        {
            tryOpenContainerForSnapshot(mc, player, dimension, pos, containerId, nowMs);
        }

        final Map<String, Integer> contents;
        final List<ItemStack> slotContents;
        try
        {
            contents = collectContainerContents(mc, pos, container);
            slotContents = collectContainerStacks(mc, pos, container);
        }
        catch (final Exception e)
        {
            recordScanFailure(containerId, dimension, pos, e.getLocalizedMessage(), nowMs);
            return ContainerScanResult.CHANGED;
        }

        boolean cacheChanged = updateScanCache(containerId, dimension, canonicalContainerPos(mc, pos), Integer.toHexString(contents.hashCode()), nowMs);
        if (inAnyZone)
        {
            scannedZoneContents.put(containerId, contents);
        }
        if (advancedMode)
        {
            final boolean hasCachedSnapshot = advancedContainerSnapshots.containsKey(containerId);
            if (!contents.isEmpty() || !hasCachedSnapshot)
            {
                advancedContainerSnapshots.put(containerId, contents);
                advancedContainerSlotSnapshots.put(containerId, slotContents);
            }
            final Map<String, Integer> advancedContents = contents.isEmpty() && hasCachedSnapshot
                    ? advancedContainerSnapshots.get(containerId)
                    : contents;
            final Map<String, Integer> matches = findAdvancedMatches(advancedContents, player.getName().getString());
            if (!matches.isEmpty())
            {
                advancedHits.put(containerId, new AdvancedContainerHit(containerId, dimension, canonicalContainerPos(mc, pos), matches, nowMs));
            }
            else
            {
                advancedHits.remove(containerId);
            }
        }

        return new ContainerScanResult(true, cacheChanged);
    }

    private boolean shouldOpenContainerForSnapshot(final String containerId, final long nowMs)
    {
        if (!advancedStockingEnabled || containerId == null || containerId.isBlank())
        {
            return false;
        }
        if (!pendingAdvancedOpenContainerId.isBlank())
        {
            return false;
        }
        final Long lastContainerOpenMs = advancedContainerOpenTimes.get(containerId);
        final boolean hasConfirmedSlotSnapshot = lastContainerOpenMs != null
                && advancedContainerSlotSnapshots.containsKey(containerId)
                && !advancedContainerSlotSnapshots.getOrDefault(containerId, Collections.emptyList()).isEmpty();
        if (hasConfirmedSlotSnapshot
                && nowMs - lastContainerOpenMs < Math.max(30000L, Math.max(advancedSafeActionIntervalMs, advancedScanIntervalMs) * 5L))
        {
            return false;
        }
        return nowMs - lastAdvancedOpenMs >= advancedOpenDelayMs();
    }

    private long advancedOpenDelayMs()
    {
        final long base = Math.max(800, advancedSafeActionIntervalMs);
        final long jitter = Math.abs(System.nanoTime() % Math.max(1, base / 3));
        if (isAdvancedV2())
        {
            return base + jitter;
        }
        if (isAdvancedV3())
        {
            return advancedAutoTakeArmed ? Math.max(250, base / 2) + jitter : base + jitter;
        }
        return base + jitter;
    }

    private void tryOpenContainerForSnapshot(final Minecraft mc, final Player player, final String dimension, final BlockPos pos, final String containerId, final long nowMs)
    {
        if (mc == null || mc.gameMode == null || mc.screen != null)
        {
            return;
        }
        try
        {
            final BlockHitResult hit = advancedOpenHitResult(mc, player, pos);
            pendingAdvancedOpenContainerId = containerId;
            pendingAdvancedOpenDimension = dimension;
            pendingAdvancedOpenPos = pos.immutable();
            pendingAdvancedOpenStartedMs = nowMs;
            lastAdvancedOpenMs = nowMs;
            advancedContainerOpenTimes.put(containerId, nowMs);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        catch (final Exception e)
        {
            advancedContainerOpenTimes.remove(containerId);
            clearPendingAdvancedOpen();
            recordScanFailure(containerId, dimension, pos, e.getLocalizedMessage(), nowMs);
        }
    }

    private BlockHitResult advancedOpenHitResult(final Minecraft mc, final Player player, final BlockPos pos)
    {
        if (mc != null && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos))
        {
            return hit;
        }

        final Vec3 center = Vec3.atCenterOf(pos);
        final Vec3 eyes = player.getEyePosition();
        final Vec3 delta = eyes.subtract(center);
        final Direction face;
        if (Math.abs(delta.x) >= Math.abs(delta.z))
        {
            face = delta.x >= 0 ? Direction.EAST : Direction.WEST;
        }
        else
        {
            face = delta.z >= 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return new BlockHitResult(center.relative(face, 0.5D), face, pos, false);
    }

    private void handleAdvancedOpenResult(final long nowMs)
    {
        if (pendingAdvancedOpenContainerId.isBlank())
        {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null)
        {
            clearPendingAdvancedOpen();
            return;
        }

        if (pendingAdvancedCloseAtMs > 0L)
        {
            if (nowMs >= pendingAdvancedCloseAtMs)
            {
                closeAdvancedContainer(mc);
                clearPendingAdvancedOpen();
            }
            return;
        }

        if (mc.player.containerMenu != null && hasExternalContainerSlots(mc.player.getInventory(), mc.player.containerMenu.slots))
        {
            final Map<String, Integer> contents = collectOpenContainerContents(mc.player.getInventory(), mc.player.containerMenu.slots);
            final List<ItemStack> slotContents = collectOpenContainerStacks(mc.player.getInventory(), mc.player.containerMenu.slots);
            cacheAdvancedContainerSnapshot(pendingAdvancedOpenContainerId, pendingAdvancedOpenDimension, pendingAdvancedOpenPos, contents, slotContents, nowMs);
            if (advancedAutoTakeArmed && (isAdvancedV2() || isAdvancedV3()) && !pendingAdvancedAutoTakeAttempted)
            {
                pendingAdvancedAutoTakeAttempted = true;
                final int moved = autoTakeClaimedItems(mc, nowMs);
                if (moved > 0)
                {
                    pendingAdvancedCloseAtMs = nowMs + advancedPostTakeCloseDelayMs();
                    return;
                }
            }
            closeAdvancedContainer(mc);
            clearPendingAdvancedOpen();
            return;
        }

        if (nowMs - pendingAdvancedOpenStartedMs > 3500L)
        {
            recordScanFailure(pendingAdvancedOpenContainerId, pendingAdvancedOpenDimension, pendingAdvancedOpenPos, "open timeout", nowMs);
            advancedContainerOpenTimes.remove(pendingAdvancedOpenContainerId);
            closeAdvancedContainer(mc);
            clearPendingAdvancedOpen();
        }
    }

    private long advancedPostTakeCloseDelayMs()
    {
        return Math.max(120L, Math.min(500L, Math.max(advancedSafeActionIntervalMs, 800) / 4L));
    }

    private void closeAdvancedContainer(final Minecraft mc)
    {
        if (mc == null || mc.player == null)
        {
            return;
        }
        try
        {
            if (mc.player.containerMenu != null && hasExternalContainerSlots(mc.player.getInventory(), mc.player.containerMenu.slots))
            {
                mc.player.closeContainer();
            }
        }
        finally
        {
            if (mc.screen instanceof AbstractContainerScreen<?>)
            {
                mc.setScreen(null);
            }
        }
    }

    private void observeManuallyOpenedContainer(final long nowMs)
    {
        if (!pendingAdvancedOpenContainerId.isBlank())
        {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || !(mc.screen instanceof AbstractContainerScreen<?>)
                || mc.player.containerMenu == null
                || !hasExternalContainerSlots(mc.player.getInventory(), mc.player.containerMenu.slots))
        {
            observedOpenContainerId = "";
            observedOpenContentHash = "";
            return;
        }

        final BlockPos pos = currentOpenContainerPos(mc);
        if (pos == null)
        {
            return;
        }

        final String dimension = mc.level.dimension().identifier().toString();
        final String containerId = containerId(mc, dimension, pos);
        final Map<String, Integer> contents = collectOpenContainerContents(mc.player.getInventory(), mc.player.containerMenu.slots);
        final List<ItemStack> slotContents = collectOpenContainerStacks(mc.player.getInventory(), mc.player.containerMenu.slots);
        final String contentHash = Integer.toHexString(contents.hashCode()) + ":" + slotContentsHash(slotContents);
        if (containerId.equals(observedOpenContainerId) && contentHash.equals(observedOpenContentHash))
        {
            return;
        }

        observedOpenContainerId = containerId;
        observedOpenContentHash = contentHash;
        cacheAdvancedContainerSnapshot(containerId, dimension, canonicalContainerPos(mc, pos), contents, slotContents, nowMs);
    }

    private BlockPos currentOpenContainerPos(final Minecraft mc)
    {
        if (mc == null || mc.level == null || mc.player == null)
        {
            return null;
        }
        if (mc.hitResult instanceof BlockHitResult hit)
        {
            final BlockPos pos = hit.getBlockPos();
            if (mc.level.getBlockEntity(pos) instanceof Container && isReachableContainer(mc.player, pos))
            {
                return pos;
            }
        }
        return nearestReachableContainerPos(mc);
    }

    private BlockPos nearestReachableContainerPos(final Minecraft mc)
    {
        if (mc == null || mc.level == null || mc.player == null)
        {
            return null;
        }
        final Player player = mc.player;
        final BlockPos playerPos = player.blockPosition();
        final int radius = Math.max(1, (int) Math.ceil(player.blockInteractionRange()) + 1);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dy = -radius; dy <= radius; dy++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    final BlockPos pos = playerPos.offset(dx, dy, dz);
                    if (!(mc.level.getBlockEntity(pos) instanceof Container) || !isReachableContainer(player, pos))
                    {
                        continue;
                    }
                    final double distance = Vec3.atCenterOf(pos).distanceToSqr(player.getEyePosition());
                    if (distance < bestDistance)
                    {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean hasExternalContainerSlots(final Inventory playerInventory, final List<Slot> slots)
    {
        if (slots == null)
        {
            return false;
        }
        for (final Slot slot : slots)
        {
            if (slot != null && slot.container != playerInventory)
            {
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> collectOpenContainerContents(final Inventory playerInventory, final List<Slot> slots)
    {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        if (slots == null)
        {
            return counts;
        }
        for (final Slot slot : slots)
        {
            if (slot == null || slot.container == playerInventory || !slot.hasItem())
            {
                continue;
            }
            addStackCounts(counts, slot.getItem(), 0);
        }
        return counts;
    }

    private List<ItemStack> collectOpenContainerStacks(final Inventory playerInventory, final List<Slot> slots)
    {
        final List<ItemStack> stacks = new ArrayList<>();
        if (slots == null)
        {
            return stacks;
        }
        for (final Slot slot : slots)
        {
            if (slot == null || slot.container == playerInventory)
            {
                continue;
            }
            stacks.add(slot.hasItem() ? slot.getItem().copy() : ItemStack.EMPTY);
        }
        return stacks;
    }

    private String slotContentsHash(final List<ItemStack> stacks)
    {
        if (stacks == null || stacks.isEmpty())
        {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        for (final ItemStack stack : stacks)
        {
            if (stack == null || stack.isEmpty())
            {
                builder.append(";");
                continue;
            }
            builder.append(itemId(stack))
                    .append('#')
                    .append(stack.getCount())
                    .append('#')
                    .append(nbtHash(stack))
                    .append(';');
        }
        return Integer.toHexString(builder.toString().hashCode());
    }

    private void cacheAdvancedContainerSnapshot(final String containerId, final String dimension, final BlockPos pos, final Map<String, Integer> contents, final long nowMs)
    {
        cacheAdvancedContainerSnapshot(containerId, dimension, pos, contents, Collections.emptyList(), nowMs);
    }

    private void cacheAdvancedContainerSnapshot(final String containerId, final String dimension, final BlockPos pos, final Map<String, Integer> contents, final List<ItemStack> slotContents, final long nowMs)
    {
        if (containerId == null || containerId.isBlank() || pos == null)
        {
            return;
        }
        advancedContainerSnapshots.put(containerId, contents);
        if (slotContents != null && !slotContents.isEmpty())
        {
            advancedContainerSlotSnapshots.put(containerId, copyStacks(slotContents));
            advancedContainerOpenTimes.put(containerId, nowMs);
        }
        if (isInsideAnyEnabledZone(dimension, pos))
        {
            scannedZoneContents.put(containerId, contents);
        }
        final Minecraft mc = Minecraft.getInstance();
        final String playerName = mc != null && mc.player != null ? mc.player.getName().getString() : "";
        final Map<String, Integer> matches = findAdvancedMatches(contents, playerName);
        if (!matches.isEmpty())
        {
            advancedHits.put(containerId, new AdvancedContainerHit(containerId, dimension, pos, matches, nowMs));
        }
        else
        {
            advancedHits.remove(containerId);
        }
        updateScanCache(containerId, dimension, pos, Integer.toHexString(contents.hashCode()), nowMs);
        if (store != null)
        {
            store.saveScanCache(scanCache);
        }
    }

    private int autoTakeClaimedItems(final Minecraft mc, final long nowMs)
    {
        if (mc == null || mc.gameMode == null || mc.player == null || mc.player.containerMenu == null)
        {
            return 0;
        }
        final int maxMoves = isAdvancedV3() ? Math.min(8, Math.max(1, maxContainersPerCycle)) : 1;
        int moved = 0;
        final List<Slot> slots = mc.player.containerMenu.slots;
        for (int slotId = 0; slotId < slots.size(); slotId++)
        {
            if (moved >= maxMoves)
            {
                break;
            }
            final Slot slot = slots.get(slotId);
            if (slot == null || slot.container == mc.player.getInventory() || !slot.hasItem())
            {
                continue;
            }
            if (!isClaimedStockingStack(slot.getItem(), mc.player.getName().getString()))
            {
                continue;
            }
            try
            {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slotId, 0, ContainerInput.QUICK_MOVE, mc.player);
                moved++;
            }
            catch (final Exception e)
            {
                recordScanFailure(pendingAdvancedOpenContainerId, pendingAdvancedOpenDimension, pendingAdvancedOpenPos, e.getLocalizedMessage(), nowMs);
                break;
            }
        }
        return moved;
    }

    private boolean isClaimedStockingStack(final ItemStack stack, final String playerName)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
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
                for (final ProjectMaterial material : record.getMaterials())
                {
                    if (!material.materialKey.equals(key) && !material.itemId.equals(stackItemId))
                    {
                        continue;
                    }
                    if (isClaimedBy(record, material.materialKey, playerName))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void clearPendingAdvancedOpen()
    {
        pendingAdvancedOpenContainerId = "";
        pendingAdvancedOpenDimension = "";
        pendingAdvancedOpenPos = null;
        pendingAdvancedOpenStartedMs = 0L;
        pendingAdvancedCloseAtMs = 0L;
        pendingAdvancedAutoTakeAttempted = false;
    }

    private int advancedScanRadius(final Player player)
    {
        final Minecraft mc = Minecraft.getInstance();
        final int renderDistance = mc != null && mc.options != null ? mc.options.renderDistance().get() : 8;
        final int renderRadius = Math.max(1, Math.min(renderDistance * 16, 96));
        if (advancedStockingEnabled)
        {
            return renderRadius;
        }
        return Math.max(1, (int) Math.ceil(player.blockInteractionRange()));
    }

    private void recomputeLocalCollected(final long nowMs)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null)
        {
            return;
        }

        final Map<String, Integer> playerCounts = includePlayerInventory ? countPlayerInventory(mc.player) : Collections.emptyMap();
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

    private Map<String, Integer> countPlayerInventory(final Player player)
    {
        if (player == null)
        {
            return Collections.emptyMap();
        }

        final Inventory inventory = player.getInventory();
        if (inventory == null)
        {
            return Collections.emptyMap();
        }

        return countInventory(inventory);
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

    private Map<String, Integer> collectContainerContents(final Minecraft mc, final BlockPos pos, final Container container)
    {
        final Map<String, Integer> counts = collectContainerContents(container);
        final Container connected = connectedChestContainer(mc, pos);
        if (connected != null && connected != container)
        {
            collectContainerContents(connected).forEach((key, amount) -> counts.merge(key, amount, Integer::sum));
        }
        return counts;
    }

    private List<ItemStack> collectContainerStacks(final Container container)
    {
        final List<ItemStack> stacks = new ArrayList<>();
        if (container == null)
        {
            return stacks;
        }
        for (int i = 0; i < container.getContainerSize(); i++)
        {
            final ItemStack stack = container.getItem(i);
            stacks.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return stacks;
    }

    private List<ItemStack> collectContainerStacks(final Minecraft mc, final BlockPos pos, final Container container)
    {
        final List<ItemStack> stacks = collectContainerStacks(container);
        final Container connected = connectedChestContainer(mc, pos);
        if (connected != null && connected != container)
        {
            stacks.addAll(collectContainerStacks(connected));
        }
        return stacks;
    }

    private Container connectedChestContainer(final Minecraft mc, final BlockPos pos)
    {
        if (mc == null || mc.level == null || pos == null)
        {
            return null;
        }
        final BlockPos connectedPos = connectedChestPos(mc, pos);
        if (connectedPos == null)
        {
            return null;
        }
        final BlockEntity blockEntity = mc.level.getBlockEntity(connectedPos);
        return blockEntity instanceof Container container ? container : null;
    }

    private Vec3 containerCenter(final Minecraft mc, final BlockPos pos)
    {
        if (pos == null)
        {
            return Vec3.ZERO;
        }
        final BlockPos connected = connectedChestPos(mc, pos);
        if (connected == null)
        {
            return Vec3.atCenterOf(pos);
        }
        return new Vec3(
                (pos.getX() + connected.getX()) / 2.0D + 0.5D,
                (pos.getY() + connected.getY()) / 2.0D + 0.5D,
                (pos.getZ() + connected.getZ()) / 2.0D + 0.5D
        );
    }

    private static List<ItemStack> copyStacks(final List<ItemStack> stacks)
    {
        final List<ItemStack> copy = new ArrayList<>();
        if (stacks == null)
        {
            return copy;
        }
        for (final ItemStack stack : stacks)
        {
            copy.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
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

    private String containerId(final Minecraft mc, final String dimension, final BlockPos pos)
    {
        return containerId(dimension, canonicalContainerPos(mc, pos));
    }

    private BlockPos canonicalContainerPos(final Minecraft mc, final BlockPos pos)
    {
        final BlockPos connected = connectedChestPos(mc, pos);
        if (connected == null)
        {
            return pos;
        }
        if (connected.getX() < pos.getX()
                || connected.getX() == pos.getX() && connected.getY() < pos.getY()
                || connected.getX() == pos.getX() && connected.getY() == pos.getY() && connected.getZ() < pos.getZ())
        {
            return connected;
        }
        return pos;
    }

    private BlockPos connectedChestPos(final Minecraft mc, final BlockPos pos)
    {
        if (mc == null || mc.level == null || pos == null)
        {
            return null;
        }
        final BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE))
        {
            return null;
        }
        final ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE)
        {
            return null;
        }
        return ChestBlock.getConnectedBlockPos(pos, state);
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
        if (!advancedStockingEnabled)
        {
            return;
        }

        final int centerX = gui.guiWidth() / 2;
        final int centerY = gui.guiHeight() / 2;
        final String dimension = mc.level == null ? "" : mc.level.dimension().identifier().toString();
        final boolean previewPressed = showContainerPreview && isPreviewKeyPressed(mc);
        final String targetedContainerId = previewPressed ? targetedContainerId(mc, centerX, centerY) : "";
        refreshAdvancedHitsFromSnapshots(dimension, System.currentTimeMillis());
        int index = 0;
        final List<AdvancedContainerHit> hits = new ArrayList<>(advancedHits.values());
        hits.sort(Comparator.comparingDouble(hit -> containerCenter(mc, hit.position).distanceToSqr(mc.player.getEyePosition())));
        for (final AdvancedContainerHit hit : hits)
        {
            if (!dimension.equals(hit.dimension))
            {
                continue;
            }

            final ScreenPoint point = screenPointForContainer(mc, hit.position, centerX, centerY);
            if (point == null)
            {
                continue;
            }
            final int maxLabelX = Math.max(4, gui.guiWidth() - 174);
            final int labelX = Math.max(4, Math.min(maxLabelX, point.x + 8));
            final int labelY = Math.max(18, Math.min(gui.guiHeight() - 34, point.y - 6 + index * 2));
            if (rayLineEnabled)
            {
                drawLine(gui, centerX, centerY, point.x, point.y, 0xAA66CCFF);
            }
            renderAdvancedHitLabel(gui, mc, hit, labelX, labelY);
            index++;
        }

        if (previewPressed)
        {
            renderContainerSnapshotPreview(gui, mc, targetedContainerId, centerX, centerY + 48);
        }

        if ((isAdvancedV2() || isAdvancedV3()) && !advancedAutoTakeArmed)
        {
            final String line = isAdvancedV3()
                    ? "V3 视距取货已锁定：先开启允许高级自动取货"
                    : "V2 安全取货已锁定：先开启允许高级自动取货";
            final int width = mc.font.width(line);
            final int x = centerX - width / 2;
            gui.fill(x - 4, centerY + 66, x + width + 4, centerY + 79, 0x99000000);
            gui.text(mc.font, line, x, centerY + 68, 0xFFFF5555);
        }
    }

    private void renderAdvancedHitLabel(final GuiGraphicsExtractor gui, final Minecraft mc, final AdvancedContainerHit hit, final int x, final int y)
    {
        final List<ContainerPreviewEntry> entries = previewEntries(hit.matchingMaterials, 3);
        final String title = "@" + hit.position.getX() + " " + hit.position.getY() + " " + hit.position.getZ();
        int width = mc.font.width(title) + 10;
        for (final ContainerPreviewEntry entry : entries)
        {
            width += 20 + mc.font.width(entry.displayName) + 6;
        }
        width = Math.min(220, Math.max(96, width));
        gui.fill(x - 3, y - 3, x + width, y + 18, 0x99000000);
        gui.text(mc.font, title, x, y + 4, 0xFF66CCFF);
        int iconX = x + mc.font.width(title) + 8;
        for (final ContainerPreviewEntry entry : entries)
        {
            gui.item(entry.stack, iconX, y);
            gui.itemDecorations(mc.font, entry.stack, iconX, y, Integer.toString(entry.amount));
            iconX += 18;
            final String name = trimHudLine(entry.displayName, 8);
            gui.text(mc.font, name, iconX, y + 4, 0xFFFFFFFF);
            iconX += mc.font.width(name) + 6;
            if (iconX > x + width - 20)
            {
                break;
            }
        }
    }

    private void renderContainerSnapshotPreview(final GuiGraphicsExtractor gui, final Minecraft mc, final String containerId, final int centerX, final int y)
    {
        final Map<String, Integer> snapshot = containerId == null || containerId.isBlank() ? null : advancedContainerSnapshots.get(containerId);
        final List<ItemStack> slotSnapshot = containerId == null || containerId.isBlank() ? Collections.emptyList() : advancedContainerSlotSnapshots.getOrDefault(containerId, Collections.emptyList());
        final List<ContainerPreviewEntry> entries = previewEntries(snapshot, 54);
        final int columns = 9;
        final int slotSize = 18;
        final int slotCount = !slotSnapshot.isEmpty() ? Math.min(54, slotSnapshot.size()) : Math.min(54, entries.size());
        final int rows = Math.max(1, (int) Math.ceil(Math.max(1, slotCount) / (double) columns));
        final int panelWidth = columns * slotSize + 12;
        final int panelHeight = rows * slotSize + 24;
        final int x = centerX - panelWidth / 2;
        gui.fill(x, y, x + panelWidth, y + panelHeight, 0xCC101010);
        gui.outline(x, y, panelWidth, panelHeight, 0xFF66CCFF);
        gui.text(mc.font, "容器快照", x + 6, y + 6, 0xFFFFFFFF);
        if (entries.isEmpty() && slotSnapshot.isEmpty())
        {
            gui.text(mc.font, "暂无该容器快照缓存", x + 58, y + 28, 0xFFFFD54F);
            return;
        }
        if (!slotSnapshot.isEmpty())
        {
            for (int i = 0; i < slotCount; i++)
            {
                final ItemStack stack = slotSnapshot.get(i);
                final int slotX = x + 6 + i % columns * slotSize;
                final int slotY = y + 20 + i / columns * slotSize;
                gui.fill(slotX, slotY, slotX + 17, slotY + 17, 0xFF303030);
                gui.outline(slotX, slotY, 17, 17, 0xFF5A5A5A);
                if (stack != null && !stack.isEmpty())
                {
                    gui.item(stack, slotX + 1, slotY + 1);
                    gui.itemDecorations(mc.font, stack, slotX + 1, slotY + 1);
                }
            }
            return;
        }
        for (int i = 0; i < entries.size(); i++)
        {
            final ContainerPreviewEntry entry = entries.get(i);
            final int slotX = x + 6 + i % columns * slotSize;
            final int slotY = y + 20 + i / columns * slotSize;
            gui.fill(slotX, slotY, slotX + 17, slotY + 17, 0xFF303030);
            gui.outline(slotX, slotY, 17, 17, 0xFF5A5A5A);
            gui.item(entry.stack, slotX + 1, slotY + 1);
            gui.itemDecorations(mc.font, entry.stack, slotX + 1, slotY + 1, Integer.toString(entry.amount));
        }
    }

    private void refreshAdvancedHitsFromSnapshots(final String dimension, final long nowMs)
    {
        if (dimension == null || dimension.isBlank())
        {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null)
        {
            return;
        }

        advancedHits.entrySet().removeIf(entry -> nowMs - entry.getValue().scannedAt > Math.max(staleAfterMs, 30000));
        final String playerName = mc.player.getName().getString();
        for (final Map.Entry<String, Map<String, Integer>> entry : advancedContainerSnapshots.entrySet())
        {
            final ScannedPosition scanned = ScannedPosition.fromContainerId(entry.getKey());
            if (scanned == null || !dimension.equals(scanned.dimension) || isContainerInCooldown(entry.getKey(), nowMs))
            {
                continue;
            }
            final Map<String, Integer> matches = findAdvancedMatches(entry.getValue(), playerName);
            if (matches.isEmpty())
            {
                advancedHits.remove(entry.getKey());
            }
            else
            {
                advancedHits.put(entry.getKey(), new AdvancedContainerHit(entry.getKey(), scanned.dimension, scanned.position, matches, nowMs));
            }
        }
    }

    private ScreenPoint screenPointForContainer(final Minecraft mc, final BlockPos pos, final int centerX, final int centerY)
    {
        if (mc == null || mc.player == null || pos == null)
        {
            return null;
        }
        return screenPointForWorldPos(mc, containerCenter(mc, pos), centerX, centerY, advancedDisplayDistance(mc.player));
    }

    private ScreenPoint screenPointForWorldPos(final Minecraft mc, final Vec3 target, final int centerX, final int centerY, final double maxDistance)
    {
        if (mc == null || mc.player == null || target == null)
        {
            return null;
        }
        final Vec3 eye = mc.player.getEyePosition();
        final Vec3 toTarget = target.subtract(eye);
        final double distance = toTarget.length();
        if (distance > maxDistance)
        {
            return null;
        }

        final Vec3 forward = mc.player.getViewVector(1.0F).normalize();
        final double depth = toTarget.dot(forward);
        if (depth <= 0.15D)
        {
            return null;
        }

        Vec3 right = forward.cross(Vec3.Y_AXIS);
        if (right.lengthSqr() < 0.0001D)
        {
            right = Vec3.X_AXIS;
        }
        right = right.normalize();
        final Vec3 up = right.cross(forward).normalize();
        final double horizontal = toTarget.dot(right) / depth;
        final double vertical = toTarget.dot(up) / depth;
        final int maxX = Math.max(20, centerX - 12);
        final int maxY = Math.max(20, centerY - 12);
        final int x = centerX + (int) Math.round(Math.max(-1.0D, Math.min(1.0D, horizontal)) * maxX);
        final int y = centerY - (int) Math.round(Math.max(-1.0D, Math.min(1.0D, vertical)) * maxY);
        return new ScreenPoint(x, y);
    }

    private double advancedDisplayDistance(final Player player)
    {
        if (player == null)
        {
            return 0.0D;
        }
        if (advancedStockingEnabled)
        {
            return advancedScanRadius(player) + 1.0D;
        }
        return Math.max(player.blockInteractionRange(), lineMaxDistanceTenths / 10.0D);
    }

    private void renderStorageZonePreview(final GuiGraphicsExtractor gui, final Minecraft mc)
    {
        if (storageZonePreviewFirstCorner == null || storageZonePreviewDimension == null || storageZonePreviewDimension.isBlank()
                || mc == null || mc.level == null || mc.player == null
                || !storageZonePreviewDimension.equals(mc.level.dimension().identifier().toString()))
        {
            return;
        }

        final int centerX = gui.guiWidth() / 2;
        final int centerY = gui.guiHeight() / 2;
        if (storageZonePreviewSecondCorner == null)
        {
            final ScreenPoint point = screenPointForWorldPos(mc, Vec3.atCenterOf(storageZonePreviewFirstCorner), centerX, centerY, 96.0D);
            if (point != null)
            {
                drawLine(gui, point.x - 5, point.y, point.x + 5, point.y, 0xCC55FF55);
                drawLine(gui, point.x, point.y - 5, point.x, point.y + 5, 0xCC55FF55);
                final String label = "备货区 A " + storageZonePreviewFirstCorner.getX() + " "
                        + storageZonePreviewFirstCorner.getY() + " " + storageZonePreviewFirstCorner.getZ();
                gui.fill(point.x + 7, point.y - 7, point.x + 11 + mc.font.width(label), point.y + 5, 0x99000000);
                gui.text(mc.font, label, point.x + 9, point.y - 5, 0xFF55FF55);
            }
            return;
        }

        final int minX = Math.min(storageZonePreviewFirstCorner.getX(), storageZonePreviewSecondCorner.getX());
        final int minY = Math.min(storageZonePreviewFirstCorner.getY(), storageZonePreviewSecondCorner.getY());
        final int minZ = Math.min(storageZonePreviewFirstCorner.getZ(), storageZonePreviewSecondCorner.getZ());
        final int maxX = Math.max(storageZonePreviewFirstCorner.getX(), storageZonePreviewSecondCorner.getX()) + 1;
        final int maxY = Math.max(storageZonePreviewFirstCorner.getY(), storageZonePreviewSecondCorner.getY()) + 1;
        final int maxZ = Math.max(storageZonePreviewFirstCorner.getZ(), storageZonePreviewSecondCorner.getZ()) + 1;
        final Vec3[] corners = {
                new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ),
                new Vec3(maxX, minY, maxZ), new Vec3(minX, minY, maxZ),
                new Vec3(minX, maxY, minZ), new Vec3(maxX, maxY, minZ),
                new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ)
        };
        final ScreenPoint[] points = new ScreenPoint[corners.length];
        for (int i = 0; i < corners.length; i++)
        {
            points[i] = screenPointForWorldPos(mc, corners[i], centerX, centerY, 128.0D);
        }
        final int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (final int[] edge : edges)
        {
            final ScreenPoint a = points[edge[0]];
            final ScreenPoint b = points[edge[1]];
            if (a != null && b != null)
            {
                drawLine(gui, a.x, a.y, b.x, b.y, 0xCC55FF55);
            }
        }
        final ScreenPoint labelPoint = screenPointForWorldPos(mc, Vec3.atCenterOf(storageZonePreviewFirstCorner), centerX, centerY, 128.0D);
        if (labelPoint != null)
        {
            final String label = "备货区预览 " + (maxX - minX) + "x" + (maxY - minY) + "x" + (maxZ - minZ);
            gui.fill(labelPoint.x + 7, labelPoint.y - 7, labelPoint.x + 11 + mc.font.width(label), labelPoint.y + 5, 0x99000000);
            gui.text(mc.font, label, labelPoint.x + 9, labelPoint.y - 5, 0xFF55FF55);
        }
    }

    private boolean isPreviewKeyPressed(final Minecraft mc)
    {
        if (mc == null || mc.getWindow() == null || containerPreviewKeyCode <= 0)
        {
            return false;
        }
        return GLFW.glfwGetKey(mc.getWindow().handle(), containerPreviewKeyCode) == GLFW.GLFW_PRESS;
    }

    private String targetedContainerId(final Minecraft mc, final int centerX, final int centerY)
    {
        if (mc == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit))
        {
            return nearestProjectedSnapshotId(mc, centerX, centerY);
        }
        final BlockPos pos = hit.getBlockPos();
        final BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        if (blockEntity instanceof Container)
        {
            final String id = containerId(mc, mc.level.dimension().identifier().toString(), pos);
            if (advancedContainerSnapshots.containsKey(id) || advancedContainerSlotSnapshots.containsKey(id))
            {
                return id;
            }
        }
        return nearestProjectedSnapshotId(mc, centerX, centerY);
    }

    private String nearestProjectedSnapshotId(final Minecraft mc, final int centerX, final int centerY)
    {
        if (mc == null || mc.level == null)
        {
            return "";
        }
        final String dimension = mc.level.dimension().identifier().toString();
        String bestId = "";
        double bestDistance = 36.0D * 36.0D;
        final Set<String> ids = new HashSet<>(advancedContainerSnapshots.keySet());
        ids.addAll(advancedContainerSlotSnapshots.keySet());
        ids.addAll(advancedHits.keySet());
        for (final String id : ids)
        {
            final ScannedPosition scanned = ScannedPosition.fromContainerId(id);
            if (scanned == null || !dimension.equals(scanned.dimension))
            {
                continue;
            }
            final ScreenPoint point = screenPointForContainer(mc, scanned.position, centerX, centerY);
            if (point == null)
            {
                continue;
            }
            final double dx = point.x - centerX;
            final double dy = point.y - centerY;
            final double distance = dx * dx + dy * dy;
            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestId = id;
            }
        }
        return bestId;
    }

    private List<ContainerPreviewEntry> previewEntries(final Map<String, Integer> counts, final int limit)
    {
        if (counts == null || counts.isEmpty())
        {
            return Collections.emptyList();
        }
        final Map<String, ContainerPreviewEntry> merged = new LinkedHashMap<>();
        counts.forEach((key, amount) -> {
            if (amount != null && amount > 0 && !isDuplicateItemIdCount(counts, key))
            {
                final String displayName = materialDisplayNameForKey(key);
                final ItemStack stack = stackForMaterialKey(key);
                merged.compute(displayName, (ignored, current) -> {
                    if (current == null)
                    {
                        return new ContainerPreviewEntry(stack, displayName, amount);
                    }
                    current.amount += amount;
                    if (current.stack.isEmpty() && !stack.isEmpty())
                    {
                        current.stack = stack;
                    }
                    return current;
                });
            }
        });
        return merged.values().stream()
                .limit(limit)
                .toList();
    }

    private String materialDisplayNameForKey(final String key)
    {
        if (key == null || key.isBlank())
        {
            return "";
        }

        final String itemId = itemIdFromMaterialKey(key);
        final ItemStack stack = stackForItemId(itemId);
        if (!stack.isEmpty())
        {
            return stack.getHoverName().getString();
        }

        synchronized (this)
        {
            for (final ThirdPartyProjectRecord record : projects.values())
            {
                for (final ProjectMaterial material : record.getMaterials())
                {
                    if (key.equals(material.materialKey) || key.equals(material.itemId) || itemId.equals(material.itemId))
                    {
                        if (material.displayName != null && !material.displayName.isBlank())
                        {
                            return material.displayName;
                        }
                    }
                }
            }
        }

        return key;
    }

    private ItemStack stackForMaterialKey(final String key)
    {
        return stackForItemId(itemIdFromMaterialKey(key));
    }

    private static boolean isDuplicateItemIdCount(final Map<String, Integer> counts, final String key)
    {
        if (key == null || key.indexOf('#') >= 0)
        {
            return false;
        }
        return counts.keySet().stream().anyMatch(other -> other.startsWith(key + "#"));
    }

    private static String itemIdFromMaterialKey(final String key)
    {
        if (key == null)
        {
            return "";
        }
        final int hashIndex = key.indexOf('#');
        return hashIndex > 0 ? key.substring(0, hashIndex) : key;
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
        return defaultClaimHighlightColor();
    }

    private int defaultClaimHighlightColor()
    {
        return 0xFF000000 | clampRgb(claimHighlightDefaultColorRgb);
    }

    private static int clampRgb(final int value)
    {
        return Math.max(0, Math.min(0xFFFFFF, value));
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
                final JsonObject response = apiClient.uploadCollected(record.getProjectId(), createCollectedBody(record));
                notifyThirdPartyConnectionSucceeded();
                record.mergeProjectDetails(response);
                mergeServerPlacement(record, response);
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
        final String projectName = cleanProjectName(record.getProject().name, placement);
        record.setProjectName(projectName);

        final JsonObject placementJson = new JsonObject();
        placementJson.addProperty("placementId", placement.getId().toString());
        placementJson.addProperty("schematicHash", placement.getHash().toString());
        placementJson.addProperty("name", projectName);
        placementJson.addProperty("dimension", placement.getDimension());
        placementJson.addProperty("originX", pos.getX());
        placementJson.addProperty("originY", pos.getY());
        placementJson.addProperty("originZ", pos.getZ());
        placementJson.addProperty("rotation", placement.getRotation().name());
        placementJson.addProperty("mirror", placement.getMirror().name());
        placementJson.addProperty("owner", placement.getOwner() != null ? placement.getOwner().getName() : "");

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
                if (!isBlankOrUnnamed(record.getProject().name))
                {
                    placement.setDisplayName(record.getProject().name);
                }
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
        if (requestedAmount >= 0)
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
                return Math.max(0, material.required - collectedAmount(record, material) - reservedAmount(record, material));
            }
        }
        return 0;
    }

    private int collectedAmount(final ThirdPartyProjectRecord record, final ProjectMaterial material)
    {
        if (record == null || material == null)
        {
            return 0;
        }
        return Math.max(0, record.getCollected().getOrDefault(material.materialKey, 0));
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

    private void recomputeMaterialReserved(final ThirdPartyProjectRecord record)
    {
        if (record == null)
        {
            return;
        }
        for (final ProjectMaterial material : record.getMaterials())
        {
            int amount = 0;
            for (final MaterialClaim claim : record.getClaims())
            {
                if (claim.materialKey.equals(material.materialKey))
                {
                    amount += claim.targetAmount;
                }
            }
            material.reserved = amount;
            material.missing = Math.max(0, material.required - collectedAmount(record, material) - material.reserved);
        }
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
            final JsonObject obj = new JsonObject();
            obj.addProperty("materialKey", material.materialKey);
            obj.addProperty("itemId", material.itemId);
            obj.addProperty("nbtHash", material.nbtHash);
            obj.addProperty("displayName", material.displayName);
            obj.addProperty("required", material.required);
            result.add(obj);
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
        private final String containerId;
        private final String dimension;
        private final BlockPos position;
        private final Map<String, Integer> matchingMaterials;
        private final long scannedAt;

        private AdvancedContainerHit(final String containerId, final String dimension, final BlockPos position, final Map<String, Integer> matchingMaterials, final long scannedAt)
        {
            this.containerId = containerId;
            this.dimension = dimension;
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

    private static final class ContainerPreviewEntry
    {
        private ItemStack stack;
        private final String displayName;
        private int amount;

        private ContainerPreviewEntry(final ItemStack stack, final String displayName, final int amount)
        {
            this.stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(Math.max(1, Math.min(64, amount)));
            this.displayName = displayName == null ? "" : displayName;
            this.amount = amount;
        }
    }

    private static final class ScreenPoint
    {
        private final int x;
        private final int y;

        private ScreenPoint(final int x, final int y)
        {
            this.x = x;
            this.y = y;
        }
    }

    private static final class ContainerScanBatch
    {
        private final int visited;
        private final boolean cacheChanged;

        private ContainerScanBatch(final int visited, final boolean cacheChanged)
        {
            this.visited = visited;
            this.cacheChanged = cacheChanged;
        }
    }

    private static final class ContainerScanResult
    {
        private static final ContainerScanResult SKIPPED = new ContainerScanResult(false, false);
        private static final ContainerScanResult CHANGED = new ContainerScanResult(true, true);

        private final boolean processed;
        private final boolean cacheChanged;

        private ContainerScanResult(final boolean processed, final boolean cacheChanged)
        {
            this.processed = processed;
            this.cacheChanged = cacheChanged;
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

    private static String normalizeAdvancedMode(final String value)
    {
        if ("v2_safe_take".equalsIgnoreCase(value))
        {
            return "v2_safe_take";
        }
        if ("v3_unrestricted_take".equalsIgnoreCase(value))
        {
            return "v3_unrestricted_take";
        }
        return "v1_scan";
    }

    private boolean isAdvancedV2()
    {
        return "v2_safe_take".equalsIgnoreCase(advancedStockingMode);
    }

    private boolean isAdvancedV3()
    {
        return "v3_unrestricted_take".equalsIgnoreCase(advancedStockingMode);
    }

    private static String cleanProjectName(final String requested, final ServerPlacement placement)
    {
        final String trimmed = requested == null ? "" : requested.trim();
        if (!isBlankOrUnnamed(trimmed))
        {
            return trimmed;
        }
        return defaultProjectName(placement);
    }

    private static String defaultProjectName(final ServerPlacement placement)
    {
        if (placement == null)
        {
            return "";
        }
        final String display = placement.getName();
        if (!isBlankOrUnnamed(display))
        {
            return display;
        }
        String fileName = placement.getCleanFileName();
        if (fileName.endsWith(".litematic"))
        {
            fileName = fileName.substring(0, fileName.length() - ".litematic".length());
        }
        return isBlankOrUnnamed(fileName) ? placement.getId().toString() : fileName;
    }

    private static boolean isBlankOrUnnamed(final String value)
    {
        if (value == null)
        {
            return true;
        }
        final String trimmed = value.trim();
        return trimmed.isBlank()
                || "?".equals(trimmed)
                || "unnamed".equalsIgnoreCase(trimmed)
                || "未命名".equals(trimmed);
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
