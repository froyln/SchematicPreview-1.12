package dev.froyln.schematicpreview.data;

/**
 * Where a custom directory icon (see {@link DirectoryIconStore}) renders relative to the
 * directory's own first-schematic preview.
 */
public enum IconPosition
{
    DEFAULT("default"),
    CENTER("center"),
    DEFAULT_WITH_SCHEMATIC("default_with_schematic");

    public final String jsonName;

    IconPosition(String jsonName)
    {
        this.jsonName = jsonName;
    }

    public IconPosition next()
    {
        IconPosition[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static IconPosition fromJsonName(String jsonName)
    {
        for (IconPosition position : values())
        {
            if (position.jsonName.equals(jsonName))
            {
                return position;
            }
        }

        return DEFAULT;
    }
}
