package in.farmhub.app;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EquipmentRepository {
    public static final int PAGE_SIZE = 25;

    private final FirebaseFirestore firestore;

    public EquipmentRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public interface EquipmentCallback {
        void onLoaded(List<Equipment> equipment, DocumentSnapshot lastDocument,
                boolean hasMore, Exception error);
    }

    public interface EquipmentDetailsCallback {
        void onLoaded(Equipment equipment, Exception error);
    }

    public interface ActionCallback {
        void onComplete(Exception error);
    }

    public void updateDamageCharge(String equipmentId, long damageChargeMinor,
            ActionCallback callback) {
        if (damageChargeMinor < 0) {
            callback.onComplete(
                    new IllegalArgumentException("The amount cannot be negative."));
            return;
        }

        firestore.collection("equipment").document(equipmentId)
                .update("damageChargeMinor", damageChargeMinor,
                        "updatedAt", FieldValue.serverTimestamp())
                .addOnCompleteListener(task -> callback.onComplete(
                        task.isSuccessful() ? null : task.getException()));
    }

    public void updateEquipment(String equipmentId, String name,
            String description, String categoryId,
            String imageUrl, long dailyPriceMinor,
            String location, ActionCallback callback) {
        if (name == null || name.trim().isEmpty()
                || description == null
                || categoryId == null || categoryId.trim().isEmpty()
                || imageUrl == null
                || location == null || location.trim().isEmpty()
                || dailyPriceMinor <= 0) {
            callback.onComplete(
                    new IllegalArgumentException("Please check all equipment details."));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name.trim());
        updates.put("description", description.trim());
        updates.put("categoryId",
                categoryId.trim().toLowerCase(java.util.Locale.ROOT));
        updates.put("imageUrl", imageUrl.trim());
        updates.put("dailyPriceMinor", dailyPriceMinor);
        updates.put("location", location.trim());
        updates.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection("equipment").document(equipmentId)
                .update(updates)
                .addOnCompleteListener(task -> callback.onComplete(
                        task.isSuccessful() ? null : task.getException()));
    }

    public void updateAvailability(String equipmentId, boolean available,
            ActionCallback callback) {
        firestore.collection("equipment").document(equipmentId)
                .update("availabilityStatus",
                        available ? "available" : "unavailable",
                        "updatedAt", FieldValue.serverTimestamp())
                .addOnCompleteListener(task -> callback.onComplete(
                        task.isSuccessful() ? null : task.getException()));
    }

    public void loadPublishedEquipment(String equipmentId,
            EquipmentDetailsCallback callback) {
        firestore.collection("equipment").document(equipmentId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onLoaded(null, task.getException());
                        return;
                    }

                    DocumentSnapshot document = task.getResult();
                    callback.onLoaded(document.exists()
                            ? Equipment.fromDocument(document)
                            : null, null);
                });
    }

    public void loadPublishedEquipment(DocumentSnapshot startAfter,
            EquipmentCallback callback) {
        Query query = firestore.collection("equipment")
                .whereEqualTo("publicationStatus", "published");

        if (startAfter != null) {
            query = query.startAfter(startAfter);
        }

        query.limit(PAGE_SIZE).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onLoaded(new ArrayList<>(), null, false,
                        task.getException());
                return;
            }

            QuerySnapshot result = task.getResult();
            List<Equipment> items = new ArrayList<>();

            for (DocumentSnapshot document : result.getDocuments()) {
                Equipment item = Equipment.fromDocument(document);
                if (item != null) {
                    items.add(item);
                }
            }

            List<DocumentSnapshot> documents = result.getDocuments();
            DocumentSnapshot lastDocument = documents.isEmpty()
                    ? null
                    : documents.get(documents.size() - 1);

            callback.onLoaded(items, lastDocument,
                    documents.size() == PAGE_SIZE, null);
        });
    }

    public void loadOwnerEquipment(String ownerUid, EquipmentCallback callback) {
        firestore.collection("equipment")
                .whereEqualTo("ownerUid", ownerUid)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onLoaded(new ArrayList<>(), null, false,
                                task.getException());
                        return;
                    }

                    QuerySnapshot result = task.getResult();
                    List<Equipment> items = new ArrayList<>();

                    for (DocumentSnapshot document : result.getDocuments()) {
                        Equipment item = Equipment.fromOwnerDocument(document);
                        if (item != null) {
                            items.add(item);
                        }
                    }

                    List<DocumentSnapshot> documents = result.getDocuments();
                    callback.onLoaded(items,
                            documents.isEmpty() ? null
                                    : documents.get(documents.size() - 1),
                            false, null);
                });
    }
}
