package ch.endte.syncmatica.config;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.Reference;
import ch.endte.syncmatica.Syncmatica;
import ch.endte.syncmatica.service.DebugService;
import ch.endte.syncmatica.service.JsonConfiguration;
import ch.endte.syncmatica.service.QuotaService;
import ch.endte.syncmatica.service.ThirdPartySyncService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigString;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SyncmaticaConfigOptions
{
    private static final List<Binding> BINDINGS = new ArrayList<>();
    private static final List<IConfigBase> OPTIONS = new ArrayList<>();
    private static boolean loading;

    public static final ConfigOptionList MODE = optionList("thirdParty", "mode", ShareMode.THIRD_PARTY);
    public static final ConfigBoolean LEGACY_SERVER_FALLBACK = bool("thirdParty", "legacyServerFallback", false);
    public static final ConfigBoolean REQUIRE_MANUAL_SHARE_CONFIRM = bool("thirdParty", "requireManualShareConfirm", true);
    public static final ConfigString BASE_URL = string("thirdParty", "baseUrl", "");
    public static final ConfigString API_TOKEN = string("thirdParty", "apiToken", "");
    public static final ConfigInteger REQUEST_TIMEOUT_MS = integer("thirdParty", "requestTimeoutMs", 5000, 100, 60000);
    public static final ConfigInteger SYNC_INTERVAL_MS = integer("thirdParty", "syncIntervalMs", 30000, 1000, 600000);
    public static final ConfigBoolean UPLOAD_SCHEMATIC_FILE = bool("thirdParty", "uploadSchematicFile", true);
    public static final ConfigOptionList PROJECT_IDENTITY_MODE = optionList("thirdParty", "projectIdentityMode", ProjectIdentityMode.PLACEMENT_HASH);
    public static final ConfigOptionList MATERIAL_KEY_MODE = optionList("thirdParty", "materialKeyMode", MaterialKeyMode.ITEM_NBT);
    public static final ConfigBoolean OFFLINE_QUEUE_ENABLED = bool("thirdParty", "offlineQueueEnabled", true);
    public static final ConfigString SHARE_BUTTON_LABEL = string("thirdParty", "shareButtonLabel", "share");
    public static final ConfigBoolean SHOW_PROJECT_LIST_ENTRY = bool("thirdParty", "showProjectListEntry", true);
    public static final ConfigBoolean SHOW_PROJECT_STATUS_IN_PLACEMENT_LIST = bool("thirdParty", "showProjectStatusInPlacementList", true);
    public static final ConfigBoolean AUTO_REFRESH_PROJECT_ON_OPEN = bool("thirdParty", "autoRefreshProjectOnOpen", true);
    public static final ConfigBoolean CLAIMS_ENABLED = bool("thirdParty", "claimsEnabled", true);
    public static final ConfigOptionList DEFAULT_CLAIM_AMOUNT_MODE = optionList("thirdParty", "defaultClaimAmountMode", ClaimAmountMode.SMART_STACK);
    public static final ConfigBoolean ALLOW_CLAIM_OVER_REMAINING = bool("thirdParty", "allowClaimOverRemaining", false);
    public static final ConfigBoolean SHOW_ONLY_MY_CLAIMS_BY_DEFAULT = bool("thirdParty", "showOnlyMyClaimsByDefault", false);
    public static final ConfigOptionList CLAIM_COLOR_MODE = optionList("thirdParty", "claimColorMode", ClaimColorMode.PER_MATERIAL);
    public static final ConfigBoolean HUD_ENABLED = bool("thirdParty", "hudEnabled", true);
    public static final ConfigOptionList AGGREGATION_MODE = optionList("thirdParty", "aggregationMode", HudAggregationMode.LOCAL_ONLY);
    public static final ConfigInteger MAX_VISIBLE_PROJECT_TAGS = integer("thirdParty", "maxVisibleProjectTags", 2, 0, 16);
    public static final ConfigBoolean HOVER_EXPAND_PROJECTS = bool("thirdParty", "hoverExpandProjects", true);
    public static final ConfigBoolean HIDE_COMPLETED = bool("thirdParty", "hideCompleted", true);
    public static final ConfigBoolean ONLY_MINE = bool("thirdParty", "onlyMine", false);
    public static final ConfigOptionList SORT_MODE = optionList("thirdParty", "sortMode", HudSortMode.MISSING_DESC);
    public static final ConfigBoolean STORAGE_ZONES_ENABLED = bool("thirdParty", "storageZonesEnabled", true);
    public static final ConfigInteger MAX_ZONES_PER_PROJECT = integer("thirdParty", "maxZonesPerProject", 8, 0, 128);
    public static final ConfigInteger ACTIVATION_DISTANCE = integer("thirdParty", "activationDistance", 32, 1, 256);
    public static final ConfigInteger STORAGE_ZONE_SCAN_INTERVAL_MS = integer("thirdParty", "storageZoneScanIntervalMs", 1500, 100, 60000);
    public static final ConfigInteger STALE_AFTER_MS = integer("thirdParty", "staleAfterMs", 300000, 1000, 3600000);
    public static final ConfigInteger DENY_RETRY_COOLDOWN_MS = integer("thirdParty", "denyRetryCooldownMs", 30000, 1000, 600000);
    public static final ConfigBoolean COUNT_ZONE_CONTENTS_FOR_PROJECT = bool("thirdParty", "countZoneContentsForProject", true);
    public static final ConfigBoolean INVENTORY_AGGREGATION_ENABLED = bool("thirdParty", "inventoryAggregationEnabled", true);
    public static final ConfigBoolean INCLUDE_PLAYER_INVENTORY = bool("thirdParty", "includePlayerInventory", true);
    public static final ConfigBoolean INCLUDE_SHULKER_CONTENTS = bool("thirdParty", "includeShulkerContents", true);
    public static final ConfigBoolean INCLUDE_STORAGE_ZONES = bool("thirdParty", "includeStorageZones", true);
    public static final ConfigBoolean INCLUDE_NON_ZONE_CONTAINERS = bool("thirdParty", "includeNonZoneContainers", false);
    public static final ConfigInteger RECOMPUTE_INTERVAL_MS = integer("thirdParty", "recomputeIntervalMs", 5000, 250, 600000);
    public static final ConfigBoolean UPLOAD_ONLY_AGGREGATED_COLLECTED = bool("thirdParty", "uploadOnlyAggregatedCollected", true);
    public static final ConfigBoolean ADVANCED_STOCKING_ENABLED = bool("thirdParty", "advancedStockingEnabled", false);
    public static final ConfigOptionList ADVANCED_STOCKING_MODE = optionList("thirdParty", "advancedStockingMode", AdvancedStockingMode.V1_SCAN);
    public static final ConfigBoolean ADVANCED_SCAN_CONTAINERS = bool("thirdParty", "advancedScanContainers", true);
    public static final ConfigInteger ADVANCED_SCAN_INTERVAL_MS = integer("thirdParty", "advancedScanIntervalMs", 1000, 100, 60000);
    public static final ConfigInteger ADVANCED_SAFE_ACTION_INTERVAL_MS = integer("thirdParty", "advancedSafeActionIntervalMs", 1600, 250, 60000);
    public static final ConfigInteger MAX_CONTAINERS_PER_CYCLE = integer("thirdParty", "maxContainersPerCycle", 6, 1, 128);
    public static final ConfigBoolean RAY_LINE_ENABLED = bool("thirdParty", "rayLineEnabled", true);
    public static final ConfigInteger LINE_MAX_DISTANCE_TENTHS = integer("thirdParty", "lineMaxDistanceTenths", 45, 1, 128);
    public static final ConfigBoolean SHOW_CONTAINER_PREVIEW = bool("thirdParty", "showContainerPreview", true);
    public static final ConfigInteger CONTAINER_PREVIEW_KEY_CODE = integer("thirdParty", "containerPreviewKeyCode", 342, 0, 512);
    public static final ConfigBoolean HIGHLIGHT_CLAIMED_ITEMS = bool("thirdParty", "highlightClaimedItems", true);
    public static final ConfigBoolean USE_CLAIM_COLORS = bool("thirdParty", "useClaimColors", true);
    public static final ConfigInteger CLAIM_HIGHLIGHT_DEFAULT_COLOR_RGB = integer("thirdParty", "claimHighlightDefaultColorRgb", 0xFFD54F, 0, 0xFFFFFF);
    public static final ConfigBoolean ADVANCED_AUTO_TAKE_ARMED = bool("thirdParty", "advancedAutoTakeArmed", false);
    public static final ConfigInteger PERMISSION_FAIL_COOLDOWN_MS = integer("thirdParty", "permissionFailCooldownMs", 60000, 1000, 600000);
    public static final ConfigBoolean NON_ZONE_SCANS_AFFECT_PROJECT_COLLECTED = bool("thirdParty", "nonZoneScansAffectProjectCollected", false);
    public static final ConfigBoolean LOG_THIRD_PARTY_REQUESTS = bool("thirdParty", "logThirdPartyRequests", false);
    public static final ConfigBoolean SHOW_SCAN_OVERLAY = bool("thirdParty", "showScanOverlay", false);
    public static final ConfigBoolean LOG_AGGREGATE_RECOMPUTE = bool("thirdParty", "logAggregateRecompute", false);
    public static final ConfigBoolean DEBUG_PACKET_LOGGING = bool("debug", "doPackageLogging", false);
    public static final ConfigBoolean QUOTA_ENABLED = bool("quota", "enabled", false);
    public static final ConfigInteger QUOTA_LIMIT = integer("quota", "limit", 40000000, 0, Integer.MAX_VALUE);

    private SyncmaticaConfigOptions()
    {
    }

    public static List<IConfigBase> options()
    {
        return OPTIONS;
    }

    public static void load(final Context context)
    {
        loading = true;
        try
        {
            final JsonObject root = readConfig(context);
            updateDisplayNames();
            for (final Binding binding : BINDINGS)
            {
                final JsonObject section = getObject(root, binding.serviceKey(), false);
                if (section != null)
                {
                    final JsonElement element = section.get(binding.configKey());
                    if (element != null)
                    {
                        binding.option().setValueFromJsonElement(element);
                    }
                }
                binding.option().markClean();
            }
        }
        finally
        {
            loading = false;
        }
    }

    public static void save(final Context context)
    {
        if (loading)
        {
            return;
        }

        try
        {
            final JsonObject root = readConfig(context);
            for (final Binding binding : BINDINGS)
            {
                getObject(root, binding.serviceKey(), true).add(binding.configKey(), binding.option().getAsJsonElement());
                binding.option().markClean();
            }

            final Path file = configFile(context);
            Files.createDirectories(file.getParent());
            try (Writer writer = new BufferedWriter(new FileWriter(file.toFile())))
            {
                writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
            }
            apply(context, root);
        }
        catch (final Exception e)
        {
            Syncmatica.LOGGER.error("Failed to save Syncmatica config GUI values: {}", e.getLocalizedMessage());
        }
    }

    private static void apply(final Context context, final JsonObject root)
    {
        if (context == null)
        {
            return;
        }

        final ThirdPartySyncService thirdParty = context.getThirdPartySyncService();
        if (thirdParty != null)
        {
            thirdParty.configure(new JsonConfiguration(getObject(root, thirdParty.getConfigKey(), true)));
        }

        final DebugService debug = context.getDebugService();
        if (debug != null)
        {
            debug.configure(new JsonConfiguration(getObject(root, debug.getConfigKey(), true)));
        }

        final QuotaService quota = context.getQuotaService();
        if (quota != null)
        {
            quota.configure(new JsonConfiguration(getObject(root, quota.getConfigKey(), true)));
        }
    }

    private static JsonObject readConfig(final Context context)
    {
        final Path file = configFile(context);
        try
        {
            if (Files.exists(file))
            {
                final JsonObject root = new Gson().fromJson(new BufferedReader(new FileReader(file.toFile())), JsonObject.class);
                return root != null ? root : new JsonObject();
            }
        }
        catch (final Exception e)
        {
            Syncmatica.LOGGER.warn("Failed to read Syncmatica config for GUI: {}", e.getLocalizedMessage());
        }
        return new JsonObject();
    }

    private static JsonObject getObject(final JsonObject root, final String key, final boolean create)
    {
        if (root.has(key) && root.get(key).isJsonObject())
        {
            return root.getAsJsonObject(key);
        }
        if (!create)
        {
            return null;
        }
        final JsonObject object = new JsonObject();
        root.add(key, object);
        return object;
    }

    private static Path configFile(final Context context)
    {
        return context != null ? context.getConfigFile() : Reference.CONFIG_ROOT.resolve(Reference.MOD_ID).resolve("config.json");
    }

    private static ConfigBoolean bool(final String serviceKey, final String key, final boolean defaultValue)
    {
        final ConfigBoolean option = new ConfigBoolean(name(serviceKey, key), defaultValue);
        bind(serviceKey, key, option);
        option.setValueChangeCallback(config -> save(activeContext()));
        return option;
    }

    private static ConfigInteger integer(final String serviceKey, final String key, final int defaultValue, final int min, final int max)
    {
        final ConfigInteger option = new ConfigInteger(name(serviceKey, key), defaultValue, min, max);
        bind(serviceKey, key, option);
        option.setValueChangeCallback(config -> save(activeContext()));
        return option;
    }

    private static ConfigString string(final String serviceKey, final String key, final String defaultValue)
    {
        final ConfigString option = new ConfigString(name(serviceKey, key), defaultValue);
        bind(serviceKey, key, option);
        option.setValueChangeCallback(config -> save(activeContext()));
        return option;
    }

    private static ConfigOptionList optionList(final String serviceKey, final String key, final IConfigOptionListEntry defaultValue)
    {
        final ConfigOptionList option = new ConfigOptionList(name(serviceKey, key), defaultValue);
        bind(serviceKey, key, option);
        option.setValueChangeCallback(config -> save(activeContext()));
        return option;
    }

    private static void bind(final String serviceKey, final String key, final IConfigBase option)
    {
        BINDINGS.add(new Binding(serviceKey, key, option));
        OPTIONS.add(option);
    }

    private static String name(final String serviceKey, final String key)
    {
        return serviceKey + "." + key;
    }

    private static Context activeContext()
    {
        return ch.endte.syncmatica.litematica.LitematicManager.getInstance().getActiveContext();
    }

    private static void updateDisplayNames()
    {
        for (final Binding binding : BINDINGS)
        {
            final String displayName = displayName(binding.serviceKey(), binding.configKey());
            binding.option().setPrettyName(displayName);
            binding.option().setTranslatedName(displayName);
        }
    }

    private static String displayName(final String serviceKey, final String key)
    {
        return switch (serviceKey + "." + key)
        {
            case "thirdParty.mode" -> "共享原理图模式";
            case "thirdParty.legacyServerFallback" -> "服务端兼容回退";
            case "thirdParty.requireManualShareConfirm" -> "分享时需要手动确认";
            case "thirdParty.baseUrl" -> "第三方服务地址";
            case "thirdParty.apiToken" -> "API 令牌";
            case "thirdParty.requestTimeoutMs" -> "请求超时 毫秒";
            case "thirdParty.syncIntervalMs" -> "同步间隔 毫秒";
            case "thirdParty.uploadSchematicFile" -> "上传原理图文件";
            case "thirdParty.projectIdentityMode" -> "项目唯一键模式";
            case "thirdParty.materialKeyMode" -> "材料识别模式";
            case "thirdParty.offlineQueueEnabled" -> "离线队列";
            case "thirdParty.shareButtonLabel" -> "分享按钮文案";
            case "thirdParty.showProjectListEntry" -> "显示项目列表入口";
            case "thirdParty.showProjectStatusInPlacementList" -> "投影列表显示项目状态";
            case "thirdParty.autoRefreshProjectOnOpen" -> "打开项目时自动刷新";
            case "thirdParty.claimsEnabled" -> "启用材料认领";
            case "thirdParty.defaultClaimAmountMode" -> "默认认领数量模式";
            case "thirdParty.allowClaimOverRemaining" -> "允许超额认领";
            case "thirdParty.showOnlyMyClaimsByDefault" -> "默认仅显示我的认领";
            case "thirdParty.claimColorMode" -> "认领颜色模式";
            case "thirdParty.hudEnabled" -> "启用 HUD";
            case "thirdParty.aggregationMode" -> "HUD 聚合模式";
            case "thirdParty.maxVisibleProjectTags" -> "HUD 最多项目标签数";
            case "thirdParty.hoverExpandProjects" -> "悬停展开项目标签";
            case "thirdParty.hideCompleted" -> "隐藏已完成材料";
            case "thirdParty.onlyMine" -> "仅显示我的认领";
            case "thirdParty.sortMode" -> "HUD 排序模式";
            case "thirdParty.storageZonesEnabled" -> "启用备货区";
            case "thirdParty.maxZonesPerProject" -> "每项目最大备货区数量";
            case "thirdParty.activationDistance" -> "备货区激活距离";
            case "thirdParty.storageZoneScanIntervalMs" -> "备货区扫描间隔 毫秒";
            case "thirdParty.staleAfterMs" -> "扫描缓存过期 毫秒";
            case "thirdParty.denyRetryCooldownMs" -> "无权限重试冷却 毫秒";
            case "thirdParty.countZoneContentsForProject" -> "备货区内容计入项目";
            case "thirdParty.inventoryAggregationEnabled" -> "启用本地已收集统计";
            case "thirdParty.includePlayerInventory" -> "统计玩家背包";
            case "thirdParty.includeShulkerContents" -> "统计潜影盒内容";
            case "thirdParty.includeStorageZones" -> "统计备货区";
            case "thirdParty.includeNonZoneContainers" -> "统计非备货区容器";
            case "thirdParty.recomputeIntervalMs" -> "本地统计重算间隔 毫秒";
            case "thirdParty.uploadOnlyAggregatedCollected" -> "仅上传项目级汇总";
            case "thirdParty.advancedStockingEnabled" -> "启用高级备货";
            case "thirdParty.advancedStockingMode" -> "高级备货模式";
            case "thirdParty.advancedScanContainers" -> "高级备货扫描容器";
            case "thirdParty.advancedScanIntervalMs" -> "高级扫描间隔 毫秒";
            case "thirdParty.advancedSafeActionIntervalMs" -> "高级操作安全间隔 毫秒";
            case "thirdParty.maxContainersPerCycle" -> "每轮最大扫描容器数";
            case "thirdParty.rayLineEnabled" -> "显示容器指引线";
            case "thirdParty.lineMaxDistanceTenths" -> "指引线最大距离 十分之一格";
            case "thirdParty.showContainerPreview" -> "显示容器材料预览";
            case "thirdParty.containerPreviewKeyCode" -> "容器预览按键代码";
            case "thirdParty.highlightClaimedItems" -> "高亮认领材料槽位";
            case "thirdParty.useClaimColors" -> "使用认领颜色";
            case "thirdParty.claimHighlightDefaultColorRgb" -> "默认高亮颜色 RGB";
            case "thirdParty.advancedAutoTakeArmed" -> "允许高级自动取货";
            case "thirdParty.permissionFailCooldownMs" -> "权限失败冷却 毫秒";
            case "thirdParty.nonZoneScansAffectProjectCollected" -> "非备货区扫描影响项目已收集";
            case "thirdParty.logThirdPartyRequests" -> "记录第三方请求日志";
            case "thirdParty.showScanOverlay" -> "显示扫描调试覆盖层";
            case "thirdParty.logAggregateRecompute" -> "记录统计重算日志";
            case "debug.doPackageLogging" -> "记录网络包日志";
            case "quota.enabled" -> "启用服务端配额";
            case "quota.limit" -> "服务端配额上限";
            default -> serviceKey + "." + key;
        };
    }

    private enum ShareMode implements IConfigOptionListEntry
    {
        THIRD_PARTY("third_party", "第三方同步"),
        LEGACY_SERVER("legacy_server", "服务端支持");

        private final String value;
        private final String displayName;

        ShareMode(final String value, final String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return value;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(final boolean forward)
        {
            return this == THIRD_PARTY ? LEGACY_SERVER : THIRD_PARTY;
        }

        @Override
        public IConfigOptionListEntry fromString(final String value)
        {
            for (final ShareMode mode : values())
            {
                if (mode.value.equalsIgnoreCase(value))
                {
                    return mode;
                }
            }
            return THIRD_PARTY;
        }
    }

    private enum ProjectIdentityMode implements IConfigOptionListEntry
    {
        PLACEMENT_HASH("placement_hash", "投影和位置"),
        SCHEMATIC_HASH("schematic_hash", "仅投影文件");

        private final String value;
        private final String displayName;

        ProjectIdentityMode(final String value, final String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return value;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(final boolean forward)
        {
            return SyncmaticaConfigOptions.cycle(values(), this, forward);
        }

        @Override
        public IConfigOptionListEntry fromString(final String value)
        {
            return SyncmaticaConfigOptions.fromString(values(), value, PLACEMENT_HASH);
        }
    }

    private enum MaterialKeyMode implements IConfigOptionListEntry
    {
        ITEM_NBT("item_nbt", "物品和 NBT"),
        ITEM_ID("item_id", "仅物品 ID");

        private final String value;
        private final String displayName;

        MaterialKeyMode(final String value, final String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return value;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(final boolean forward)
        {
            return SyncmaticaConfigOptions.cycle(values(), this, forward);
        }

        @Override
        public IConfigOptionListEntry fromString(final String value)
        {
            return SyncmaticaConfigOptions.fromString(values(), value, ITEM_NBT);
        }
    }

    private enum ClaimAmountMode implements IConfigOptionListEntry
    {
        SMART_STACK("smart_stack", "智能一组"),
        ALL_MISSING("all_missing", "全部缺口");

        private final String value;
        private final String displayName;

        ClaimAmountMode(final String value, final String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return value;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(final boolean forward)
        {
            return SyncmaticaConfigOptions.cycle(values(), this, forward);
        }

        @Override
        public IConfigOptionListEntry fromString(final String value)
        {
            return SyncmaticaConfigOptions.fromString(values(), value, SMART_STACK);
        }
    }

    private enum ClaimColorMode implements IConfigOptionListEntry
    {
        PER_MATERIAL("per_material", "按材料"),
        SINGLE("single", "统一颜色");

        private final String value;
        private final String displayName;

        ClaimColorMode(final String value, final String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return value;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(final boolean forward)
        {
            return SyncmaticaConfigOptions.cycle(values(), this, forward);
        }

        @Override
        public IConfigOptionListEntry fromString(final String value)
        {
            return SyncmaticaConfigOptions.fromString(values(), value, PER_MATERIAL);
        }
    }

    private enum HudAggregationMode implements IConfigOptionListEntry
    {
        LOCAL_ONLY("local_only", "仅本地");

        private final String value;
        private final String displayName;

        HudAggregationMode(final String value, final String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return value;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(final boolean forward)
        {
            return this;
        }

        @Override
        public IConfigOptionListEntry fromString(final String value)
        {
            return LOCAL_ONLY;
        }
    }

    private enum HudSortMode implements IConfigOptionListEntry
    {
        MISSING_DESC("missing_desc", "缺口降序"),
        NAME("name", "材料名称"),
        CLAIMED_FIRST("claimed_first", "我的认领优先");

        private final String value;
        private final String displayName;

        HudSortMode(final String value, final String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return value;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(final boolean forward)
        {
            return SyncmaticaConfigOptions.cycle(values(), this, forward);
        }

        @Override
        public IConfigOptionListEntry fromString(final String value)
        {
            return SyncmaticaConfigOptions.fromString(values(), value, MISSING_DESC);
        }
    }

    private enum AdvancedStockingMode implements IConfigOptionListEntry
    {
        V1_SCAN("v1_scan", "V1 指引扫描"),
        V2_SAFE_TAKE("v2_safe_take", "V2 安全取货"),
        V3_UNRESTRICTED_TAKE("v3_unrestricted_take", "V3 视距取货");

        private final String value;
        private final String displayName;

        AdvancedStockingMode(final String value, final String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return value;
        }

        @Override
        public String getDisplayName()
        {
            return displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(final boolean forward)
        {
            return SyncmaticaConfigOptions.cycle(values(), this, forward);
        }

        @Override
        public IConfigOptionListEntry fromString(final String value)
        {
            return SyncmaticaConfigOptions.fromString(values(), value, V1_SCAN);
        }
    }

    private static IConfigOptionListEntry cycle(final IConfigOptionListEntry[] values, final IConfigOptionListEntry current, final boolean forward)
    {
        for (int i = 0; i < values.length; i++)
        {
            if (values[i] == current)
            {
                final int next = Math.floorMod(i + (forward ? 1 : -1), values.length);
                return values[next];
            }
        }
        return values[0];
    }

    private static IConfigOptionListEntry fromString(final IConfigOptionListEntry[] values, final String value, final IConfigOptionListEntry fallback)
    {
        for (final IConfigOptionListEntry entry : values)
        {
            if (entry.getStringValue().equalsIgnoreCase(value))
            {
                return entry;
            }
        }
        return fallback;
    }

    private record Binding(String serviceKey, String configKey, IConfigBase option)
    {
    }
}
