package ch.endte.syncmatica.litematica_mixin;

import ch.endte.syncmatica.litematica.gui.GuiSyncmaticaConfigs;
import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiConfigs.class)
public abstract class MixinGuiConfigs extends GuiBase
{
    @Inject(method = "initGui", at = @At("RETURN"), remap = false)
    private void syncmatica$addConfigCategory(final CallbackInfo ci)
    {
        final String label = StringUtils.translate("syncmatica.gui.button.configs");
        final int buttonWidth = Math.max(96, getStringWidth(label) + 20);
        final ButtonGeneric button = new ButtonGeneric(width - buttonWidth - 10, 26, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> syncmatica$openConfigGui());
    }

    @Unique
    private void syncmatica$openConfigGui()
    {
        final GuiSyncmaticaConfigs gui = new GuiSyncmaticaConfigs();
        gui.setParent(this);
        GuiBase.openGui(gui);
    }
}
