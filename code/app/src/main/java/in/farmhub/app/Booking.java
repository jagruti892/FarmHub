package in.farmhub.app;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/** Immutable booking identity and customer-visible agreement/inspection snapshots. */
public final class Booking {
    private final String id;
    private final String equipmentId;
    private final String equipmentName;
    private final String customerUid;
    private final String ownerUid;
    private final Timestamp rentalDay;
    private final Timestamp returnDeadline;
    private final long agreedPriceMinor;
    private final long commissionMinor;
    private final long commissionRateBps;
    private final long agreedDamageChargeMinor;
    private final String currency;
    private final String returnLocation;
    private final boolean agreementAccepted;
    private final Timestamp agreementAcceptedAt;
    private final String termsVersion;
    private final String status;
    private final String returnStatus;
    private final String inspectionCondition;
    private final String damageDescription;
    private final long inspectionChargeMinor;
    private final Timestamp inspectedAt;

    private Booking(String id, String equipmentId, String equipmentName, String customerUid,
                    String ownerUid, Timestamp rentalDay, Timestamp returnDeadline,
                    long agreedPriceMinor, long commissionMinor, long commissionRateBps, long agreedDamageChargeMinor, String currency,
                    String returnLocation, boolean agreementAccepted,
                    Timestamp agreementAcceptedAt, String termsVersion, String status,
                    String returnStatus, String inspectionCondition, String damageDescription,
                    long inspectionChargeMinor, Timestamp inspectedAt) {
        this.id = id;
        this.equipmentId = equipmentId;
        this.equipmentName = equipmentName;
        this.customerUid = customerUid;
        this.ownerUid = ownerUid;
        this.rentalDay = rentalDay;
        this.returnDeadline = returnDeadline;
        this.agreedPriceMinor = agreedPriceMinor;
        this.commissionMinor = commissionMinor;
        this.commissionRateBps = commissionRateBps;
        this.agreedDamageChargeMinor = agreedDamageChargeMinor;
        this.currency = currency;
        this.returnLocation = returnLocation;
        this.agreementAccepted = agreementAccepted;
        this.agreementAcceptedAt = agreementAcceptedAt;
        this.termsVersion = termsVersion;
        this.status = status;
        this.returnStatus = returnStatus;
        this.inspectionCondition = inspectionCondition;
        this.damageDescription = damageDescription;
        this.inspectionChargeMinor = inspectionChargeMinor;
        this.inspectedAt = inspectedAt;
    }

    /** Older booking documents remain readable with safe display defaults. */
    public static Booking fromDocument(DocumentSnapshot document) {
        Timestamp day = document.getTimestamp("rentalDay");
        Long price = document.getLong("agreedPriceMinor");
        String equipmentId = text(document.getString("equipmentId"));
        String customerUid = text(document.getString("customerUid"));
        String ownerUid = text(document.getString("ownerUid"));
        String currency = text(document.getString("currency"));
        String status = text(document.getString("status"));
        if (day == null || price == null || price <= 0 || equipmentId.isEmpty()
                || customerUid.isEmpty() || ownerUid.isEmpty()
                || currency.isEmpty() || status.isEmpty()) {
            return null;
        }
        Long commission = nonNegative(document.getLong("commissionMinor"));
        Long rate = nonNegative(document.getLong("commissionRateBps"));
        Long damageCap = nonNegative(document.getLong("agreedDamageChargeMinor"));
        Long charged = nonNegative(document.getLong("inspectionChargeMinor"));
        Timestamp deadline = document.getTimestamp("returnDeadline");
        Timestamp acceptedAt = document.getTimestamp("agreementAcceptedAt");
        Timestamp inspectedAt = document.getTimestamp("inspectedAt");
        String name = text(document.getString("equipmentName"));
        return new Booking(document.getId(), equipmentId,
                name.isEmpty() ? equipmentId : name, customerUid, ownerUid, day, deadline,
                price, commission, rate, damageCap, currency, text(document.getString("returnLocation")),
                Boolean.TRUE.equals(document.getBoolean("agreementAccepted")), acceptedAt,
                text(document.getString("termsVersion")), status,
                defaultText(document.getString("returnStatus"), "unreturned"),
                defaultText(document.getString("inspectionCondition"), "not_inspected"),
                text(document.getString("damageDescription")), charged, inspectedAt);
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String defaultText(String value, String fallback) {
        String text = text(value);
        return text.isEmpty() ? fallback : text;
    }
    private static Long nonNegative(Long value) { return value == null || value < 0 ? 0L : value; }

    public boolean hasAgreementTerms() {
        return agreementAccepted && agreementAcceptedAt != null
                && returnDeadline != null && !termsVersion.isEmpty();
    }

    public String getId() { return id; }
    public String getEquipmentId() { return equipmentId; }
    public String getEquipmentName() { return equipmentName; }
    public String getCustomerUid() { return customerUid; }
    public String getOwnerUid() { return ownerUid; }
    public Timestamp getRentalDay() { return rentalDay; }
    public Timestamp getReturnDeadline() { return returnDeadline; }
    public long getAgreedPriceMinor() { return agreedPriceMinor; }
    public long getCommissionMinor() { return commissionMinor; }
    public long getCommissionRateBps() { return commissionRateBps; }
    public long getAgreedDamageChargeMinor() { return agreedDamageChargeMinor; }
    public String getCurrency() { return currency; }
    public String getReturnLocation() { return returnLocation; }
    public boolean isAgreementAccepted() { return agreementAccepted; }
    public Timestamp getAgreementAcceptedAt() { return agreementAcceptedAt; }
    public String getTermsVersion() { return termsVersion; }
    public String getStatus() { return status; }
    public String getReturnStatus() { return returnStatus; }
    public String getInspectionCondition() { return inspectionCondition; }
    public String getDamageDescription() { return damageDescription; }
    public long getInspectionChargeMinor() { return inspectionChargeMinor; }
    public Timestamp getInspectedAt() { return inspectedAt; }
}
