package ch.endte.syncmatica.mixin;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.Syncmatica;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class MixinAbstractContainerScreen
{
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void syncmatica$suppressAdvancedStockingContainerScreen(final GuiGraphicsExtractor gui, final int mouseX, final int mouseY, final float partialTicks, final CallbackInfo ci)
    {
        final Context context = Syncmatica.getContext(Syncmatica.CLIENT_CONTEXT);
        if (context != null && context.getThirdPartySyncService() != null
                && context.getThirdPartySyncService().isSuppressingAdvancedContainerScreen())
        {
            ci.cancel();
        }
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void syncmatica$highlightClaimedStockingItems(final GuiGraphicsExtractor gui, final Slot slot, final int slotX, final int slotY, final CallbackInfo ci)
    {
        if (slot == null)
        {
            return;
        }

        final Context context = Syncmatica.getContext(Syncmatica.CLIENT_CONTEXT);
        final Minecraft minecraft = Minecraft.getInstance();
        if (context == null || context.getThirdPartySyncService() == null || minecraft == null || minecraft.player == null)
        {
            return;
        }

        final ItemStack stack = slot.getItem();
        final int color = context.getThirdPartySyncService().getClaimHighlightColor(stack, minecraft.player.getName().getString());
        if (color != 0)
        {
            gui.fill(slotX, slotY, slotX + 16, slotY + 1, color);
            gui.fill(slotX, slotY + 15, slotX + 16, slotY + 16, color);
            gui.fill(slotX, slotY, slotX + 1, slotY + 16, color);
            gui.fill(slotX + 15, slotY, slotX + 16, slotY + 16, color);
        }
    }
}
