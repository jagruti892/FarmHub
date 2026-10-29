package in.farmhub.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CartActivity extends Activity {

    private FirebaseFirestore db;
    private FirebaseUser user;
    private LinearLayout itemsContainer;
    private TextView statusText;
    private TextView totalText;
    private EditText deliveryAddressInput;
    private ProgressBar progress;

    private final int backgroundColor = Color.rgb(244, 243, 232);
    private final int cardColor = Color.rgb(255, 254, 249);
    private final int darkGreen = Color.rgb(24, 63, 43);
    private final int green = Color.rgb(39, 90, 53);
    private final int leafGreen = Color.rgb(79, 139, 69);
    private final int borderColor = Color.rgb(220, 229, 216);
    private final int secondaryText = Color.rgb(102, 117, 106);
    private final int primaryText = Color.rgb(32, 49, 40);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        buildScreen();
        loadCart();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(backgroundColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("My Cart");
        title.setTextSize(28);
        title.setTextColor(darkGreen);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Review your selected farm products");
        subtitle.setTextSize(14);
        subtitle.setTextColor(secondaryText);
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle, matchWrap());

        statusText = new TextView(this);
        statusText.setText("Loading your cart...");
        statusText.setTextSize(15);
        statusText.setTextColor(secondaryText);
        statusText.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusText, matchWrap());

        progress = new ProgressBar(this);
        root.addView(progress, matchWrap());

        itemsContainer = new LinearLayout(this);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(itemsContainer, matchWrap());

        totalText = new TextView(this);
        totalText.setTextSize(21);
        totalText.setTextColor(green);
        totalText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        totalText.setPadding(0, dp(20), 0, dp(16));
        root.addView(totalText, matchWrap());

        TextView addressLabel = new TextView(this);
        addressLabel.setText("Delivery Address (required)");
        addressLabel.setTextSize(16);
        addressLabel.setTextColor(darkGreen);
        addressLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addressLabel.setPadding(0, dp(8), 0, dp(6));
        root.addView(addressLabel, matchWrap());

        deliveryAddressInput = new EditText(this);
        deliveryAddressInput.setHint("House no., street, area, city, PIN code");
        deliveryAddressInput.setTextSize(15);
        deliveryAddressInput.setTextColor(primaryText);
        deliveryAddressInput.setHintTextColor(secondaryText);
        deliveryAddressInput.setGravity(Gravity.TOP | Gravity.START);
        deliveryAddressInput.setMinLines(2);
        deliveryAddressInput.setMaxLines(4);
        deliveryAddressInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        deliveryAddressInput.setBackgroundColor(cardColor);
        deliveryAddressInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams addressParams = matchWrap();
        addressParams.bottomMargin = dp(12);
        root.addView(deliveryAddressInput, addressParams);

        Button checkoutButton = new Button(this);
        checkoutButton.setText("Place Order (Cash on Delivery)");
        styleButton(checkoutButton, true);
        checkoutButton.setOnClickListener(v -> placeCodOrder());
        LinearLayout.LayoutParams checkoutParams = matchWrap();
        checkoutParams.topMargin = dp(8);
        checkoutParams.bottomMargin = dp(10);
        root.addView(checkoutButton, checkoutParams);

        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh Cart");
        styleButton(refreshButton, true);
        refreshButton.setOnClickListener(v -> loadCart());
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.topMargin = dp(10);
        refreshParams.bottomMargin = dp(8);
        root.addView(refreshButton, refreshParams);

        Button backButton = new Button(this);
        backButton.setText("Back to Home");
        styleButton(backButton, false);

        LinearLayout.LayoutParams backParams = matchWrap();
        backParams.topMargin = dp(8);
        root.addView(backButton, backParams);

        backButton.setOnClickListener(v -> finish());

        setContentView(scroll);
    }

    private void placeCodOrder() {
        if (user == null) {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_LONG).show();
            return;
        }

        String deliveryAddress = deliveryAddressInput.getText()
                .toString().trim();

        if (deliveryAddress.length() < 10 || deliveryAddress.length() > 500) {
            deliveryAddressInput.setError(
                    "Enter a complete address (10 to 500 characters).");
            deliveryAddressInput.requestFocus();
            Toast.makeText(this,
                    "Please enter a valid delivery address.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        progress.setVisibility(View.VISIBLE);

        db.collection("carts")
                .document(user.getUid())
                .collection("items")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(this, "Your cart is empty.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    java.util.List<java.util.Map<String, Object>> orderItems =
                            new java.util.ArrayList<>();
                    long totalMinor = 0L;

                    for (DocumentSnapshot item : snapshot.getDocuments()) {
                        String name = item.getString("name");
                        Long price = item.getLong("priceMinor");
                        Long quantity = item.getLong("quantity");

                        if (name == null || price == null || quantity == null
                                || price < 0 || quantity < 1
                                || price > Long.MAX_VALUE / quantity) {
                            progress.setVisibility(View.GONE);
                            Toast.makeText(this,
                                    "Invalid cart item. Refresh your cart and try again.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        long subtotal = price * quantity;
                        if (Long.MAX_VALUE - totalMinor < subtotal) {
                            progress.setVisibility(View.GONE);
                            Toast.makeText(this, "Order total is too large.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        totalMinor += subtotal;

                        java.util.Map<String, Object> orderItem =
                                new java.util.HashMap<>();
                        orderItem.put("productId", item.getId());
                        orderItem.put("name", name);
                        orderItem.put("priceMinor", price);
                        orderItem.put("quantity", quantity);
                        orderItem.put("sellerUid", item.getString("sellerUid"));
                        orderItem.put("unitLabel", item.getString("unitLabel"));
                        orderItems.add(orderItem);
                    }

                    java.util.Map<String, Object> order =
                            new java.util.HashMap<>();
                    order.put("customerUid", user.getUid());
                    order.put("deliveryAddress", deliveryAddress);
                    order.put("items", orderItems);
                    order.put("totalMinor", totalMinor);
                    order.put("commissionRateBps", 500L);
                    order.put("commissionMinor", (totalMinor * 5L) / 100L);
                    order.put("currency", "INR");
                    order.put("paymentMethod", "COD");
                    order.put("paymentStatus", "Pending");
                    order.put("status", "Placed");
                    order.put("createdAt",
                            com.google.firebase.firestore.FieldValue.serverTimestamp());

                    db.collection("orders")
                            .add(order)
                            .addOnSuccessListener(orderRef -> {
                                java.util.List<com.google.android.gms.tasks.Task<Void>> deletions =
                                        new java.util.ArrayList<>();
                                for (DocumentSnapshot item : snapshot.getDocuments()) {
                                    deletions.add(item.getReference().delete());
                                }

                                com.google.android.gms.tasks.Tasks.whenAll(deletions)
                                        .addOnSuccessListener(unused -> {
                                            progress.setVisibility(View.GONE);
                                            Toast.makeText(this,
                                                    "Order placed! Payment: Cash on Delivery",
                                                    Toast.LENGTH_LONG).show();
                                            loadCart();
                                            startActivity(new Intent(
                                                    this, ProductOrdersActivity.class));
                                        })
                                        .addOnFailureListener(error -> {
                                            progress.setVisibility(View.GONE);
                                            Toast.makeText(this,
                                                    "Order saved, but cart cleanup failed. Check My Orders before retrying.",
                                                    Toast.LENGTH_LONG).show();
                                        });
                            })
                            .addOnFailureListener(error -> {
                                progress.setVisibility(View.GONE);
                                Toast.makeText(this,
                                        "Could not place order. Check Firestore rules.",
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(error -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Could not load cart.",
                            Toast.LENGTH_LONG).show();
                });
    }
    private void loadCart() {
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Loading your cart...");
        itemsContainer.removeAllViews();
        totalText.setText("Total: " + formatMoney(0));

        db.collection("carts")
                .document(user.getUid())
                .collection("items")
                .get()
                .addOnSuccessListener(this::showCart)
                .addOnFailureListener(error -> {
                    progress.setVisibility(View.GONE);
                    statusText.setText(
                            "Could not load your cart. Check your connection and retry.");

                    Toast.makeText(
                            this,
                            "Could not load cart: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void showCart(QuerySnapshot snapshot) {
        progress.setVisibility(View.GONE);
        itemsContainer.removeAllViews();

        if (snapshot.isEmpty()) {
            statusText.setText("Your cart is empty.");
            totalText.setText("Total: " + formatMoney(0));
            return;
        }

        statusText.setText("Items in your cart: " + snapshot.size());

        long totalMinor = 0L;

        for (DocumentSnapshot item : snapshot.getDocuments()) {
            String name = item.getString("name");

            if (name == null || name.trim().isEmpty()) {
                name = "Unnamed product";
            }

            Long priceValue = item.getLong("priceMinor");
            Long quantityValue = item.getLong("quantity");

            long priceMinor = priceValue == null ? 0L : priceValue;
            long quantity = quantityValue == null ? 0L : quantityValue;
            long subtotal = priceMinor * quantity;

            totalMinor += subtotal;

            addItemCard(item, name, priceMinor, quantity, subtotal);
        }

        totalText.setText("Total: " + formatMoney(totalMinor));
    }

    private void addItemCard(
            DocumentSnapshot item,
            String name,
            long priceMinor,
            long quantity,
            long subtotal) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(createRoundedBackground(
                cardColor, borderColor, 18));

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(16);
        itemsContainer.addView(card, cardParams);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(19);
        nameView.setTextColor(darkGreen);
        nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nameView.setPadding(0, 0, 0, dp(8));
        card.addView(nameView, matchWrap());

        TextView priceView = new TextView(this);
        priceView.setText("Price: " + formatMoney(priceMinor)
                + " per " + safeText(item.getString("unitLabel"), "unit"));
        priceView.setTextSize(15);
        priceView.setTextColor(green);
        card.addView(priceView, matchWrap());

        TextView sellerView = new TextView(this);
        sellerView.setText("Seller: "
                + safeText(item.getString("sellerDisplayName"), "Seller")
                + " | "
                + safeText(item.getString("sellerArea"),
                "Area not specified"));
        sellerView.setTextSize(13);
        sellerView.setTextColor(secondaryText);
        sellerView.setPadding(0, dp(6), 0, dp(12));
        card.addView(sellerView, matchWrap());

        View separator = new View(this);
        separator.setBackgroundColor(borderColor);

        LinearLayout.LayoutParams separatorParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1));

        separatorParams.bottomMargin = dp(12);
        card.addView(separator, separatorParams);

        TextView subtotalView = new TextView(this);
        subtotalView.setText("Subtotal: " + formatMoney(subtotal));
        subtotalView.setTextSize(17);
        subtotalView.setTextColor(green);
        subtotalView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        subtotalView.setPadding(0, 0, 0, dp(12));
        card.addView(subtotalView, matchWrap());

        TextView quantityLabel = new TextView(this);
        quantityLabel.setText("Quantity");
        quantityLabel.setTextSize(14);
        quantityLabel.setTextColor(primaryText);
        quantityLabel.setPadding(0, 0, 0, dp(4));
        card.addView(quantityLabel, matchWrap());

        LinearLayout quantityRow = new LinearLayout(this);
        quantityRow.setOrientation(LinearLayout.HORIZONTAL);
        quantityRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText quantityInput = new EditText(this);
        quantityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantityInput.setSingleLine(true);
        quantityInput.setHint("Quantity");
        quantityInput.setText(String.valueOf(quantity));
        quantityInput.setSelectAllOnFocus(true);
        quantityInput.setTextColor(primaryText);
        quantityInput.setBackground(createRoundedBackground(
                Color.WHITE, borderColor, 12));
        quantityInput.setPadding(dp(12), 0, dp(12), 0);

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(0, dp(50), 1.4f);

        inputParams.rightMargin = dp(10);
        quantityRow.addView(quantityInput, inputParams);

        Button updateButton = new Button(this);
        updateButton.setText("Update");
        styleButton(updateButton, true);
        updateButton.setTextSize(13);

        LinearLayout.LayoutParams updateParams =
                new LinearLayout.LayoutParams(0, dp(50), 1);

        quantityRow.addView(updateButton, updateParams);
        card.addView(quantityRow, matchWrap());

        updateButton.setOnClickListener(v -> {
            String entered = quantityInput.getText().toString().trim();
            int newQuantity;

            try {
                newQuantity = Integer.parseInt(entered);
            } catch (NumberFormatException e) {
                quantityInput.setError("Enter a valid quantity");
                return;
            }

            if (newQuantity < 1) {
                quantityInput.setError("Quantity must be at least 1");
                return;
            }

            if (newQuantity == quantity) {
                Toast.makeText(
                        this,
                        "Quantity is unchanged",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            updateButton.setEnabled(false);
            updateCartQuantity(item, newQuantity, updateButton);
        });

        Button removeButton = new Button(this);
        removeButton.setText("Remove item");
        styleButton(removeButton, false);
        removeButton.setTextSize(14);

        LinearLayout.LayoutParams removeParams = matchWrap();
        removeParams.topMargin = dp(8);
        card.addView(removeButton, removeParams);

        removeButton.setOnClickListener(
                v -> removeCartItem(item, removeButton));
    }

    private void updateCartQuantity(
            DocumentSnapshot cartItem,
            int quantity,
            Button updateButton) {

        String productId = cartItem.getId();

        DocumentReference cartRef = db.collection("carts")
                .document(user.getUid())
                .collection("items")
                .document(productId);

        DocumentReference productRef = db.collection("products")
                .document(productId);

        db.runTransaction(transaction -> {
            DocumentSnapshot currentCart = transaction.get(cartRef);
            DocumentSnapshot product = transaction.get(productRef);

            if (!currentCart.exists() || !product.exists()) {
                throw new IllegalStateException(
                        "Cart item or product no longer exists.");
            }

            Long stockValue = product.getLong("stockQuantity");
            long stock = stockValue == null ? 0L : stockValue;

            if (!"published".equals(
                    product.getString("publicationStatus"))) {
                throw new IllegalStateException(
                        "This product is no longer available.");
            }

            if (quantity > stock) {
                throw new IllegalArgumentException(
                        "Quantity exceeds available stock (" + stock + ").");
            }

            Map<String, Object> updated = new HashMap<>();
            updated.put("name", product.getString("name"));
            updated.put("categoryId", product.getString("categoryId"));
            updated.put("imageUrl",
                    safeText(product.getString("imageUrl"), ""));
            updated.put("priceMinor", product.getLong("priceMinor"));
            updated.put("currency", product.getString("currency"));
            updated.put("unitLabel", product.getString("unitLabel"));
            updated.put("quantity", quantity);
            updated.put("sellerUid", product.getString("sellerUid"));
            updated.put("sellerDisplayName",
                    safeText(product.getString("sellerDisplayName"), ""));
            updated.put("sellerArea",
                    safeText(product.getString("sellerArea"), ""));
            updated.put("updatedAt", FieldValue.serverTimestamp());

            transaction.set(cartRef, updated);
            return null;

        }).addOnSuccessListener(unused -> {
            Toast.makeText(
                    this,
                    "Cart quantity updated",
                    Toast.LENGTH_SHORT
            ).show();

            loadCart();

        }).addOnFailureListener(error -> {
            updateButton.setEnabled(true);

            Toast.makeText(
                    this,
                    "Could not update quantity: " + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private void removeCartItem(
            DocumentSnapshot item,
            Button removeButton) {

        removeButton.setEnabled(false);

        db.collection("carts")
                .document(user.getUid())
                .collection("items")
                .document(item.getId())
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(
                            this,
                            "Item removed from cart",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadCart();
                })
                .addOnFailureListener(error -> {
                    removeButton.setEnabled(true);

                    Toast.makeText(
                            this,
                            "Could not remove item: " + error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private String formatMoney(long minorUnits) {
        NumberFormat format =
                NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        format.setCurrency(
                java.util.Currency.getInstance("INR"));

        return format.format(minorUnits / 100.0);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty()
                ? fallback : value;
    }

    private GradientDrawable createRoundedBackground(
            int fillColor,
            int strokeColor,
            int radiusDp) {

        GradientDrawable background = new GradientDrawable();
        background.setColor(fillColor);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), strokeColor);

        return background;
    }

    private void styleButton(Button button, boolean primary) {
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));

        // Prevent Android's default button tint from overriding our colors.
        button.setBackgroundTintList(null);

        if (primary) {
            // Primary: FarmHub dark green with white text.
            button.setTextColor(Color.WHITE);
            button.setBackground(createRoundedBackground(
                    green, green, 14));
        } else {
            // Secondary: cream background with green text and border.
            button.setTextColor(green);
            button.setBackground(createRoundedBackground(
                    cardColor, borderColor, 14));
        }
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
