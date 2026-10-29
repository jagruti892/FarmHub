package in.farmhub.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProductDetailsActivity extends Activity {
    private FirebaseAuth firebaseAuth;
    private FirebaseUser currentUser;
    private ProductRepository productRepository;
    private ProductImageLoader imageLoader;
    private FirebaseFirestore firestore;
    private String productId;
    private Product loadedProduct;
    private int selectedQuantity = 1;

    private ProgressBar progressBar;
    private TextView stateMessage;
    private Button retryButton;
    private ImageView productImage;
    private TextView productName;
    private TextView productCategory;
    private TextView productPrice;
    private TextView productAvailability;
    private TextView productDescription;
    private TextView sellerInformation;
    private TextView quantityText;
    private Button quantityMinusButton;
    private Button quantityPlusButton;
    private Button addToCartButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_details);

        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            returnToLogin();
            return;
        }

        productId = getIntent().getStringExtra(
                ProductCatalogActivity.EXTRA_PRODUCT_ID);

        productRepository = new ProductRepository();
        imageLoader = new ProductImageLoader();
        firestore = FirebaseFirestore.getInstance();

        bindViews();

        findViewById(R.id.product_details_back_button)
                .setOnClickListener(view -> finish());

        retryButton.setOnClickListener(view -> verifyCustomerAndLoad());
        quantityMinusButton.setOnClickListener(view -> changeQuantity(-1));
        quantityPlusButton.setOnClickListener(view -> changeQuantity(1));
        addToCartButton.setOnClickListener(view -> addToCart());

        verifyCustomerAndLoad();
    }

    private void bindViews() {
        progressBar = findViewById(R.id.product_details_progress);
        stateMessage = findViewById(R.id.product_details_state_message);
        retryButton = findViewById(R.id.product_details_retry_button);
        productImage = findViewById(R.id.product_details_image);
        productName = findViewById(R.id.product_details_name);
        productCategory = findViewById(R.id.product_details_category);
        productPrice = findViewById(R.id.product_details_price);
        productAvailability = findViewById(R.id.product_details_availability);
        productDescription = findViewById(R.id.product_details_description);
        sellerInformation = findViewById(R.id.product_details_seller);

        quantityText = findViewById(R.id.product_details_quantity);
        quantityMinusButton = findViewById(
                R.id.product_details_quantity_minus);
        quantityPlusButton = findViewById(
                R.id.product_details_quantity_plus);
        addToCartButton = findViewById(
                R.id.product_details_add_to_cart);
    }

    private void verifyCustomerAndLoad() {
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            returnToLogin();
            return;
        }

        if (productId == null || productId.trim().isEmpty()) {
            showUnavailable();
            return;
        }

        showLoading(getString(R.string.products_checking_access));

        RoleAccess.loadRole(this, currentUser, (role, error) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (error != null) {
                showError(getString(R.string.products_access_error));
                return;
            }

            if (role != RoleAccess.Role.CUSTOMER) {
                showError(getString(R.string.products_customer_only));
                retryButton.setVisibility(View.GONE);
                return;
            }

            loadProduct();
        });
    }

    private void loadProduct() {
        showLoading(getString(R.string.product_details_loading));

        productRepository.loadPublishedProduct(productId, (product, error) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            progressBar.setVisibility(View.GONE);

            if (error != null) {
                if (error instanceof FirebaseFirestoreException
                        && ((FirebaseFirestoreException) error)
                                .getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    showError(getString(R.string.product_details_access_denied));
                    return;
                }

                showError(getString(R.string.product_details_load_error));
                return;
            }

            if (product == null) {
                showUnavailable();
                return;
            }

            loadedProduct = product;
            selectedQuantity = 1;
            showProduct(product);
        });
    }

    private void showProduct(Product product) {
        stateMessage.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);

        View content = findViewById(R.id.product_details_content);
        content.setVisibility(View.VISIBLE);

        productName.setText(product.getDisplayName());
        productCategory.setText(formatCategory(product.getCategoryId()));

        productPrice.setText(getString(
                R.string.product_price_with_unit,
                formatPrice(product),
                product.getUnitLabel()));

        productAvailability.setText(product.isAvailable()
                ? R.string.product_in_stock
                : R.string.product_out_of_stock);

        productAvailability.setTextColor(getColor(product.isAvailable()
                ? R.color.leaf_green_dark
                : R.color.error_red));

        productDescription.setText(product.getDescription().isEmpty()
                ? getString(R.string.product_description_unavailable)
                : product.getDescription());

        String sellerName = product.getSellerDisplayName();
        if (sellerName.isEmpty()) {
            sellerName = getString(R.string.product_seller_unavailable);
        }

        String sellerArea = product.getSellerArea().trim();
        sellerArea = sellerArea.replaceFirst("(?i)^A\\.\\s*", "");
        sellerArea = sellerArea.replaceFirst("(?i),\\s*India\\s*$", "").trim();

        sellerInformation.setText(sellerArea.isEmpty()
                ? getString(R.string.product_seller_name_only, sellerName)
                : getString(R.string.product_seller_name_and_area,
                        sellerName, sellerArea));

        android.util.Log.d("FarmHubLocation",
                "Original sellerArea=[" + sellerArea + "]");

        sellerArea = sellerArea.replaceFirst("(?i)^A\\.\\s*", "");
        sellerArea = sellerArea.replaceFirst("(?i),\\s*India\\s*$", "").trim();

        android.util.Log.d("FarmHubLocation",
                "Formatted sellerArea=[" + sellerArea + "]");

        // Remove a leading "A. " and a trailing ", India" for display only.
        sellerArea = sellerArea.replaceFirst("(?i)^A\\.\\s*", "");
        sellerArea = sellerArea.replaceFirst("(?i),\\s*India\\s*$", "").trim();

        sellerInformation.setText(sellerArea.isEmpty()
                ? getString(R.string.product_seller_name_only, sellerName)
                : getString(R.string.product_seller_name_and_area,
                        sellerName, sellerArea));

        productImage.setContentDescription(getString(
                R.string.product_image_description,
                product.getDisplayName()));

        imageLoader.loadForProduct(
                product.getImageUrl(),
                product.getDisplayName(),
                productImage,
                null);

        quantityText.setText(String.valueOf(selectedQuantity));

        boolean available = product.getStockQuantity() > 0;
        quantityMinusButton.setEnabled(false);
        quantityPlusButton.setEnabled(available
                && selectedQuantity < product.getStockQuantity());
        addToCartButton.setEnabled(available);
    }

    private void changeQuantity(int change) {
        if (loadedProduct == null) {
            return;
        }

        long stock = loadedProduct.getStockQuantity();
        long next = (long) selectedQuantity + change;

        if (next < 1 || next > stock || next > Integer.MAX_VALUE) {
            return;
        }

        selectedQuantity = (int) next;
        quantityText.setText(String.valueOf(selectedQuantity));

        quantityMinusButton.setEnabled(selectedQuantity > 1);
        quantityPlusButton.setEnabled(
                selectedQuantity < stock
                        && selectedQuantity < Integer.MAX_VALUE);
    }

    private void addToCart() {
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            returnToLogin();
            return;
        }

        if (loadedProduct == null || selectedQuantity < 1) {
            Toast.makeText(this,
                    getString(R.string.cart_quantity_invalid),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (loadedProduct.getStockQuantity() < selectedQuantity) {
            Toast.makeText(this,
                    getString(R.string.cart_stock_limit),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final String uid = currentUser.getUid();
        final Product product = loadedProduct;
        final int amountToAdd = selectedQuantity;

        DocumentReference cartItem = firestore
                .collection("carts")
                .document(uid)
                .collection("items")
                .document(product.getId());

        addToCartButton.setEnabled(false);
        addToCartButton.setText(R.string.cart_loading);

        firestore.runTransaction(transaction -> {
            DocumentSnapshot existing = transaction.get(cartItem);

            long existingQuantity = 0;
            if (existing.exists()) {
                Long savedQuantity = existing.getLong("quantity");
                if (savedQuantity == null || savedQuantity < 0) {
                    throw new FirebaseFirestoreException(
                            "The saved cart quantity is invalid.",
                            FirebaseFirestoreException.Code.INVALID_ARGUMENT);
                }
                existingQuantity = savedQuantity;
            }

            long newQuantity = existingQuantity + amountToAdd;

            if (newQuantity > product.getStockQuantity()
                    || newQuantity > Integer.MAX_VALUE) {
                throw new FirebaseFirestoreException(
                        getString(R.string.cart_stock_limit),
                        FirebaseFirestoreException.Code.INVALID_ARGUMENT);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("name", product.getName());
            data.put("categoryId", product.getCategoryId());
            data.put("imageUrl", product.getImageUrl());
            data.put("priceMinor", product.getPriceMinor());
            data.put("currency", product.getCurrency());
            data.put("unitLabel", product.getUnitLabel());
            data.put("quantity", (int) newQuantity);
            data.put("sellerUid", product.getSellerUid());
            data.put("sellerDisplayName", product.getSellerDisplayName());
            data.put("sellerArea", product.getSellerArea());
            data.put("updatedAt", FieldValue.serverTimestamp());

            transaction.set(cartItem, data);
            return null;
        }).addOnCompleteListener(task -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            addToCartButton.setText(R.string.cart_add_button);
            addToCartButton.setEnabled(
                    loadedProduct != null
                            && loadedProduct.getStockQuantity() > 0);

            if (task.isSuccessful()) {
                Toast.makeText(this,
                        getString(R.string.cart_added_success),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Exception error = task.getException();
            String message = getString(R.string.cart_add_error);

            if (error instanceof FirebaseFirestoreException
                    && error.getMessage() != null
                    && error.getMessage().contains("stock")) {
                message = getString(R.string.cart_stock_limit);
            } else if (error instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) error)
                            .getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                message = getString(R.string.cart_add_error);
            }

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void showLoading(String message) {
        progressBar.setVisibility(View.VISIBLE);
        stateMessage.setText(message);
        stateMessage.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        findViewById(R.id.product_details_content)
                .setVisibility(View.GONE);
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        stateMessage.setText(message);
        stateMessage.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        findViewById(R.id.product_details_content)
                .setVisibility(View.GONE);
    }

    private void showUnavailable() {
        progressBar.setVisibility(View.GONE);
        stateMessage.setText(R.string.product_details_unavailable);
        stateMessage.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        findViewById(R.id.product_details_content)
                .setVisibility(View.GONE);
    }

    private String formatPrice(Product product) {
        try {
            Currency currency = Currency.getInstance(product.getCurrency());
            int digits = Math.max(0, currency.getDefaultFractionDigits());

            BigDecimal amount = BigDecimal.valueOf(
                    product.getPriceMinor(), digits);

            NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.getDefault());

            formatter.setCurrency(currency);
            formatter.setMinimumFractionDigits(digits);
            formatter.setMaximumFractionDigits(digits);

            return formatter.format(amount);
        } catch (IllegalArgumentException error) {
            return product.getCurrency() + " " + product.getPriceMinor();
        }
    }

    private static String formatCategory(String categoryId) {
        String label = categoryId.replace('_', ' ')
                .replace('-', ' ')
                .trim();

        if (label.isEmpty()) {
            return label;
        }

        return label.substring(0, 1)
                .toUpperCase(Locale.getDefault()) + label.substring(1);
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (imageLoader != null) {
            imageLoader.close();
        }
        super.onDestroy();
    }
}
