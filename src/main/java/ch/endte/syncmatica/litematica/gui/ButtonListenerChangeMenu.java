package ch.endte.syncmatica.litematica.gui;

import net.minecraft.client.gui.screens.Screen;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.service.ThirdPartySyncService;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;

public class ButtonListenerChangeMenu implements IButtonActionListener
{
    private final MainMenuButtonType type;
    private final Screen parent;

    public ButtonListenerChangeMenu(final MainMenuButtonType type, final Screen parent)
    {
        this.type = type;
        this.parent = parent;
    }

    @Override
    public void actionPerformedWithButton(final ButtonBase arg0, final int arg1)
    {
        GuiBase gui = null;
        switch (type)
        {
//            case MATERIAL_GATHERINGS:
//                LogManager.getLogger().info("Opened Material Gatherings GUI - currently unsupported operation");
//                break;
            case VIEW_SYNCMATICS:
                final Context context = LitematicManager.getInstance().getActiveContext();
                final ThirdPartySyncService thirdParty = context != null ? context.getThirdPartySyncService() : null;
                gui = thirdParty != null && thirdParty.isThirdPartyMode()
                        ? new GuiThirdPartyProjectList()
                        : new GuiSyncmaticaServerPlacementList();
                break;
            default:
                break;
        }
        if (gui != null)
        {
            gui.setParent(parent);
            GuiBase.openGui(gui);
        }
    }
}
