package ch.endte.syncmatica.litematica.gui;

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

        final String text = StringUtils.translate("syncmatica.gui.button.details");
        final int len = getStringWidth(text) + 10;
        final ButtonGeneric button = new ButtonGeneric(x + width - len - 2, y + 1, len, 20, text);
        addButton(button, (b, mouseButton) -> {
            final GuiThirdPartyProjectDetails gui = new GuiThirdPartyProjectDetails(record);
            gui.setParent(parent);
            GuiBase.openGui(gui);
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
        final int missing = record.getMaterials().stream()
                .mapToInt(material -> Math.max(0, material.required - record.getCollected().getOrDefault(material.materialKey, material.collected)))
                .sum();
        return record.getStatus() + " / " + StringUtils.translate("syncmatica.gui.label.project_info.missing") + ": " + missing;
    }
}
