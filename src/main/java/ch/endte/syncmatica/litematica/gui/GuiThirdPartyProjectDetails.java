package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.service.ThirdPartySyncService;
import ch.endte.syncmatica.thirdparty.MaterialClaim;
import ch.endte.syncmatica.thirdparty.ProjectMaterial;
import ch.endte.syncmatica.thirdparty.StorageZone;
import ch.endte.syncmatica.thirdparty.ThirdPartyProjectRecord;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public class GuiThirdPartyProjectDetails extends GuiBase
{
    private final ThirdPartyProjectRecord record;
    private BlockPos firstCorner;
    private BlockPos secondCorner;

    public GuiThirdPartyProjectDetails(final ThirdPartyProjectRecord record)
    {
        this.record = record;
        title = StringUtils.translate("syncmatica.gui.title.project_details", WidgetListThirdPartyProject.projectName(record));
    }

    @Override
    public void initGui()
    {
        super.initGui();
        int x = 10;
        final int y = height - 26;

        String label = StringUtils.translate("syncmatica.gui.button.back");
        int buttonWidth = getStringWidth(label) + 20;
        ButtonGeneric button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> GuiBase.openGui(getParent()));
        x += buttonWidth + 4;

        label = StringUtils.translate("syncmatica.gui.button.refresh_projects");
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> service().refreshProjectDetails(record));
        x += buttonWidth + 4;

        label = StringUtils.translate("syncmatica.gui.button.zone_corner_a");
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> {
            firstCorner = currentPosition();
            addMessage(Message.MessageType.SUCCESS, "syncmatica.success.storage_zone_corner_set", "A", posString(firstCorner));
        });
        x += buttonWidth + 4;

        label = StringUtils.translate("syncmatica.gui.button.zone_corner_b");
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> {
            secondCorner = currentPosition();
            addMessage(Message.MessageType.SUCCESS, "syncmatica.success.storage_zone_corner_set", "B", posString(secondCorner));
        });
        x += buttonWidth + 4;

        label = StringUtils.translate("syncmatica.gui.button.save_zone");
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> {
            if (firstCorner == null || secondCorner == null)
            {
                addMessage(Message.MessageType.ERROR, "syncmatica.error.storage_zone_missing_corners");
                return;
            }
            service().upsertStorageZone(record.getProjectId(), currentDimension(), firstCorner, secondCorner);
            addMessage(Message.MessageType.SUCCESS, "syncmatica.success.storage_zone_saved", WidgetListThirdPartyProject.projectName(record));
        });

        addMaterialButtons();
    }

    private void addMaterialButtons()
    {
        int y = 108;
        final int claimX = width - 168;
        final int cancelX = width - 84;
        final List<ProjectMaterial> materials = sortedMaterials();
        final int rows = Math.min(12, materials.size());
        for (int i = 0; i < rows; i++)
        {
            final ProjectMaterial material = materials.get(i);
            String label = StringUtils.translate("syncmatica.gui.button.claim");
            ButtonGeneric button = new ButtonGeneric(claimX, y, 78, 20, label);
            addButton(button, (b, mouseButton) -> service().claimMaterial(record.getProjectId(), material.materialKey, 0));

            label = StringUtils.translate("syncmatica.gui.button.cancel_claim");
            button = new ButtonGeneric(cancelX, y, 78, 20, label);
            addButton(button, (b, mouseButton) -> {
                final MaterialClaim claim = myClaim(material.materialKey);
                if (claim != null)
                {
                    service().cancelClaim(claim.claimId);
                }
            });
            y += 22;
        }
    }

    @Override
    protected void drawContents(final GuiContext ctx, final int mouseX, final int mouseY, final float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        drawProjectHeader(ctx);
        drawMaterialRows(ctx);
        drawZones(ctx);
    }

    private void drawProjectHeader(final GuiContext ctx)
    {
        int x = 10;
        int y = 24;
        RenderUtils.drawOutlinedBox(ctx, x, y, width - 20, 58, 0xA0000000, COLOR_HORIZONTAL_BAR);
        x += 6;
        y += 6;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.placement_info.display_name") + ": " + WidgetListThirdPartyProject.projectName(record), x, y, 0xFFFFFFFF);
        y += 12;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.project_id") + ": " + record.getProjectId(), x, y, 0xFFC0C0C0);
        y += 12;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.status") + ": " + record.getStatus()
                + "    " + StringUtils.translate("syncmatica.gui.label.project_info.updated_at") + ": " + record.getUpdatedAt(), x, y, 0xFFC0C0C0);
        y += 12;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.placement_info.dimension_id") + ": " + record.getProject().dimension
                + "    " + StringUtils.translate("syncmatica.gui.label.placement_info.position") + ": "
                + record.getProject().originX + " " + record.getProject().originY + " " + record.getProject().originZ, x, y, 0xFFC0C0C0);
    }

    private void drawMaterialRows(final GuiContext ctx)
    {
        final int x = 10;
        int y = 88;
        final int listWidth = width - 188;
        RenderUtils.drawOutlinedBox(ctx, x, y - 4, width - 20, 286, 0xA0000000, COLOR_HORIZONTAL_BAR);
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.materials"), x + 6, y, 0xFFFFFFFF);
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.counts"), x + listWidth - 126, y, 0xFFFFFFFF);
        y += 20;

        final List<ProjectMaterial> materials = sortedMaterials();
        final int rows = Math.min(12, materials.size());
        for (int i = 0; i < rows; i++)
        {
            final ProjectMaterial material = materials.get(i);
            final int rowY = y + i * 22;
            if ((i & 1) == 1)
            {
                RenderUtils.drawRect(ctx, x + 2, rowY - 2, width - 24, 20, 0x20101010);
            }

            final ItemStack stack = itemStackFor(material.itemId);
            RenderUtils.drawRect(ctx, x + 6, rowY, 16, 16, 0x20FFFFFF);
            if (!stack.isEmpty())
            {
                ctx.renderItem(stack, x + 6, rowY);
            }
            drawString(ctx, trim(material.displayName, 34), x + 28, rowY + 4, 0xFFFFFFFF);

            final int collected = record.getCollected().getOrDefault(material.materialKey, material.collected);
            drawString(ctx, String.valueOf(collected), x + listWidth - 126, rowY + 4, collected >= material.required ? 0xFF55FF55 : 0xFFFFD54F);
            drawString(ctx, "/" + material.reserved + "/" + material.required, x + listWidth - 96, rowY + 4, 0xFFE0E0E0);
            final MaterialClaim claim = myClaim(material.materialKey);
            if (claim != null)
            {
                drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.mine") + ": " + claim.targetAmount, x + listWidth - 48, rowY + 4, 0xFF77DD77);
            }
        }
    }

    private void drawZones(final GuiContext ctx)
    {
        int x = 10;
        int y = height - 56;
        final String cornerA = firstCorner == null ? "-" : posString(firstCorner);
        final String cornerB = secondCorner == null ? "-" : posString(secondCorner);
        drawString(ctx, "A: " + cornerA + "  B: " + cornerB, x, y, 0xFFC0C0C0);
        y += 12;
        if (!record.getZones().isEmpty())
        {
            final StorageZone zone = record.getZones().get(record.getZones().size() - 1);
            drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.storage_zones") + ": "
                    + zone.dimension + " " + zone.minX + "," + zone.minY + "," + zone.minZ + " -> "
                    + zone.maxX + "," + zone.maxY + "," + zone.maxZ, x, y, 0xFFC0C0C0);
        }
    }

    private List<ProjectMaterial> sortedMaterials()
    {
        return record.getMaterials().stream()
                .sorted(Comparator.comparingInt((ProjectMaterial material) ->
                        Math.max(0, material.required - record.getCollected().getOrDefault(material.materialKey, material.collected))).reversed()
                        .thenComparing(material -> material.displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private MaterialClaim myClaim(final String materialKey)
    {
        final String player = currentPlayerName();
        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.materialKey.equals(materialKey) && claim.assignee.equalsIgnoreCase(player))
            {
                return claim;
            }
        }
        return null;
    }

    private ThirdPartySyncService service()
    {
        final Context context = LitematicManager.getInstance().getActiveContext();
        return context.getThirdPartySyncService();
    }

    private static ItemStack itemStackFor(final String itemId)
    {
        final Identifier id = itemId == null ? null : Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id))
        {
            return ItemStack.EMPTY;
        }
        final Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static BlockPos currentPosition()
    {
        final Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
    }

    private static String currentDimension()
    {
        final Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null ? mc.level.dimension().identifier().toString() : "";
    }

    private static String currentPlayerName()
    {
        final Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null ? mc.player.getName().getString() : "";
    }

    private static String posString(final BlockPos pos)
    {
        return pos == null ? "-" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String trim(final String value, final int max)
    {
        if (value == null || value.length() <= max)
        {
            return value == null ? "" : value;
        }
        return value.substring(0, max - 3) + "...";
    }
}
