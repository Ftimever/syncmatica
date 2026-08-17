package ch.endte.syncmatica.mixin;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.Syncmatica;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui
{
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void syncmatica$renderThirdPartyHud(final GuiGraphicsExtractor gui, final DeltaTracker deltaTracker, final CallbackInfo ci)
    {
        final Context context = Syncmatica.getContext(Syncmatica.CLIENT_CONTEXT);
        if (context != null && context.getThirdPartySyncService() != null)
        {
            context.getThirdPartySyncService().renderHud(gui);
        }
    }
}
