package dev.froyln.schematicpreview.data;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.item.Item;

import fi.dy.masa.malilib.config.util.ConfigUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import dev.froyln.schematicpreview.Reference;

/**
 * Per-directory custom icons, keyed by absolute path with {@code /} separators, persisted to
 * {@code config/schematicpreview_icons.json}. Unknown item ids are dropped on load; the file
 * is rewritten only after a user change.
 */
public final class DirectoryIconStore
{
    private static final String FILE_NAME = Reference.MOD_ID + "_icons.json";

    private static final Map<String, Entry> ICONS = new HashMap<>();

    private DirectoryIconStore()
    {
    }

    public static void load()
    {
        ICONS.clear();

        JsonElement root = JsonUtils.parseJsonFile(getFile());

        if (root == null || root.isJsonObject() == false)
        {
            return;
        }

        JsonObject icons = JsonUtils.getNestedObject(root.getAsJsonObject(), "icons", false);

        if (icons == null)
        {
            return;
        }

        for (Map.Entry<String, JsonElement> e : icons.entrySet())
        {
            if (e.getValue().isJsonObject() == false)
            {
                continue;
            }

            JsonObject obj = e.getValue().getAsJsonObject();
            String itemId = JsonUtils.getString(obj, "itemId");

            if (itemId == null || Item.getByNameOrId(itemId) == null)
            {
                continue;
            }

            IconPosition position = IconPosition.fromJsonName(JsonUtils.getStringOrDefault(obj, "pos", IconPosition.DEFAULT.jsonName));
            ICONS.put(e.getKey(), new Entry(itemId, position));
        }
    }

    private static void save()
    {
        JsonObject root = new JsonObject();
        JsonObject icons = new JsonObject();

        for (Map.Entry<String, Entry> e : ICONS.entrySet())
        {
            JsonObject obj = new JsonObject();
            obj.addProperty("itemId", e.getValue().itemId);
            obj.addProperty("pos", e.getValue().position.jsonName);
            icons.add(e.getKey(), obj);
        }

        root.add("icons", icons);
        JsonUtils.writeJsonToFile(root, getFile());
    }

    @Nullable
    public static Entry get(Path directory)
    {
        return ICONS.get(keyFor(directory));
    }

    /**
     * @return false if {@code itemId} isn't a registered item - the caller should not treat the
     * icon as saved in that case.
     */
    public static boolean set(Path directory, String itemId, IconPosition position)
    {
        if (Item.getByNameOrId(itemId) == null)
        {
            return false;
        }

        ICONS.put(keyFor(directory), new Entry(itemId, position));
        save();
        return true;
    }

    public static void remove(Path directory)
    {
        if (ICONS.remove(keyFor(directory)) != null)
        {
            save();
        }
    }

    private static String keyFor(Path directory)
    {
        return directory.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static Path getFile()
    {
        return ConfigUtils.getConfigDirectory().resolve(FILE_NAME);
    }

    public static class Entry
    {
        public final String itemId;
        public final IconPosition position;

        public Entry(String itemId, IconPosition position)
        {
            this.itemId = itemId;
            this.position = position;
        }
    }
}
