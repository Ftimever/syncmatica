package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.service.ThirdPartySyncService;
import ch.endte.syncmatica.thirdparty.ThirdPartyProjectRecord;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class WidgetThirdPartyProjectEntry extends WidgetListEntryBase<ThirdPartyProjectRecord>
{
    private final ThirdPartyProjectRecord record;
    private final boolean isOdd;

    public WidgetThirdPartyProjectEntry(final int x, int y, final int width, final int height, final ThirdPartyProjectRecord entry,
                                        final int listIndex, final GuiThirdPartyProjectList parent)
    {
        super(x, y, width, height, entry, listIndex);
        this.record = entry;
        this.isOdd = (listIndex % 2) == 1;
        final Context context = LitematicManager.getInstance().getActiveContext();
        final ThirdPartySyncService thirdParty = context != null ? context.getThirdPartySyncService() : null;
        final boolean downloaded = thirdParty != null && thirdParty.isProjectDownloaded(record);

        int posX = x + width - 2;
        String text = StringUtils.translate("syncmatica.gui.button.details");
        int len = getStringWidth(text) + 10;
        posX -= len;
        ButtonGeneric button = new ButtonGeneric(posX, y + 1, len, 20, text);
        button.setEnabled(downloaded);
        addButton(button, (b, mouseButton) -> {
            final GuiThirdPartyProjectDetails gui = new GuiThirdPartyProjectDetails(record);
            gui.setParent(parent);
            GuiBase.openGui(gui);
        });

        text = StringUtils.translate("syncmatica.gui.button.download");
        len = getStringWidth(text) + 10;
        posX -= len + 2;
        button = new ButtonGeneric(posX, y + 1, len, 20, text);
        button.setEnabled(record.getPlacement() != null && !downloaded);
        addButton(button, (b, mouseButton) -> {
            if (thirdParty != null)
            {
                thirdParty.downloadProjectSchematic(record);
            }
        });
    }

    @Override
    public void render(final GuiContext ctx, final int mouseX, final int mouseY, final boolean selected)
    {
        if (selected || isMouseOver(mouseX, mouseY))
        {
            RenderUtils.drawRect(ctx, x, y, width, height, 0x70FFFFFF);
        }
        else if (isOdd)
        {
            RenderUtils.drawRect(ctx, x, y, width, height, 0x20FFFFFF);
        }
        else
        {
            RenderUtils.drawRect(ctx, x, y, width, height, 0x50FFFFFF);
        }

        drawString(ctx, x + 6, y + 7, 0xFFFFFFFF, WidgetListThirdPartyProject.projectName(record));
        drawString(ctx, x + 170, y + 7, 0xFFC0C0C0, statusText());
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
                        - record.getCollected().getOrDefault(material.materialKey, material.collected)
                        - WidgetListThirdPartyProject.reservedAmount(record, material.materialKey)))
                .sum();
        return WidgetListThirdPartyProject.projectStage(record) + " / " + StringUtils.translate("syncmatica.gui.label.project_info.missing") + ": " + missing;
    }
}
