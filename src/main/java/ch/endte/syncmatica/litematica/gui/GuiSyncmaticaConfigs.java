package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Reference;
import ch.endte.syncmatica.config.SyncmaticaConfigOptions;
import ch.endte.syncmatica.litematica.LitematicManager;
import fi.dy.masa.malilib.gui.GuiConfigsBase;

import java.util.List;

public class GuiSyncmaticaConfigs extends GuiConfigsBase
{
    public GuiSyncmaticaConfigs()
    {
        super(10, 50, Reference.MOD_ID, null, "syncmatica.gui.title.configs", Reference.MOD_VERSION);
        setConfigWidth(230);
        SyncmaticaConfigOptions.load(LitematicManager.getInstance().getActiveContext());
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs()
    {
        return ConfigOptionWrapper.createFor(SyncmaticaConfigOptions.options());
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
