package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.litematica.ScreenHelper;
import ch.endte.syncmatica.service.ThirdPartySyncService;
import ch.endte.syncmatica.thirdparty.MaterialClaim;
import ch.endte.syncmatica.thirdparty.Project;
import ch.endte.syncmatica.thirdparty.ProjectMaterial;
import ch.endte.syncmatica.thirdparty.StorageZone;
import ch.endte.syncmatica.thirdparty.ThirdPartyProjectRecord;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSearchBar;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WidgetListThirdPartyProject extends WidgetListBase<ThirdPartyProjectRecord, WidgetThirdPartyProjectEntry>
{
    private final int infoWidth = 230;
    private final int infoHeight = 320;
    private final GuiThirdPartyProjectList parent;
    private final UUID currentPlacementId;
    private final boolean onlyMyClaims;

    public WidgetListThirdPartyProject(final int x, final int y, final int width, final int height, final GuiThirdPartyProjectList parent,
                                       final ISelectionListener<ThirdPartyProjectRecord> selectionListener,
                                       final UUID currentPlacementId, final boolean onlyMyClaims)
    {
        super(x, y, width, height, selectionListener);
        this.parent = parent;
        this.currentPlacementId = currentPlacementId;
        this.onlyMyClaims = onlyMyClaims;
        browserEntryHeight = 22;
        widgetSearchBar = new WidgetSearchBar(x + 2, y + 4, width - 14, 14, 0, Icons.FILE_ICON_SEARCH, LeftRight.LEFT);
        browserEntriesOffsetY = widgetSearchBar.getHeight() + 3;
        setSize(width, height);
        ScreenHelper.ifPresent(s -> s.setCurrentGui(parent));
    }

    @Override
    public void setSize(final int width, final int height)
    {
        super.setSize(width, height);
        browserWidth = width - 6 - infoWidth;
        browserEntryWidth = browserWidth - 14;
    }

    @Override
    public void drawContents(final GuiContext ctx, final int mouseX, final int mouseY, final float partialTicks)
    {
        RenderUtils.drawOutlinedBox(ctx, posX, posY, browserWidth, browserHeight, 0xB0000000, GuiBase.COLOR_HORIZONTAL_BAR);
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        drawProjectInfo(ctx, getLastSelectedEntry());
    }

    private void drawProjectInfo(final GuiContext ctx, final ThirdPartyProjectRecord record)
    {
        int x = posX + totalWidth - infoWidth;
        int y = posY;
        final int height = Math.min(infoHeight, parent.getMaxInfoHeight());
        RenderUtils.drawOutlinedBox(ctx, x, y, infoWidth, height, 0xA0000000, GuiBase.COLOR_HORIZONTAL_BAR);
        if (record == null)
        {
            return;
        }

        x += 4;
        y += 4;
        final int label = 0xC0C0C0C0;
        final int value = 0xFFFFFFFF;
        final Project project = record.getProject();
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ThirdPartySyncService thirdParty = context != null ? context.getThirdPartySyncService() : null;
        final boolean downloaded = thirdParty != null && thirdParty.isProjectDownloaded(record);

        y = drawPair(ctx, x, y, "syncmatica.gui.label.placement_info.display_name", projectName(record), label, value);
        y = drawPair(ctx, x, y, "syncmatica.gui.label.project_info.project_id", shortValue(record.getProjectId(), 26), label, value);
        y = drawPair(ctx, x, y, "syncmatica.gui.label.project_info.status", projectStage(record), label, value);
        y = drawPair(ctx, x, y, "syncmatica.gui.label.project_download.state",
                StringUtils.translate(downloaded ? "syncmatica.gui.label.project_download.downloaded" : "syncmatica.gui.label.project_download.not_downloaded"),
                label, downloaded ? 0xFF77DD77 : 0xFFFFD54F);
        y = drawPair(ctx, x, y, "syncmatica.gui.label.placement_info.dimension_id", project.dimension, label, value);
        y = drawPair(ctx, x, y, "syncmatica.gui.label.placement_info.position",
                project.originX + " " + project.originY + " " + project.originZ, label, value);
        y = drawPair(ctx, x, y, "syncmatica.gui.label.project_info.updated_at", shortValue(record.getUpdatedAt(), 28), label, value);
        final String participants = downloaded ? participantsText(record) : (project.owner == null || project.owner.isBlank() ? "-" : project.owner);
        y = drawPair(ctx, x, y, "syncmatica.gui.label.project_info.participants", shortValue(participants, 30), label, value);

        if (!downloaded)
        {
            y += 8;
            drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_download.download_to_join"), x, y, 0xFFFFD54F);
            return;
        }

        y += 4;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.materials") + ": " + record.getMaterials().size(), x, y, label);
        y += 12;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.claims") + ": " + record.getClaims().size(), x, y, label);
        y += 12;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.storage_zones") + ": " + record.getZones().size(), x, y, label);
        y += 16;

        int shown = 0;
        for (final ProjectMaterial material : record.getMaterials())
        {
            if (shown >= 6)
            {
                break;
            }
            drawString(ctx, shortValue(material.displayName, 20), x + 2, y, value);
            final int collected = collectedAmount(record, material);
            final int reserved = reservedAmount(record, material.materialKey);
            drawString(ctx, collected + "/" + reserved + "/" + material.required, x + 120, y, collected >= material.required ? 0xFF55FF55 : 0xFFFFD54F);
            y += 12;
            shown++;
        }

        if (record.getZones().size() > 0)
        {
            y += 4;
            final StorageZone zone = record.getZones().get(0);
            drawString(ctx, "Zone: " + zone.minX + "," + zone.minY + "," + zone.minZ + " -> " + zone.maxX + "," + zone.maxY + "," + zone.maxZ, x, y, 0xFFAAAAAA);
        }
    }

    private int drawPair(final GuiContext ctx, final int x, int y, final String key, final String text, final int label, final int value)
    {
        drawString(ctx, StringUtils.translate(key), x, y, label);
        y += 12;
        drawString(ctx, text == null ? "" : text, x + 4, y, value);
        return y + 12;
    }

    @Override
    protected List<String> getEntryStringsForFilter(final ThirdPartyProjectRecord entry)
    {
        return ImmutableList.of(projectName(entry).toLowerCase(), entry.getProjectId().toLowerCase(), entry.getProject().dimension.toLowerCase());
    }

    @Override
    protected WidgetThirdPartyProjectEntry createListEntryWidget(final int x, final int y, final int listIndex, final boolean isOdd, final ThirdPartyProjectRecord entry)
    {
        return new WidgetThirdPartyProjectEntry(x, y, browserEntryWidth, getBrowserEntryHeightFor(entry), entry, listIndex, parent);
    }

    @Override
    protected Collection<ThirdPartyProjectRecord> getAllEntries()
    {
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ThirdPartySyncService thirdParty = context != null ? context.getThirdPartySyncService() : null;
        if (thirdParty == null)
        {
            return List.of();
        }
        final String player = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : "";
        return thirdParty.getProjects().stream()
                .filter(record -> currentPlacementId == null || currentPlacementId.toString().equals(record.getPlacementId()))
                .filter(record -> !onlyMyClaims || hasClaimBy(record, player))
                .sorted(Comparator.comparing(WidgetListThirdPartyProject::projectName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    static String projectName(final ThirdPartyProjectRecord record)
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

    static String projectStage(final ThirdPartyProjectRecord record)
    {
        if (record == null)
        {
            return StringUtils.translate("syncmatica.gui.label.project_status.collecting");
        }
        final String status = record.getProject().status == null || record.getProject().status.isBlank()
                ? record.getStatus()
                : record.getProject().status;
        if ("completed".equalsIgnoreCase(status))
        {
            return StringUtils.translate("syncmatica.gui.label.project_status.completed");
        }
        if ("building".equalsIgnoreCase(status))
        {
            return StringUtils.translate("syncmatica.gui.label.project_status.building");
        }
        if ("collecting".equalsIgnoreCase(status))
        {
            return StringUtils.translate("syncmatica.gui.label.project_status.collecting");
        }
        if (record.getMaterials().isEmpty())
        {
            return StringUtils.translate("syncmatica.gui.label.project_status.collecting");
        }

        boolean allCollected = true;
        boolean allCovered = true;
        for (final ProjectMaterial material : record.getMaterials())
        {
            final int collected = collectedAmount(record, material);
            final int reserved = reservedAmount(record, material.materialKey);
            if (collected < material.required)
            {
                allCollected = false;
            }
            if (collected + reserved < material.required)
            {
                allCovered = false;
            }
        }

        if (allCollected)
        {
            return StringUtils.translate("syncmatica.gui.label.project_status.completed");
        }
        if (allCovered)
        {
            return StringUtils.translate("syncmatica.gui.label.project_status.building");
        }
        return StringUtils.translate("syncmatica.gui.label.project_status.collecting");
    }

    static int reservedAmount(final ThirdPartyProjectRecord record, final String materialKey)
    {
        int amount = 0;
        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.materialKey.equals(materialKey))
            {
                amount += claim.targetAmount;
            }
        }
        for (final ProjectMaterial material : record.getMaterials())
        {
            if (material.materialKey.equals(materialKey))
            {
                amount = Math.max(amount, material.reserved);
                break;
            }
        }
        return amount;
    }

    static int collectedAmount(final ThirdPartyProjectRecord record, final ProjectMaterial material)
    {
        if (record == null || material == null)
        {
            return 0;
        }
        return Math.max(0, record.getCollected().getOrDefault(material.materialKey, 0));
    }

    static String participantsText(final ThirdPartyProjectRecord record)
    {
        final List<String> participants = participants(record);
        return participants.isEmpty() ? "-" : String.join(", ", participants);
    }

    static List<String> participants(final ThirdPartyProjectRecord record)
    {
        final Map<String, String> names = new LinkedHashMap<>();
        if (record == null)
        {
            return List.of();
        }
        final String owner = record.getProject().owner;
        if (owner != null && !owner.isBlank())
        {
            names.put(owner.toLowerCase(), owner);
        }
        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.assignee != null && !claim.assignee.isBlank())
            {
                names.put(claim.assignee.toLowerCase(), claim.assignee);
            }
        }
        return new ArrayList<>(names.values());
    }

    static List<String> claimLines(final ThirdPartyProjectRecord record, final String materialKey)
    {
        final Map<String, Integer> claims = new LinkedHashMap<>();
        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.materialKey.equals(materialKey) && claim.targetAmount > 0)
            {
                claims.merge(claim.assignee == null || claim.assignee.isBlank() ? "?" : claim.assignee, claim.targetAmount, Integer::sum);
            }
        }
        return claims.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.toList());
    }

    private static boolean hasClaimBy(final ThirdPartyProjectRecord record, final String player)
    {
        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.assignee.equalsIgnoreCase(player))
            {
                return true;
            }
        }
        return false;
    }

    private static String shortValue(final String value, final int max)
    {
        if (value == null || value.length() <= max)
        {
            return value == null ? "" : value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
