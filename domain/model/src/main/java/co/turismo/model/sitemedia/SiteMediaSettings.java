package co.turismo.model.sitemedia;

public record SiteMediaSettings(
        boolean enabled,
        String keyPrefix,
        long maxImageBytes,
        long maxVideoBytes,
        long maxModelBytes,
        int maxImageWidth,
        int maxImageHeight,
        int maxFilesPerSite
) {
    public SiteMediaSettings {
        keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "sites" : keyPrefix.trim().replaceAll("^/+|/+$", "");
        maxImageBytes = positive(maxImageBytes, 10L * 1024 * 1024);
        maxVideoBytes = positive(maxVideoBytes, 100L * 1024 * 1024);
        maxModelBytes = positive(maxModelBytes, 25L * 1024 * 1024);
        maxImageWidth = positive(maxImageWidth, 2400);
        maxImageHeight = positive(maxImageHeight, 2400);
        maxFilesPerSite = positive(maxFilesPerSite, 50);
    }

    private static long positive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
