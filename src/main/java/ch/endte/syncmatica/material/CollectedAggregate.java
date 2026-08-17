package ch.endte.syncmatica.material;

import java.util.LinkedHashSet;
import java.util.Set;

public class CollectedAggregate
{
    public String projectId = "";
    public String materialKey = "";
    public int collectedAmount = 0;
    public Set<String> sourcesUsed = new LinkedHashSet<>();
    public String computedAt = "";
}
