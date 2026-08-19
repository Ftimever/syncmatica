package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.data.ServerPlacement;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.litematica.ScreenHelper;
import ch.endte.syncmatica.service.ThirdPartySyncService;
import ch.endte.syncmatica.thirdparty.ThirdPartyProjectRecord;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.ArrayList;

public class WidgetThirdPartyProjectEntry extends WidgetListEntryBase<ThirdPartyProjectRecord>
{
    private final ThirdPartyProjectRecord record;
    private final boolean isOdd;
    private final boolean showGroupHeader;
    private final String groupTitle;
    private final int groupHeaderHeight;

    public WidgetThirdPartyProjectEntry(final int x, int y, final int width, final int height, final ThirdPartyProjectRecord entry,
                                        final int listIndex, final GuiThirdPartyProjectList parent, final boolean showGroupHeader,
                                        final String groupTitle, final int groupHeaderHeight)
    {
        super(x, y, width, height, entry, listIndex);
        this.record = entry;
        this.isOdd = (listIndex % 2) == 1;
        this.showGroupHeader = showGroupHeader;
        this.groupTitle = groupTitle;
        this.groupHeaderHeight = groupHeaderHeight;
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ThirdPartySyncService thirdParty = context != null ? context.getThirdPartySyncService() : null;
        final boolean downloaded = thirdParty != null && thirdParty.isProjectDownloaded(record);
        final int rowY = y + (showGroupHeader ? groupHeaderHeight : 0);

        int posX = x + width - 2;
        String text = StringUtils.translate("syncmatica.gui.button.remove");
        int len = getStringWidth(text) + 10;
        posX -= len;
        ButtonGeneric button = new ButtonGeneric(posX, rowY + 1, len, 20, text);
        addButton(button, (b, mouseButton) -> {
            if (thirdParty == null)
            {
                return;
            }
            if (!GuiBase.isShiftDown())
            {
                ScreenHelper.ifPresent(screen -> screen.addMessage(Message.MessageType.WARNING, "syncmatica.error.delete_without_shift"));
                return;
            }
            thirdParty.forgetProject(record);
        });

        text = StringUtils.translate("syncmatica.gui.button.details");
        len = getStringWidth(text) + 10;
        posX -= len + 2;
        button = new ButtonGeneric(posX, rowY + 1, len, 20, text);
        button.setEnabled(downloaded);
        addButton(button, (b, mouseButton) -> {
            final GuiThirdPartyProjectDetails gui = new GuiThirdPartyProjectDetails(record);
            gui.setParent(parent);
            GuiBase.openGui(gui);
        });

        final ArrayList<IButtonType> actionTypes = new ArrayList<>();
        actionTypes.add(new BaseButtonType("syncmatica.gui.button.download",
                () -> thirdParty != null && record.getPlacement() != null && !thirdParty.isProjectDownloaded(record),
                (b, mouseButton) -> {
                    if (thirdParty != null)
                    {
                        thirdParty.downloadProjectSchematic(record);
                    }
                }));
        actionTypes.add(new BaseButtonType("syncmatica.gui.button.load",
                () -> thirdParty != null && record.getPlacement() != null && thirdParty.isProjectDownloaded(record)
                        && !LitematicManager.getInstance().isRendered(record.getPlacement()),
                (b, mouseButton) -> LitematicManager.getInstance().renderSyncmatic(record.getPlacement())));
        actionTypes.add(new BaseButtonType("syncmatica.gui.button.unload",
                () -> record.getPlacement() != null && LitematicManager.getInstance().isRendered(record.getPlacement()),
                (b, mouseButton) -> LitematicManager.getInstance().unrenderSyncmatic(record.getPlacement())));
        posX -= 2;
        addButton(new MultiTypeButton(posX, rowY + 1, true, actionTypes), null);
    }

    @Override
    public void render(final GuiContext ctx, final int mouseX, final int mouseY, final boolean selected)
    {
        final int rowY = y + (showGroupHeader ? groupHeaderHeight : 0);
        if (showGroupHeader)
        {
            RenderUtils.drawRect(ctx, x, y + 2, width, groupHeaderHeight - 3, 0xA0182430);
            RenderUtils.drawRect(ctx, x, y + groupHeaderHeight - 2, width, 1, GuiBase.COLOR_HORIZONTAL_BAR);
            drawString(ctx, x + 6, y + 5, 0xFFFFD54F, groupTitle);
        }

        if (selected || isMouseOver(mouseX, mouseY))
        {
            RenderUtils.drawRect(ctx, x + 8, rowY, width - 8, 22, 0x70FFFFFF);
        }
        else if (isOdd)
        {
            RenderUtils.drawRect(ctx, x + 8, rowY, width - 8, 22, 0x20FFFFFF);
        }
        else
        {
            RenderUtils.drawRect(ctx, x + 8, rowY, width - 8, 22, 0x50FFFFFF);
        }
        drawString(ctx, x + 18, rowY + 7, 0xFFFFFFFF, WidgetListThirdPartyProject.projectName(record));
        drawString(ctx, x + 182, rowY + 7, 0xFFC0C0C0, statusText());
        drawSubWidgets(ctx, mouseX, mouseY);
    }

    private String statusText()
    {
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ThirdPartySyncService thirdParty = context != null ? context.getThirdPartySyncService() : null;
        if (thirdParty == null || !thirdParty.isProjectDownloaded(record))
        {
            return StringUtils.translate("syncmatica.gui.label.project_download.not_downloaded")
                    + " / " + WidgetListThirdPartyProject.projectStage(record);
        }

        final int missing = record.getMaterials().stream()
                .mapToInt(material -> Math.max(0, material.required
                        - WidgetListThirdPartyProject.collectedAmount(record, material)
                        - WidgetListThirdPartyProject.reservedAmount(record, material.materialKey)))
                .sum();
        return WidgetListThirdPartyProject.projectStage(record) + " / " + StringUtils.translate("syncmatica.gui.label.project_info.missing") + ": " + missing;
    }

}
