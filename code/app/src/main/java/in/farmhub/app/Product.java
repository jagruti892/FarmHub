package in.farmhub.app;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Currency;
import java.util.Locale;

/** Read-only view of a published product document. */
public final class Product {
    private final String id;
    private final String name;
    private final String categoryId;
    private final String description;
    private final String imageUrl;
    private final long priceMinor;
    private final String currency;
    private final String unitLabel;
    private final long stockQuantity;
    private final String sellerUid;
    private final String sellerDisplayName;
    private final String sellerArea;

    private Product(String id, String name, String categoryId, String description,
                    String imageUrl, long priceMinor, String currency, String unitLabel,
                    long stockQuantity, String sellerUid, String sellerDisplayName,
                    String sellerArea) {
        this.id = id;
        this.name = name;
        this.categoryId = categoryId;
        this.description = description;
        this.imageUrl = imageUrl;
        this.priceMinor = priceMinor;
        this.currency = currency;
        this.unitLabel = unitLabel;
        this.stockQuantity = stockQuantity;
        this.sellerUid = sellerUid;
        this.sellerDisplayName = sellerDisplayName;
        this.sellerArea = sellerArea;
    }

    /** Returns null when a document is unpublished or does not match the required schema. */
    public static Product fromDocument(DocumentSnapshot document) {
        if (!"published".equals(document.getString("publicationStatus"))) {
            return null;
        }

        String name = document.getString("name");
        String categoryId = document.getString("categoryId");
        String currency = document.getString("currency");
        String unitLabel = document.getString("unitLabel");
        String sellerUid = document.getString("sellerUid");
        Long priceMinor = document.getLong("priceMinor");
        Long stockQuantity = document.getLong("stockQuantity");
        String normalizedCurrency = isBlank(currency)
                ? "" : currency.trim().toUpperCase(Locale.ROOT);

        if (isBlank(name) || isBlank(categoryId) || isBlank(normalizedCurrency)
                || isBlank(unitLabel) || isBlank(sellerUid)
                || priceMinor == null || priceMinor < 0
                || stockQuantity == null || stockQuantity < 0) {
            return null;
        }
        try {
            if (Currency.getInstance(normalizedCurrency).getDefaultFractionDigits() < 0) {
                return null;
            }
        } catch (IllegalArgumentException invalidCurrency) {
            return null;
        }

        return new Product(
                document.getId(),
                name.trim(),
                categoryId.trim(),
                valueOrEmpty(document.getString("description")),
                valueOrEmpty(document.getString("imageUrl")),
                priceMinor,
                normalizedCurrency,
                unitLabel.trim(),
                stockQuantity,
                sellerUid,
                valueOrEmpty(document.getString("sellerDisplayName")),
                valueOrEmpty(document.getString("sellerArea")));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    /** Keeps the demo's two unverified seed listings free of an unsubstantiated claim. */
    public String getDisplayName() {
        if ("seeds".equalsIgnoreCase(categoryId)) {
            if ("certified paddy seeds".equalsIgnoreCase(name)) {
                return "Paddy Seeds";
            }
            if ("certified wheat seeds".equalsIgnoreCase(name)) {
                return "Wheat Seeds";
            }
        }
        return name;
    }
    public String getCategoryId() { return categoryId; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public long getPriceMinor() { return priceMinor; }
    public String getCurrency() { return currency; }
    public String getUnitLabel() { return unitLabel; }
    public long getStockQuantity() { return stockQuantity; }
    public String getSellerUid() { return sellerUid; }
    public String getSellerDisplayName() { return sellerDisplayName; }
    public String getSellerArea() { return sellerArea; }
    public boolean isAvailable() { return stockQuantity > 0; }
}
