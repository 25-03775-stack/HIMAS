package himas;

public final class HeatData {
    private HeatData() {
    }

    public static final String[] LEVEL_NAMES = {
        "LOW", "MODERATE", "HIGH", "CRITICAL"
    };

    public static final String[] LEVEL_COLORS = {
        "#2E7D32", "#B26A00", "#D84315", "#B71C1C"
    };

    public static final String[] LEVEL_BG = {
        "#E8F5E9", "#FFF8E1", "#FFF3E0", "#FFEBEE"
    };

    public static final int[][] STATUS = {
        {0, 1, 2, 2},
        {0, 1, 2, 2},
        {0, 1, 2, 2},
        {0, 1, 2, 2},
        {0, 0, 0, 0},
        {0, 0, 0, 0}
    };
}