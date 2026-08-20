package ch.endte.syncmatica.litematica.gui;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.service.ThirdPartySyncService;
import ch.endte.syncmatica.thirdparty.MaterialClaim;
import ch.endte.syncmatica.thirdparty.ProjectMaterial;
import ch.endte.syncmatica.thirdparty.StorageZone;
import ch.endte.syncmatica.thirdparty.ThirdPartyProjectRecord;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

public class GuiThirdPartyProjectDetails extends GuiBase
{
    private final ThirdPartyProjectRecord record;
    private BlockPos firstCorner;
    private BlockPos secondCorner;
    private int selectedMaterialIndex;
    private int claimDraftAmount;
    private boolean claimSliderDragging;
    private String pendingClaimMaterialKey = "";
    private GuiTextFieldGeneric projectNameField;
    private boolean editingCornerA = true;

    public GuiThirdPartyProjectDetails(final ThirdPartyProjectRecord record)
    {
        this.record = record;
        title = StringUtils.translate("syncmatica.gui.title.project_details", WidgetListThirdPartyProject.projectName(record));
    }

    @Override
    public void initGui()
    {
        super.initGui();
        final ThirdPartySyncService service = service();
        if (service == null || !service.isProjectDownloaded(record))
        {
            addMessage(Message.MessageType.WARNING, "syncmatica.error.third_party_project_not_downloaded");
            GuiBase.openGui(getParent());
            return;
        }
        service.recomputeLocalCollectedNow();
        initializeZoneCorners();
        selectedMaterialIndex = clampSelectedMaterialIndex(selectedMaterialIndex);
        claimDraftAmount = currentClaimAmount(selectedMaterial());
        addProjectNameEditor(service);
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
        addButton(button, (b, mouseButton) -> {
            service().recomputeLocalCollectedNow();
            service().refreshProjectDetails(record);
            clearClaimPreview();
            claimDraftAmount = currentClaimAmount(selectedMaterial());
        });
        x += buttonWidth + 4;

        label = currentCornerButtonLabel();
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> {
            editingCornerA = !editingCornerA;
            initGui();
        });
        x += buttonWidth + 4;

