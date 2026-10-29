package in.farmhub.app;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only Firestore access for published products. */
public final class ProductRepository {
    public static final int PAGE_SIZE = 25;

    private final FirebaseFirestore firestore;

    public ProductRepository() {
        firestore = FirebaseFirestore.getInstance();
    }

    public interface ProductsCallback {
        void onLoaded(List<Product> products, DocumentSnapshot lastDocument,
                      boolean hasMore, Exception error);
    }

    public interface ProductCallback {
        void onLoaded(Product product, Exception error);
    }

    /** Loads one bounded page. Every list query includes the published rule condition. */
    public void loadPublishedProducts(String categoryId, String searchText,
                                      DocumentSnapshot startAfter,
                                      ProductsCallback callback) {
        Query query = firestore.collection("products")
                .whereEqualTo("publicationStatus", "published");

        if (categoryId != null && !categoryId.isEmpty()) {
            query = query.whereEqualTo("categoryId", categoryId);
        }

        String prefix = normalizeSearch(searchText);
        query = query.orderBy("nameSearch");
        if (!prefix.isEmpty()) {
            query = query.startAt(prefix).endAt(prefix + "\uf8ff");
        }
        if (startAfter != null) {
            query = query.startAfter(startAfter);
        }

        query.limit(PAGE_SIZE).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                callback.onLoaded(new ArrayList<>(), null, false, task.getException());
                return;
            }

            QuerySnapshot result = task.getResult();
            List<Product> products = new ArrayList<>();
            for (DocumentSnapshot document : result.getDocuments()) {
                Product product = Product.fromDocument(document);
                if (product != null) {
                    products.add(product);
                }
            }

            List<DocumentSnapshot> documents = result.getDocuments();
            DocumentSnapshot lastDocument = documents.isEmpty()
                    ? null : documents.get(documents.size() - 1);
            callback.onLoaded(products, lastDocument,
                    documents.size() == PAGE_SIZE, null);
        });
    }

    /** Loads one published product; missing, unpublished, or malformed documents return null. */
    public void loadPublishedProduct(String productId, ProductCallback callback) {
        firestore.collection("products").document(productId).get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        callback.onLoaded(null, task.getException());
                        return;
                    }

                    DocumentSnapshot document = task.getResult();
                    Product product = document.exists()
                            ? Product.fromDocument(document) : null;
                    callback.onLoaded(product, null);
                });
    }

    private static String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
