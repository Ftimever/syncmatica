package ch.endte.syncmatica.material;

import net.minecraft.core.BlockPos;

public class StorageZone
{
    public String zoneId = "";
    public String projectId = "";
    public String dimension = "";
    public BlockPos minPos = BlockPos.ZERO;
    public BlockPos maxPos = BlockPos.ZERO;
    public boolean enabled = true;
}