        label = StringUtils.translate("syncmatica.gui.button.zone_target_block");
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> {
            final BlockPos target = currentTargetBlock();
            if (target == null)
            {
                addMessage(Message.MessageType.WARNING, "syncmatica.error.storage_zone_no_target");
                return;
            }
            setCurrentZoneCorner(target);
        });
        x += buttonWidth + 4;

        label = StringUtils.translate("syncmatica.gui.button.zone_player_pos");
        buttonWidth = getStringWidth(label) + 20;
        button = new ButtonGeneric(x, y, buttonWidth, 20, label);
        addButton(button, (b, mouseButton) -> setCurrentZoneCorner(currentPosition()));
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

        addMaterialActions();
    }

    private void initializeZoneCorners()
    {
        if ((firstCorner != null && secondCorner != null) || record.getZones().isEmpty())
        {
            return;
        }
        final StorageZone zone = record.getZones().get(record.getZones().size() - 1);
        firstCorner = new BlockPos(zone.minX, zone.minY, zone.minZ);
        secondCorner = new BlockPos(zone.maxX, zone.maxY, zone.maxZ);
    }

    private void addProjectNameEditor(final ThirdPartySyncService service)
    {
        final String nameLabel = StringUtils.translate("syncmatica.gui.label.placement_info.display_name") + ":";
        final String buttonLabel = StringUtils.translate("syncmatica.gui.button.save_project_name");
        final int fieldY = projectNameEditorY();
        final int buttonWidth = getStringWidth(buttonLabel) + 16;
        final int buttonX = Math.max(160, width - 16 - buttonWidth);
        final int fieldX = Math.max(16, Math.min(buttonX - 96, 16 + getStringWidth(nameLabel) + 8));
        final int fieldWidth = Math.max(90, buttonX - fieldX - 6);
        projectNameField = new GuiTextFieldGeneric(fieldX, fieldY, fieldWidth, 16, Minecraft.getInstance().font);
        projectNameField.setMaxLengthWrapper(80);
        projectNameField.setTextWrapper(WidgetListThirdPartyProject.projectName(record));
        addTextField(projectNameField, new ITextFieldListener<>()
        {
            @Override
            public boolean onTextChange(final GuiTextFieldGeneric textField)
            {
                return true;
            }
        });

        final ButtonGeneric button = new ButtonGeneric(buttonX, fieldY - 2, buttonWidth, 20, buttonLabel);
        addButton(button, (b, mouseButton) -> {
            final String nextName = projectNameField.getTextWrapper().trim();
            service.renameProject(record, nextName);
            title = StringUtils.translate("syncmatica.gui.title.project_details", nextName.isBlank() ? WidgetListThirdPartyProject.projectName(record) : nextName);
        });
    }

    private void addMaterialActions()
    {
        // The claim amount is controlled by the slider drawn in drawMaterialActions().
    }

    @Override
    protected void drawContents(final GuiContext ctx, final int mouseX, final int mouseY, final float partialTicks)
    {
        updateZonePreview();
        final ThirdPartySyncService service = service();
        if (service != null)
        {
            service.renderStorageZonePreview(ctx);
        }
        drawProjectHeader(ctx);
        drawMaterialRows(ctx);
        drawMaterialActions(ctx);
        drawZones(ctx);
        drawButtons(ctx, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean onMouseClicked(final MouseButtonEvent event, final boolean onCurrentScreen)
    {
        if (isOverClaimSlider((int) event.x(), (int) event.y()))
        {
            final ProjectMaterial material = selectedMaterial();
            pendingClaimMaterialKey = material == null ? "" : material.materialKey;
            claimSliderDragging = true;
            updateClaimDraftFromMouse((int) event.x());
            return true;
        }

        final int row = materialRowAt((int) event.x(), (int) event.y());
        if (row >= 0)
        {
            selectedMaterialIndex = row;
            pendingClaimMaterialKey = "";
            claimDraftAmount = currentClaimAmount(selectedMaterial());
            return true;
        }
        return super.onMouseClicked(event, onCurrentScreen);
    }

    @Override
    public boolean onMouseDragged(final MouseButtonEvent event, final double deltaX, final double deltaY)
    {
        if (claimSliderDragging)
        {
            updateClaimDraftFromMouse((int) event.x());
            return true;
        }
        return super.onMouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean onMouseReleased(final MouseButtonEvent event)
    {
        if (claimSliderDragging)
        {
            claimSliderDragging = false;
            applyClaimDraft();
            return true;
        }
        return super.onMouseReleased(event);
    }

    @Override
    public void removed()
    {
        final ThirdPartySyncService service = service();
        if (service != null)
        {
            service.clearStorageZonePreview(record.getProjectId());
        }
        super.removed();
    }

    private void drawProjectHeader(final GuiContext ctx)
    {
        int x = 10;
        int y = 24;
        RenderUtils.drawOutlinedBox(ctx, x, y, width - 20, 76, 0xA0000000, COLOR_HORIZONTAL_BAR);
        x += 6;
        y += 6;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.placement_info.display_name") + ":", x, y, 0xFFFFFFFF);
        y += 22;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.placement_info.dimension_id") + ": " + record.getProject().dimension
                + "    " + StringUtils.translate("syncmatica.gui.label.placement_info.position") + ": "
                + record.getProject().originX + " " + record.getProject().originY + " " + record.getProject().originZ, x, y, 0xFFC0C0C0);
        y += 12;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.status") + ": " + WidgetListThirdPartyProject.projectStage(record)
                + "    " + StringUtils.translate("syncmatica.gui.label.project_info.updated_at") + ": " + record.getUpdatedAt(), x, y, 0xFFC0C0C0);
        y += 12;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.project_id") + ": " + trim(record.getProjectId(), 56), x, y, 0xFF909090);
    }

    private void drawMaterialRows(final GuiContext ctx)
    {
        final int x = 10;
        int y = materialListY();
        final int listWidth = materialListWidth();
        RenderUtils.drawOutlinedBox(ctx, x, y - 4, listWidth, materialListHeight(), 0xA0000000, COLOR_HORIZONTAL_BAR);
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.materials"), x + 6, y, 0xFFFFFFFF);
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.counts"), x + listWidth - 196, y, 0xFFFFFFFF);
        y += 20;

        final List<ProjectMaterial> materials = sortedMaterials();
        final int rows = visibleMaterialRows();
        for (int i = 0; i < rows; i++)
        {
            final ProjectMaterial material = materials.get(i);
            final int rowY = y + i * 22;
            if (i == selectedMaterialIndex)
            {
                RenderUtils.drawRect(ctx, x + 2, rowY - 2, listWidth - 4, 20, 0x40457B9D);
            }
            if ((i & 1) == 1)
            {
                RenderUtils.drawRect(ctx, x + 2, rowY - 2, listWidth - 4, 20, 0x20101010);
            }

            final ItemStack stack = itemStackFor(material.itemId);
            RenderUtils.drawRect(ctx, x + 6, rowY, 16, 16, 0x20FFFFFF);
            if (!stack.isEmpty())
            {
                ctx.renderItem(stack, x + 6, rowY);
            }
            drawString(ctx, trim(material.displayName, Math.max(12, (listWidth - 296) / 6)), x + 28, rowY + 4,
                    myClaim(material.materialKey) != null ? 0xFF77DD77 : 0xFFFFFFFF);

            final int collected = collectedAmount(material);
            final int reserved = previewReservedAmount(material);
            drawProgress(ctx, x + listWidth - 246, rowY + 3, 42, 12, collected, material.required);
            drawMaterialCounts(ctx, material, collected, reserved, x + listWidth - 196, rowY + 4);
        }
    }

    private void drawMaterialActions(final GuiContext ctx)
    {
        final int panelX = operationPanelX();
        final int panelY = materialListY() - 4;
        RenderUtils.drawOutlinedBox(ctx, panelX, panelY, 166, materialListHeight(), 0xA0000000, COLOR_HORIZONTAL_BAR);
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.claims"), panelX + 10, panelY + 8, 0xFFFFFFFF);

        final ProjectMaterial material = selectedMaterial();
        if (material == null)
        {
            drawString(ctx, "-", panelX + 10, panelY + 30, 0xFFAAAAAA);
            return;
        }

        clampClaimDraftAmount(material);
        final int collected = collectedAmount(material);
        final int reserved = previewReservedAmount(material);
        drawString(ctx, trim(material.displayName, 22), panelX + 10, panelY + 30, 0xFFFFFFFF);
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.missing") + ": "
                + formatCount(Math.max(0, material.required - collected - reserved)), panelX + 10, panelY + 44, 0xFFFFD54F);
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.reserved") + ": "
                + formatCount(reserved), panelX + 10, panelY + 58, 0xFFFFD54F);
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.claim_amount"), panelX + 10, panelY + 72, 0xFFC0C0C0);

        drawClaimSlider(ctx, material, panelX + 10, panelY + 82, 146, 14);
        drawString(ctx, formatCount(claimDraftAmount) + " / " + formatCount(maxClaimAmount(material)), panelX + 10, panelY + 102, 0xFF77DD77);

        int y = panelY + 122;
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.claim_assignees"), panelX + 10, y, 0xFFC0C0C0);
        y += 12;
        final List<String> claimLines = WidgetListThirdPartyProject.claimLines(record, material.materialKey);
        if (claimLines.isEmpty())
        {
            drawString(ctx, "-", panelX + 14, y, 0xFF909090);
        }
        else
        {
            for (int i = 0; i < Math.min(6, claimLines.size()); i++)
            {
                drawString(ctx, trim(claimLines.get(i), 22), panelX + 14, y, 0xFFFFFFFF);
                y += 12;
            }
        }
    }

    private void drawMaterialCounts(final GuiContext ctx, final ProjectMaterial material, final int collected, final int reserved, int x, final int y)
    {
        x = drawCountPart(ctx, formatCount(collected), x, y, collected >= material.required ? 0xFF55FF55 : (collected > 0 ? 0xFFFFD54F : 0xFFFF5555));
        x = drawCountPart(ctx, "/", x, y, 0xFF777777);
        x = drawCountPart(ctx, formatCount(reserved), x, y, reserved > 0 ? 0xFFFFD54F : 0xFF999999);
        x = drawCountPart(ctx, "/", x, y, 0xFF777777);
        drawCountPart(ctx, formatCount(material.required), x, y, 0xFF66CCFF);
    }

    private void drawClaimSlider(final GuiContext ctx, final ProjectMaterial material, final int x, final int y, final int sliderWidth, final int sliderHeight)
    {
        final int max = maxClaimAmount(material);
        final double ratio = max <= 0 ? 0.0D : Math.min(1.0D, Math.max(0.0D, claimDraftAmount / (double) max));
        final int fillWidth = (int) Math.round(sliderWidth * ratio);
        RenderUtils.drawRect(ctx, x, y, sliderWidth, sliderHeight, 0xFF202020);
        if (fillWidth > 0)
        {
            RenderUtils.drawRect(ctx, x, y, fillWidth, sliderHeight, 0xFF4B9F6A);
        }
        final int handleX = x + Math.max(0, fillWidth - 2);
        RenderUtils.drawRect(ctx, handleX, y - 2, 4, sliderHeight + 4, 0xFFE0E0E0);
    }

    private void drawProgress(final GuiContext ctx, final int x, final int y, final int barWidth, final int barHeight, final int collected, final int required)
    {
        RenderUtils.drawRect(ctx, x, y, barWidth, barHeight, 0xFF202020);
        final double ratio = required <= 0 ? 1.0D : Math.min(1.0D, Math.max(0.0D, collected / (double) required));
        final int fillWidth = Math.max(0, (int) Math.round((barWidth - 2) * ratio));
        final int color = ratio >= 1.0D ? 0xFF55AA55 : (ratio > 0.0D ? 0xFFD6A938 : 0xFF773333);
        if (fillWidth > 0)
        {
            RenderUtils.drawRect(ctx, x + 1, y + 1, fillWidth, barHeight - 2, color);
        }
    }

    private int drawCountPart(final GuiContext ctx, final String text, final int x, final int y, final int color)
    {
        drawString(ctx, text, x, y, color);
        return x + getStringWidth(text);
    }

    private static String formatCount(final int count)
    {
        if (count >= 1728)
        {
            return String.format("%d(%.2f box)", count, count / 1728.0D);
        }
        if (count > 64)
        {
            final int stacks = count / 64;
            final int remainder = count % 64;
            return remainder > 0 ? count + "(" + stacks + "x64+" + remainder + ")" : count + "(" + stacks + "x64)";
        }
        return Integer.toString(count);
    }

    private void drawZones(final GuiContext ctx)
    {
        int x = 10;
        int y = height - 56;
        final String cornerA = firstCorner == null ? "-" : posString(firstCorner);
        final String cornerB = secondCorner == null ? "-" : posString(secondCorner);
        final String active = editingCornerA ? "A" : "B";
        drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.zone_editor") + "  "
                + StringUtils.translate("syncmatica.gui.label.project_info.active_corner") + ": " + active
                + "  A: " + cornerA + "  B: " + cornerB, x, y, 0xFFC0C0C0);
        y += 12;
        if (!record.getZones().isEmpty())
        {
            final StorageZone zone = record.getZones().get(record.getZones().size() - 1);
            drawString(ctx, StringUtils.translate("syncmatica.gui.label.project_info.storage_zones") + ": "
                    + zone.dimension + " " + zone.minX + "," + zone.minY + "," + zone.minZ + " -> "
                    + zone.maxX + "," + zone.maxY + "," + zone.maxZ, x, y, 0xFFC0C0C0);
        }
    }

    private void updateZonePreview()
    {
        final ThirdPartySyncService service = service();
        if (service != null)
        {
            service.setStorageZonePreview(record.getProjectId(), currentDimension(), firstCorner, secondCorner);
        }
    }

    private void setCurrentZoneCorner(final BlockPos pos)
    {
        if (editingCornerA)
        {
            firstCorner = pos;
            addMessage(Message.MessageType.SUCCESS, "syncmatica.success.storage_zone_corner_set", "A", posString(firstCorner));
        }
        else
        {
            secondCorner = pos;
            addMessage(Message.MessageType.SUCCESS, "syncmatica.success.storage_zone_corner_set", "B", posString(secondCorner));
        }
        updateZonePreview();
    }

    private String currentCornerButtonLabel()
    {
        return StringUtils.translate("syncmatica.gui.button.zone_active_corner", editingCornerA ? "A" : "B");
    }

    private List<ProjectMaterial> sortedMaterials()
    {
        return record.getMaterials().stream()
                .sorted(Comparator.comparingInt((ProjectMaterial material) ->
                        Math.max(0, material.required - collectedAmount(material))).reversed()
                        .thenComparing(material -> material.displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ProjectMaterial selectedMaterial()
    {
        final List<ProjectMaterial> materials = sortedMaterials();
        if (materials.isEmpty())
        {
            return null;
        }
        selectedMaterialIndex = clampSelectedMaterialIndex(selectedMaterialIndex);
        return materials.get(selectedMaterialIndex);
    }

    private int clampSelectedMaterialIndex(final int index)
    {
        final int size = record.getMaterials().size();
        if (size <= 0)
        {
            return 0;
        }
        return Math.max(0, Math.min(index, size - 1));
    }

    private int defaultClaimAmount(final ProjectMaterial material)
    {
        if (material == null)
        {
            return 1;
        }
        final int missing = Math.max(1, maxClaimAmount(material));
        return Math.min(64, missing);
    }

    private int currentClaimAmount(final ProjectMaterial material)
    {
        return recordClaimAmount(material);
    }

    private int recordClaimAmount(final ProjectMaterial material)
    {
        final MaterialClaim claim = material == null ? null : myClaim(material.materialKey);
        return claim == null ? 0 : claim.targetAmount;
    }

    private int maxClaimAmount(final ProjectMaterial material)
    {
        if (material == null)
        {
            return 0;
        }
        final int collected = collectedAmount(material);
        final int myClaim = recordClaimAmount(material);
        return Math.max(0, material.required - collected - Math.max(0, reservedAmount(material) - myClaim));
    }

    private int reservedAmount(final ProjectMaterial material)
    {
        int reserved = 0;
        for (final MaterialClaim claim : record.getClaims())
        {
            if (claim.materialKey.equals(material.materialKey))
            {
                reserved += claim.targetAmount;
            }
        }
        return Math.max(material.reserved, reserved);
    }

    private int collectedAmount(final ProjectMaterial material)
    {
        if (material == null)
        {
            return 0;
        }
        return Math.max(0, record.getCollected().getOrDefault(material.materialKey, 0));
    }

    private int previewReservedAmount(final ProjectMaterial material)
    {
        if (material == null)
        {
            return 0;
        }
        if (claimSliderDragging && material.materialKey.equals(pendingClaimMaterialKey))
        {
            return Math.max(0, reservedAmount(material) - recordClaimAmount(material) + claimDraftAmount);
        }
        return reservedAmount(material);
    }

    private boolean isOverClaimSlider(final int mouseX, final int mouseY)
    {
        final int x = operationPanelX() + 10;
        final int y = materialListY() - 4 + 82;
        return selectedMaterial() != null && mouseX >= x && mouseX <= x + 146 && mouseY >= y - 4 && mouseY <= y + 18;
    }

    private void updateClaimDraftFromMouse(final int mouseX)
    {
        final ProjectMaterial material = selectedMaterial();
        final int max = maxClaimAmount(material);
        final int x = operationPanelX() + 10;
        final double ratio = Math.min(1.0D, Math.max(0.0D, (mouseX - x) / 146.0D));
        claimDraftAmount = (int) Math.round(max * ratio);
        clampClaimDraftAmount(material);
    }

    private void applyClaimDraft()
    {
        final ProjectMaterial material = selectedMaterial();
        if (material == null)
        {
            return;
        }

        clampClaimDraftAmount(material);
        final int current = recordClaimAmount(material);
        if (claimDraftAmount == current)
        {
            clearClaimPreview();
            return;
        }
        clearClaimPreview();
        if (claimDraftAmount <= 0)
        {
            service().claimMaterial(record.getProjectId(), material.materialKey, 0);
            return;
        }
        service().claimMaterial(record.getProjectId(), material.materialKey, claimDraftAmount);
    }

    private void clampClaimDraftAmount(final ProjectMaterial material)
    {
        if (material == null)
        {
            claimDraftAmount = 0;
            return;
        }
        final int max = maxClaimAmount(material);
        claimDraftAmount = Math.max(0, Math.min(claimDraftAmount, max));
    }

    private void clearClaimPreview()
    {
        pendingClaimMaterialKey = "";
    }

    private int materialRowAt(final int mouseX, final int mouseY)
    {
        final int x = 10;
        final int y = materialListY() + 20;
        final int listWidth = materialListWidth();
        if (mouseX < x || mouseX > x + listWidth || mouseY < y)
        {
            return -1;
        }
        final int row = (mouseY - y) / 22;
        return row >= 0 && row < visibleMaterialRows() && row < sortedMaterials().size() ? row : -1;
    }

    private int materialListY()
    {
        return 110;
    }

    private int materialListWidth()
    {
        return Math.max(220, width - 196);
    }

    private int materialListHeight()
    {
        return Math.max(160, height - 182);
    }

    private int projectNameEditorY()
    {
        return 29;
    }

    private int visibleMaterialRows()
    {
        return Math.max(1, Math.min(12, (materialListHeight() - 28) / 22));
    }

    private int operationPanelX()
    {
        return width - 176;
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
        return context == null ? null : context.getThirdPartySyncService();
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

    private static BlockPos currentTargetBlock()
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.hitResult instanceof BlockHitResult hit))
        {
            return null;
        }
        return hit.getBlockPos();
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
