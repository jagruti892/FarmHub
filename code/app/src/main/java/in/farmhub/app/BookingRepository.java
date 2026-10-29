package in.farmhub.app;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Firestore operations matching the local single-calendar-day security rules. */
public final class BookingRepository {
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface BookingListCallback {
        void onLoaded(List<Booking> bookings, Exception error);
    }

    public interface ActionCallback {
        void onComplete(String bookingId, Exception error);
    }

    public interface BookingCallback {
        void onLoaded(Booking booking, Exception error);
    }

    /** Creates only the exact pending-request schema; rules revalidate price and ownership. */
    public void createPendingBooking(Equipment acceptedEquipment, Timestamp rentalDay,
                                     FirebaseUser customer, boolean agreementAccepted,
                                     ActionCallback callback) {
        if (!agreementAccepted) {
            callback.onComplete(null, new IllegalArgumentException(
                    "Accept the rental agreement before submitting."));
            return;
        }
        DocumentReference equipmentRef = firestore.collection("equipment")
                .document(acceptedEquipment.getId());
        equipmentRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onComplete(null, task.getException());
                return;
            }
            Equipment equipment = task.getResult().exists()
                    ? Equipment.fromDocument(task.getResult()) : null;
            if (equipment == null || !equipment.isAvailable()
                    || equipment.getOwnerUid().isEmpty()
                    || equipment.getOwnerUid().equals(customer.getUid())
                    || equipment.getLocation().isEmpty()) {
                callback.onComplete(null, new IllegalStateException(
                        "This equipment is unavailable or has no listed return location."));
                return;
            }
            if (!equipment.getName().equals(acceptedEquipment.getName())
                    || !equipment.getLocation().equals(acceptedEquipment.getLocation())
                    || equipment.getDailyPriceMinor() != acceptedEquipment.getDailyPriceMinor()
                    || equipment.getDamageChargeMinor() != acceptedEquipment.getDamageChargeMinor()) {
                callback.onComplete(null, new IllegalStateException(
                        "The listing terms changed. Review the updated agreement before requesting."));
                return;
            }

            Timestamp returnDeadline;
            try {
                returnDeadline = returnDeadlineFor(rentalDay);
            } catch (IllegalArgumentException invalidDate) {
                callback.onComplete(null, invalidDate);
                return;
            }

