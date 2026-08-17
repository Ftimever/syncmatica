package ch.endte.syncmatica.thirdparty;

import com.google.gson.JsonObject;

final class ThirdPartyJson
{
    private ThirdPartyJson()
    {
    }

    static String string(final JsonObject obj, final String key)
    {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    static int integer(final JsonObject obj, final String key)
    {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : 0;
    }

    static boolean bool(final JsonObject obj, final String key, final boolean fallback)
    {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : fallback;
    }
}
