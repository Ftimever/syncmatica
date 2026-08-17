package ch.endte.syncmatica.material;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

public class ContainerSnapshot
{
    public String containerId = "";
    public String projectId = "";
    public BlockPos pos = BlockPos.ZERO;
    public String dimension = "";
    public String type = "";
    public String scanState = "";
    public String lastAccessibleAt = "";
    public String lastDeniedAt = "";
    public Map<String, Integer> itemStacks = new LinkedHashMap<>();
}
