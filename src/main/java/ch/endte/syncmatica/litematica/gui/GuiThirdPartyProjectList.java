package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Reference;
import ch.endte.syncmatica.data.ServerPlacement;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.service.ThirdPartySyncService;
import ch.endte.syncmatica.thirdparty.ThirdPartyProjectRecord;
import fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.UUID;

public class GuiThirdPartyProjectList extends GuiListBase<ThirdPartyProjectRecord, WidgetThirdPartyProjectEntry, WidgetListThirdPartyProject>
{
    private final UUID currentPlacementId;
    private final boolean onlyMyClaims;

    public GuiThirdPartyProjectList()
    {
        this(null, false);
    }

    public GuiThirdPartyProjectList(final ServerPlacement placement, final boolean onlyMyClaims)
    {
        super(12, 30);
        this.currentPlacementId = placement != null ? placement.getId() : null;
        this.onlyMyClaims = onlyMyClaims;
        title = StringUtils.translate("syncmatica.gui.title.manage_projects", String.format("v%s", Reference.MOD_VERSION));
    }

    @Override
    public void initGui()
    {
        super.initGui();
        final ThirdPartySyncService thirdParty = LitematicManager.getInstance().getActiveContext().getThirdPartySyncService();
        final int y = height - 26;
        int x = 10;

        String label = StringUtils.translate("syncmatica.gui.button.refresh_projects");
        int buttonWidth = getStringWidth(label) + 20;
        ButtonGeneric button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> {
            if (thirdParty != null)
            {
                thirdParty.refreshProjects();
                getListWidget().refreshEntries();
            }
        });
        x += buttonWidth + 4;

        label = onlyMyClaims
                ? StringUtils.translate("syncmatica.gui.button.view_all_projects")
                : StringUtils.translate("syncmatica.gui.button.view_my_claims");
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> {
            final GuiThirdPartyProjectList gui = new GuiThirdPartyProjectList(null, !onlyMyClaims);
            gui.setParent(getParent());
            GuiBase.openGui(gui);
        });

        final ButtonListenerChangeMenu.ButtonType type = ButtonListenerChangeMenu.ButtonType.MAIN_MENU;
        label = StringUtils.translate(type.getLabelKey());
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(width - buttonWidth - 10, y, buttonWidth, 20, label);
        addButton(button, new ButtonListenerChangeMenu(type, getParent()));
    }

    @Override
    protected WidgetListThirdPartyProject createListWidget(final int listX, final int listY)
    {
        return new WidgetListThirdPartyProject(listX, listY, getBrowserWidth(), getBrowserHeight(), this, null, currentPlacementId, onlyMyClaims);
    }

    @Override
    protected int getBrowserHeight()
    {
        return height - 68;
    }

    @Override
    protected int getBrowserWidth()
    {
        return width - 20;
    }

    public int getMaxInfoHeight()
    {
        return getBrowserHeight();
    }
}
