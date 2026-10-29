package in.farmhub.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SellerProductsActivity extends Activity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseUser user;
    private LinearLayout list;
    private TextView message;
    private Button addButton;
    private boolean accessVerified = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(0xFFFFF8ED);

        TextView title = new TextView(this);
        title.setText("Manage Products");
        title.setTextSize(26);
        title.setTextColor(0xFF245C38);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Manage the agricultural products you sell.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF555555);
        root.addView(subtitle);

        addButton = new Button(this);
        addButton.setText("Add Product");
        addButton.setEnabled(false);
        addButton.setOnClickListener(v -> showProductForm(null));
        root.addView(addButton);

        message = new TextView(this);
        message.setPadding(0, dp(12), 0, dp(12));
        root.addView(message);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button back = new Button(this);
        back.setText("Back");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        setContentView(root);

        RoleAccess.loadRole(this, user, (role, error) -> {
            if (isFinishing()) return;

            if (error != null || role != RoleAccess.Role.OWNER) {
                message.setText("Owner access could not be verified.");
                return;
            }

            accessVerified = true;
            addButton.setEnabled(true);
            loadProducts();
        });
    }

    private void loadProducts() {
        if (!accessVerified || user == null) return;

        message.setText("Loading products...");
        list.removeAllViews();

        db.collection("products")
                .whereEqualTo("sellerUid", user.getUid())
                .limit(25)
                .get()
                .addOnCompleteListener(task -> {
                    if (isFinishing()) return;

                    if (!task.isSuccessful()) {
                        message.setText("Products could not be loaded. Check Firestore permissions and connection.");
                        return;
                    }

                    List<DocumentSnapshot> docs =
                            new ArrayList<>(task.getResult().getDocuments());

                    Collections.sort(docs, Comparator.comparing(d -> {
                        String name = d.getString("name");
                        return name == null ? "" : name.toLowerCase(Locale.ROOT);
                    }));

                    if (docs.isEmpty()) {
                        message.setText("No products listed yet.");
                        return;
                    }

                    message.setText("Your products: " + docs.size());
                    for (DocumentSnapshot doc : docs) {
                        addProductRow(doc);
                    }
                });
    }

    private void addProductRow(DocumentSnapshot doc) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(0xFFFFFFFF);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        card.setLayoutParams(params);

        TextView heading = new TextView(this);
        heading.setText(doc.getString("name") == null ? "" : doc.getString("name"));
        heading.setTextSize(18);
        heading.setTextColor(0xFF245C38);
        heading.setTypeface(null, 1);
        card.addView(heading);

        Long price = doc.getLong("priceMinor");
        Long stock = doc.getLong("stockQuantity");
        String category = doc.getString("categoryId");
        String unit = doc.getString("unitLabel");
        String status = doc.getString("publicationStatus");

        TextView details = new TextView(this);
        details.setText(
                (category == null ? "" : category)
                + "\nPrice: ₹"
                + String.format(Locale.ROOT, "%.2f",
                    (price == null ? 0L : price) / 100.0)
                + (unit == null || unit.isEmpty() ? "" : " / " + unit)
                + "\nStock: " + (stock == null ? 0L : stock)
                + "\nStatus: " + (status == null ? "" : status));
        card.addView(details);

        Button edit = new Button(this);
        edit.setText("Edit Product");
        edit.setOnClickListener(v -> showProductForm(doc));
        card.addView(edit);

        list.addView(card);
    }

    private void showProductForm(DocumentSnapshot existing) {
        if (!accessVerified || user == null) return;

        boolean editing = existing != null;
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(8), dp(4), dp(8), dp(4));

        EditText name = field(fields, "Product name",
                editing ? existing.getString("name") : "",
                InputType.TYPE_CLASS_TEXT);
        EditText category = field(fields, "Category ID",
                editing ? existing.getString("categoryId") : "",
                InputType.TYPE_CLASS_TEXT);
        EditText description = field(fields, "Description",
                editing ? existing.getString("description") : "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        EditText imageUrl = field(fields, "Image URL",
                editing ? existing.getString("imageUrl") : "",
                InputType.TYPE_CLASS_TEXT);
        EditText price = field(fields, "Price in rupees",
                editing ? formatPrice(existing.getLong("priceMinor")) : "",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText unit = field(fields, "Unit",
                editing ? existing.getString("unitLabel") : "",
                InputType.TYPE_CLASS_TEXT);
        EditText stock = field(fields, "Available quantity",
                editing ? String.valueOf(valueLong(existing, "stockQuantity")) : "",
                InputType.TYPE_CLASS_NUMBER);
        EditText area = field(fields, "Selling area",
                editing ? existing.getString("sellerArea") : "",
                InputType.TYPE_CLASS_TEXT);
        EditText sellerName = field(fields, "Seller name",
                editing ? existing.getString("sellerDisplayName")
                        : (user.getDisplayName() == null ? "" : user.getDisplayName().trim()),
                InputType.TYPE_CLASS_TEXT);

        CheckBox published = new CheckBox(this);
        published.setText("Publish product");
        published.setChecked(!editing ||
                "published".equals(existing.getString("publicationStatus")));
        fields.addView(published);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit Product" : "Add Product")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String productName = value(name);
                String categoryId = value(category).toLowerCase(Locale.ROOT);
                String priceText = value(price);
                String stockText = value(stock);

                if (productName.isEmpty() || categoryId.isEmpty()
                        || value(unit).isEmpty() || value(area).isEmpty()
                        || value(sellerName).isEmpty()
                        || priceText.isEmpty() || stockText.isEmpty()) {
                    toast("Complete all required fields.");
                    return;
                }

                long priceMinor;
                long stockQuantity;
                try {
                    if (!priceText.matches("\\d+(\\.\\d{1,2})?") ||
                            !stockText.matches("\\d+")) {
                        throw new NumberFormatException();
                    }

                    priceMinor = Math.round(
                            Double.parseDouble(priceText) * 100.0);
                    stockQuantity = Long.parseLong(stockText);

                    if (priceMinor < 0 || stockQuantity < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    toast("Enter a valid price and non-negative whole-number stock.");
                    return;
                }

                Map<String, Object> data = new HashMap<>();
                data.put("name", productName);
                data.put("nameSearch", productName.toLowerCase(Locale.ROOT));
                data.put("categoryId", categoryId);
                data.put("description", value(description));
                data.put("imageUrl", value(imageUrl));
                data.put("priceMinor", priceMinor);
                data.put("currency", "INR");
                data.put("unitLabel", value(unit));
                data.put("stockQuantity", stockQuantity);
                data.put("sellerUid", user.getUid());
                data.put("sellerDisplayName", value(sellerName));
                data.put("sellerArea", value(area));
                data.put("publicationStatus",
                        published.isChecked() ? "published" : "draft");
                data.put("updatedAt", FieldValue.serverTimestamp());

                if (editing) {
                    if (!user.getUid().equals(existing.getString("sellerUid"))) {
                        toast("You can only edit your own products.");
                        return;
                    }

                    db.collection("products").document(existing.getId())
                            .update(data)
                            .addOnSuccessListener(unused -> {
                                dialog.dismiss();
                                toast("Product updated.");
                                loadProducts();
                            })
                            .addOnFailureListener(e ->
                                toast("Update failed: " + e.getMessage()));
                } else {
                    data.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("products").add(data)
                            .addOnSuccessListener(ref -> {
                                dialog.dismiss();
                                toast("Product saved.");
                                loadProducts();
                            })
                            .addOnFailureListener(e ->
                                toast("Create failed: " + e.getMessage()));
                }
            }));

        dialog.show();
    }

    private EditText field(LinearLayout parent, String hint,
                           String initial, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(initial == null ? "" : initial);
        edit.setInputType(inputType);
        parent.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }

    private String value(EditText field) {
        return field.getText().toString().trim();
    }

    private long valueLong(DocumentSnapshot doc, String key) {
        Long number = doc.getLong(key);
        return number == null ? 0L : number;
    }

    private String formatPrice(Long minor) {
        return String.format(Locale.ROOT, "%.2f",
                (minor == null ? 0L : minor) / 100.0);
    }

    private int dp(int value) {
        return Math.round(value * getResources()
                .getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
