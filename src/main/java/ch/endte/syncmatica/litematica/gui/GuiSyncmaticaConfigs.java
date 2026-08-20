package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Reference;
import ch.endte.syncmatica.config.SyncmaticaConfigOptions;
import ch.endte.syncmatica.litematica.LitematicManager;
import fi.dy.masa.malilib.gui.GuiConfigsBase;

import java.util.ArrayList;
import java.util.List;

public class GuiSyncmaticaConfigs extends GuiConfigsBase
{
    public GuiSyncmaticaConfigs()
    {
        super(10, 50, Reference.MOD_ID, null, "syncmatica.gui.title.configs", Reference.MOD_VERSION);
        setConfigWidth(260);
        SyncmaticaConfigOptions.load(LitematicManager.getInstance().getActiveContext());
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs()
    {
        final List<ConfigOptionWrapper> configs = new ArrayList<>();
        addGroup(configs, "同步模式",
                SyncmaticaConfigOptions.MODE,
                SyncmaticaConfigOptions.LEGACY_SERVER_FALLBACK,
                SyncmaticaConfigOptions.REQUIRE_MANUAL_SHARE_CONFIRM);
        addGroup(configs, "第三方服务",
                SyncmaticaConfigOptions.BASE_URL,
                SyncmaticaConfigOptions.API_TOKEN,
                SyncmaticaConfigOptions.REQUEST_TIMEOUT_MS,
                SyncmaticaConfigOptions.SYNC_INTERVAL_MS,
                SyncmaticaConfigOptions.UPLOAD_SCHEMATIC_FILE,
                SyncmaticaConfigOptions.PROJECT_IDENTITY_MODE,
                SyncmaticaConfigOptions.MATERIAL_KEY_MODE,
                SyncmaticaConfigOptions.OFFLINE_QUEUE_ENABLED);
        addGroup(configs, "项目界面",
                SyncmaticaConfigOptions.SHARE_BUTTON_LABEL,
                SyncmaticaConfigOptions.SHOW_PROJECT_LIST_ENTRY,
                SyncmaticaConfigOptions.SHOW_PROJECT_STATUS_IN_PLACEMENT_LIST,
                SyncmaticaConfigOptions.AUTO_REFRESH_PROJECT_ON_OPEN);
        addGroup(configs, "材料认领",
                SyncmaticaConfigOptions.CLAIMS_ENABLED,
                SyncmaticaConfigOptions.DEFAULT_CLAIM_AMOUNT_MODE,
                SyncmaticaConfigOptions.ALLOW_CLAIM_OVER_REMAINING,
                SyncmaticaConfigOptions.SHOW_ONLY_MY_CLAIMS_BY_DEFAULT,
                SyncmaticaConfigOptions.CLAIM_COLOR_MODE);
        addGroup(configs, "HUD",
                SyncmaticaConfigOptions.HUD_ENABLED,
                SyncmaticaConfigOptions.AGGREGATION_MODE,
                SyncmaticaConfigOptions.MAX_VISIBLE_PROJECT_TAGS,
                SyncmaticaConfigOptions.HOVER_EXPAND_PROJECTS,
                SyncmaticaConfigOptions.HIDE_COMPLETED,
                SyncmaticaConfigOptions.ONLY_MINE,
                SyncmaticaConfigOptions.SORT_MODE);
        addGroup(configs, "备货区",
                SyncmaticaConfigOptions.STORAGE_ZONES_ENABLED,
                SyncmaticaConfigOptions.MAX_ZONES_PER_PROJECT,
                SyncmaticaConfigOptions.ACTIVATION_DISTANCE,
                SyncmaticaConfigOptions.STORAGE_ZONE_SCAN_INTERVAL_MS,
                SyncmaticaConfigOptions.STALE_AFTER_MS,
                SyncmaticaConfigOptions.DENY_RETRY_COOLDOWN_MS,
                SyncmaticaConfigOptions.COUNT_ZONE_CONTENTS_FOR_PROJECT);
        addGroup(configs, "本地统计",
                SyncmaticaConfigOptions.INVENTORY_AGGREGATION_ENABLED,
                SyncmaticaConfigOptions.INCLUDE_PLAYER_INVENTORY,
                SyncmaticaConfigOptions.INCLUDE_SHULKER_CONTENTS,
                SyncmaticaConfigOptions.INCLUDE_STORAGE_ZONES,
                SyncmaticaConfigOptions.INCLUDE_NON_ZONE_CONTAINERS,
                SyncmaticaConfigOptions.RECOMPUTE_INTERVAL_MS,
                SyncmaticaConfigOptions.UPLOAD_ONLY_AGGREGATED_COLLECTED);
        addGroup(configs, "高级备货",
                SyncmaticaConfigOptions.ADVANCED_STOCKING_ENABLED,
                SyncmaticaConfigOptions.ADVANCED_STOCKING_MODE,
                SyncmaticaConfigOptions.ADVANCED_SCAN_CONTAINERS,
                SyncmaticaConfigOptions.ADVANCED_SCAN_INTERVAL_MS,
                SyncmaticaConfigOptions.ADVANCED_SAFE_ACTION_INTERVAL_MS,
                SyncmaticaConfigOptions.MAX_CONTAINERS_PER_CYCLE,
                SyncmaticaConfigOptions.RAY_LINE_ENABLED,
                SyncmaticaConfigOptions.LINE_MAX_DISTANCE_TENTHS,
                SyncmaticaConfigOptions.SHOW_CONTAINER_PREVIEW,
                SyncmaticaConfigOptions.CONTAINER_PREVIEW_KEY_CODE,
                SyncmaticaConfigOptions.HIGHLIGHT_CLAIMED_ITEMS,
                SyncmaticaConfigOptions.USE_CLAIM_COLORS,
                SyncmaticaConfigOptions.CLAIM_HIGHLIGHT_DEFAULT_COLOR_RGB,
                SyncmaticaConfigOptions.ADVANCED_AUTO_TAKE_ARMED,
                SyncmaticaConfigOptions.PERMISSION_FAIL_COOLDOWN_MS,
                SyncmaticaConfigOptions.NON_ZONE_SCANS_AFFECT_PROJECT_COLLECTED);
        addGroup(configs, "调试与配额",
                SyncmaticaConfigOptions.LOG_THIRD_PARTY_REQUESTS,
                SyncmaticaConfigOptions.SHOW_SCAN_OVERLAY,
                SyncmaticaConfigOptions.LOG_AGGREGATE_RECOMPUTE,
                SyncmaticaConfigOptions.DEBUG_PACKET_LOGGING,
                SyncmaticaConfigOptions.QUOTA_ENABLED,
                SyncmaticaConfigOptions.QUOTA_LIMIT);
        return configs;
    }

    private static void addGroup(final List<ConfigOptionWrapper> configs, final String label, final fi.dy.masa.malilib.config.IConfigBase... options)
    {
        configs.add(new ConfigOptionWrapper("§e" + label));
        for (final fi.dy.masa.malilib.config.IConfigBase option : options)
        {
            configs.add(new ConfigOptionWrapper(option));
        }
    }

    @Override
    protected void onSettingsChanged()
    {
        super.onSettingsChanged();
        SyncmaticaConfigOptions.save(LitematicManager.getInstance().getActiveContext());
    }

    @Override
    public void removed()
    {
        SyncmaticaConfigOptions.save(LitematicManager.getInstance().getActiveContext());
        super.removed();
    }
}
