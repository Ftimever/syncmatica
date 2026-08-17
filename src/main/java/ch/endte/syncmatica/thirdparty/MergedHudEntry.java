package ch.endte.syncmatica.thirdparty;

import java.util.ArrayList;
import java.util.List;

public class MergedHudEntry
{
    public String materialKey = "";
    public String itemId = "";
    public String displayName = "";
    public int required = 0;
    public int collected = 0;
    public int reserved = 0;
    public int missing = 0;
    public boolean claimedByMe = false;
    public final List<String> projectNames = new ArrayList<>();
}
