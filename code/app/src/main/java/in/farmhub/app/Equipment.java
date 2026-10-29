package in.farmhub.app;

import android.net.Uri;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

/** Read-only view of a published catalogue listing or the owner's own listing. */
public final class Equipment {
    private final String id;
    private final String name;
    private final String description;
    private final String ownerUid;
    private final String imageUrl;
    private final long dailyPriceMinor;
    private final long damageChargeMinor;
    private final String location;
    private final String availabilityStatus;
    private final String publicationStatus;

    private Equipment(String id, String name, String description, String ownerUid,
                      String imageUrl, long dailyPriceMinor, long damageChargeMinor, String location,
                      String availabilityStatus, String publicationStatus) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ownerUid = ownerUid;
        this.imageUrl = imageUrl;
        this.dailyPriceMinor = dailyPriceMinor;
        this.damageChargeMinor = damageChargeMinor;
        this.location = location;
        this.availabilityStatus = availabilityStatus;
        this.publicationStatus = publicationStatus;
    }

    /**
     * Returns null for an unpublished or unusable listing. Optional display fields
     * safely fall back to empty values when they are absent or have the wrong type.
     */
    public static Equipment fromDocument(DocumentSnapshot document) {
        return parseDocument(document, false);
    }

    /** Owner view may include their own draft and archived listings. */
    public static Equipment fromOwnerDocument(DocumentSnapshot document) {
        return parseDocument(document, true);
    }

    private static Equipment parseDocument(DocumentSnapshot document, boolean ownerView) {
        Map<String, Object> data = document.getData();
        String publication = data == null ? "" : stringValue(data.get("publicationStatus"));
        if (data == null || (!ownerView && !"published".equals(publication))
                || (ownerView && !("draft".equals(publication)
                || "published".equals(publication) || "archived".equals(publication)))) {
            return null;
        }

        String name = stringValue(data.get("name"));
        String currency = stringValue(data.get("currency"));
        Long priceMinor = integerValue(data.get("dailyPriceMinor"));
        if (name.isEmpty() || priceMinor == null || priceMinor <= 0
                || !"INR".equals(currency)) {
            return null;
        }

        String imageUrl = usableHttpsUrl(stringValue(data.get("imageUrl")))
                ? stringValue(data.get("imageUrl")) : "";
        String location = stringValue(data.get("location"));
        String availability = stringValue(data.get("availabilityStatus"));
        Long damageCharge = integerValue(data.get("damageChargeMinor"));
        if (damageCharge == null || damageCharge < 0) {
            damageCharge = 0L;
        }
        if (!"available".equals(availability) && !"unavailable".equals(availability)) {
            availability = "";
        }

        return new Equipment(document.getId(), name, stringValue(data.get("description")),
                stringValue(data.get("ownerUid")), imageUrl, priceMinor, damageCharge,
                location, availability,
                publication);
    }

    private static String stringValue(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static Long integerValue(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return null;
    }

    private static boolean usableHttpsUrl(String value) {
        if (value.isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(value);
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getOwnerUid() { return ownerUid; }
    public String getImageUrl() { return imageUrl; }
    public long getDailyPriceMinor() { return dailyPriceMinor; }
    public long getDamageChargeMinor() { return damageChargeMinor; }
    public String getLocation() { return location; }
    public String getPublicationStatus() { return publicationStatus; }

    public boolean hasImage() { return !imageUrl.isEmpty(); }
    public boolean isAvailable() { return "available".equals(availabilityStatus); }
    public boolean isUnavailable() { return "unavailable".equals(availabilityStatus); }
}