            DocumentReference bookingRef = firestore.collection("bookings").document();
            Map<String, Object> data = new HashMap<>();
            data.put("equipmentId", equipment.getId());
            data.put("equipmentName", equipment.getName());
            data.put("customerUid", customer.getUid());
            data.put("ownerUid", equipment.getOwnerUid());
            data.put("rentalDay", rentalDay);
            data.put("returnDeadline", returnDeadline);
            data.put("returnLocation", equipment.getLocation());
            data.put("agreedPriceMinor", equipment.getDailyPriceMinor());
            data.put("commissionRateBps", 500L);
            data.put("commissionMinor", (equipment.getDailyPriceMinor() * 5L) / 100L);
            data.put("agreedDamageChargeMinor", equipment.getDamageChargeMinor());
            data.put("currency", "INR");
            data.put("status", "pending");
            data.put("agreementAccepted", true);
            data.put("agreementAcceptedAt", FieldValue.serverTimestamp());
            data.put("termsVersion", "farmhub-rental-v1");
            data.put("returnStatus", "unreturned");
            data.put("inspectionCondition", "not_inspected");
            data.put("damageDescription", "");
            data.put("inspectionChargeMinor", 0L);
            data.put("createdAt", FieldValue.serverTimestamp());
            bookingRef.set(data).addOnCompleteListener(writeTask ->
                    callback.onComplete(writeTask.isSuccessful() ? bookingRef.getId() : null,
                            writeTask.isSuccessful() ? null : writeTask.getException()));
        });
    }

    public void loadBooking(String bookingId, BookingCallback callback) {
        firestore.collection("bookings").document(bookingId).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onLoaded(null, task.getException());
                return;
            }
            DocumentSnapshot snapshot = task.getResult();
            callback.onLoaded(snapshot.exists() ? Booking.fromDocument(snapshot) : null, null);
        });
    }

    /** Saves the owner's latest return inspection; Firestore Rules enforce identity and charge cap. */
    public void recordInspection(String bookingId, String returnStatus,
                                 String inspectionCondition, String damageDescription,
                                 long chargeMinor, ActionCallback callback) {
        if (chargeMinor < 0 || !("returned".equals(returnStatus)
                || "unreturned".equals(returnStatus))) {
            callback.onComplete(null, new IllegalArgumentException("Inspection details are invalid."));
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("returnStatus", returnStatus);
        update.put("inspectionCondition", inspectionCondition);
        update.put("damageDescription", damageDescription == null ? "" : damageDescription.trim());
        update.put("inspectionChargeMinor", chargeMinor);
        update.put("inspectedAt", FieldValue.serverTimestamp());
        firestore.collection("bookings").document(bookingId).update(update)
                .addOnCompleteListener(task -> callback.onComplete(
                        task.isSuccessful() ? bookingId : null,
                        task.isSuccessful() ? null : task.getException()));
    }

    public void loadCustomerBookings(String uid, BookingListCallback callback) {
        loadBookings(firestore.collection("bookings")
                .whereEqualTo("customerUid", uid), callback);
    }

    public void loadOwnerBookings(String uid, BookingListCallback callback) {
        loadBookings(firestore.collection("bookings")
                .whereEqualTo("ownerUid", uid), callback);
    }

    private void loadBookings(Query query, BookingListCallback callback) {
        query.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onLoaded(new ArrayList<>(), task.getException());
                return;
            }
            QuerySnapshot result = task.getResult();
            List<Booking> bookings = new ArrayList<>();
            for (DocumentSnapshot document : result.getDocuments()) {
                Booking booking = Booking.fromDocument(document);
                if (booking != null) {
                    bookings.add(booking);
                }
            }
            bookings.sort((first, second) ->
                    second.getRentalDay().compareTo(first.getRentalDay()));
            callback.onLoaded(bookings, null);
        });
    }

    /** Owner decision and one-day reservation lock are one atomic transaction. */
    public void decideBooking(String bookingId, String ownerUid, boolean approve,
                              ActionCallback callback) {
        DocumentReference bookingRef = firestore.collection("bookings").document(bookingId);
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot booking = transaction.get(bookingRef);
            if (!booking.exists() || !"pending".equals(booking.getString("status"))) {
                throw new IllegalStateException("Only a pending request can be decided.");
            }
            if (!ownerUid.equals(booking.getString("ownerUid"))) {
                throw new IllegalStateException("This request belongs to another owner.");
            }

            String equipmentId = booking.getString("equipmentId");
            Timestamp rentalDay = booking.getTimestamp("rentalDay");
            if (equipmentId == null || rentalDay == null) {
                throw new IllegalStateException("The booking record is incomplete.");
            }
            DocumentReference equipmentRef = firestore.collection("equipment")
                    .document(equipmentId);
            DocumentReference approvalRef = firestore.collection("ownerApprovals")
                    .document(ownerUid);
            DocumentSnapshot equipment = transaction.get(equipmentRef);
            DocumentSnapshot approval = transaction.get(approvalRef);
            if (!equipment.exists() || !ownerUid.equals(equipment.getString("ownerUid"))
                    || !approval.exists() || !"approved".equals(approval.getString("status"))) {
                throw new IllegalStateException("Owner approval or equipment ownership could not be verified.");
            }

            String dayKey = indiaDayKey(rentalDay);
            DocumentReference lockRef = equipmentRef.collection("rentalDays").document(dayKey);
            DocumentSnapshot lock = transaction.get(lockRef);
            if (approve && lock.exists()) {
                throw new IllegalStateException("That equipment is already reserved for this day.");
            }

            Map<String, Object> update = new HashMap<>();
            update.put("status", approve ? "approved" : "rejected");
            update.put("reviewedAt", FieldValue.serverTimestamp());
            transaction.update(bookingRef, update);
            if (approve) {
                Map<String, Object> reservation = new HashMap<>();
                reservation.put("bookingId", bookingId);
                reservation.put("rentalDay", rentalDay);
                reservation.put("createdAt", FieldValue.serverTimestamp());
                transaction.set(lockRef, reservation);
            }
            return null;
        }).addOnCompleteListener(task ->
                callback.onComplete(task.isSuccessful() ? bookingId : null,
                        task.isSuccessful() ? null : task.getException()));
    }

    private static String indiaDayKey(Timestamp timestamp) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"), Locale.US);
        calendar.setTime(timestamp.toDate());
        return String.format(Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private static Timestamp returnDeadlineFor(Timestamp rentalDay) {
        Calendar india = Calendar.getInstance(
                TimeZone.getTimeZone("Asia/Kolkata"), Locale.US);
        india.setTime(rentalDay.toDate());

        if (india.get(Calendar.HOUR_OF_DAY) != 0
                || india.get(Calendar.MINUTE) != 0
                || india.get(Calendar.SECOND) != 0
                || india.get(Calendar.MILLISECOND) != 0) {
            throw new IllegalArgumentException(
                    "Choose midnight at the start of a calendar day.");
        }

        // Return deadline: the next calendar day at 5:00 PM IST.
        india.add(Calendar.DAY_OF_MONTH, 1);
        india.set(Calendar.HOUR_OF_DAY, 17);
        india.set(Calendar.MINUTE, 0);
        india.set(Calendar.SECOND, 0);
        india.set(Calendar.MILLISECOND, 0);

        return new Timestamp(india.getTime());
    }
}

