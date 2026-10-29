package in.farmhub.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;

/** Read-only catalogue for signed-in Customer accounts. */
public class ProductCatalogActivity extends Activity {
    public static final String EXTRA_PRODUCT_ID = "in.farmhub.app.extra.PRODUCT_ID";

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final List<Product> loadedProducts = new ArrayList<>();
    private final List<Product> products = new ArrayList<>();
    private final List<String> categoryIds = new ArrayList<>();

    private FirebaseAuth firebaseAuth;
    private FirebaseUser currentUser;
    private ProductRepository productRepository;
    private ProductImageLoader imageLoader;
    private ProductAdapter productAdapter;
    private DocumentSnapshot lastDocument;
    private String selectedCategory = "";
    private int requestNumber;
    private boolean loading;
    private boolean suppressCategorySelection;
    private boolean customerVerified;

    private EditText searchInput;
    private Spinner categorySpinner;
    private ProgressBar progressBar;
    private TextView stateMessage;
    private ListView productList;
    private Button retryButton;
    private Button loadMoreButton;
    private final Runnable searchRunnable = () -> loadProducts(true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_catalog);

        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            returnToLogin();
            return;
        }

        productRepository = new ProductRepository();
        imageLoader = new ProductImageLoader();
        bindViews();
        configureCategoryFilter();
        configureSearch();

        findViewById(R.id.products_back_button).setOnClickListener(view -> finish());
        findViewById(R.id.products_cart_button).setOnClickListener(view -> startActivity(new Intent(this, CartActivity.class)));
        retryButton.setOnClickListener(view -> verifyCustomerAndLoad());
        loadMoreButton.setOnClickListener(view -> loadProducts(false));
        productList.setOnItemClickListener((parent, view, position, id) -> {
            Product product = productAdapter.getItem(position);
            Intent intent = new Intent(this, ProductDetailsActivity.class);
            intent.putExtra(EXTRA_PRODUCT_ID, product.getId());
            startActivity(intent);
        });

        verifyCustomerAndLoad();
    }

    private void bindViews() {
        searchInput = findViewById(R.id.products_search_input);
        categorySpinner = findViewById(R.id.products_category_spinner);
        progressBar = findViewById(R.id.products_progress);
        stateMessage = findViewById(R.id.products_state_message);
        productList = findViewById(R.id.products_list);
        retryButton = findViewById(R.id.products_retry_button);
        loadMoreButton = findViewById(R.id.products_load_more_button);
        productAdapter = new ProductAdapter();
        productList.setAdapter(productAdapter);
    }

    private void configureCategoryFilter() {
        categoryIds.add("");
        // These are the category IDs used by the current published sample products.
        categoryIds.add("fertilizers");
        categoryIds.add("irrigation");
        categoryIds.add("seeds");
        categoryIds.add("tools");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                buildCategoryLabels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                if (suppressCategorySelection || position >= categoryIds.size()) {
                    return;
                }
                String categoryId = categoryIds.get(position);
                if (!categoryId.equals(selectedCategory)) {
                    selectedCategory = categoryId;
                    applyCategoryFilter();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // All categories remains the default.
            }
        });
    }

    private void configureSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // No action needed before the text changes.
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 350);
            }

            @Override
            public void afterTextChanged(Editable text) {
                // The query is started after a short pause in typing.
            }
        });
    }

    private void verifyCustomerAndLoad() {
        requestNumber++;
        loading = false;
        customerVerified = false;
        currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            returnToLogin();
            return;
        }

        showLoading(getString(R.string.products_checking_access));
        RoleAccess.loadRole(this, currentUser, (role, error) -> {
            if (error != null) {
                showError(getString(R.string.products_access_error));
                return;
            }
            if (role != RoleAccess.Role.CUSTOMER) {
                showError(getString(R.string.products_customer_only));
                retryButton.setVisibility(View.GONE);
                return;
            }
            customerVerified = true;
            loadProducts(true);
        });
    }

    private void loadProducts(boolean reset) {
        if (!customerVerified) {
            return;
        }
        if (reset) {
            requestNumber++;
            loading = false;
        }
        if (loading) {
            return;
        }
        if (reset) {
            loadedProducts.clear();
            products.clear();
            lastDocument = null;
            productAdapter.notifyDataSetChanged();
            loadMoreButton.setVisibility(View.GONE);
            productList.setVisibility(View.GONE);
        } else if (lastDocument == null) {
            return;
        }

        loading = true;
        final int currentRequest = ++requestNumber;
        final DocumentSnapshot pageCursor = reset ? null : lastDocument;
        progressBar.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        stateMessage.setText(reset
                ? getString(R.string.products_loading)
                : getString(R.string.products_loading_more));
        stateMessage.setVisibility(products.isEmpty() ? View.VISIBLE : View.GONE);
        loadMoreButton.setEnabled(false);

        productRepository.loadPublishedProducts(
                "",
                searchInput.getText().toString(),
                pageCursor,
                (page, nextDocument, hasMore, error) -> {
                    if (currentRequest != requestNumber || isFinishing()) {
                        return;
                    }
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    loadMoreButton.setEnabled(true);

                    if (error != null) {
                        if (error instanceof FirebaseFirestoreException) {
                            FirebaseFirestoreException firestoreError =
                                    (FirebaseFirestoreException) error;
                            Log.e("FarmHubProducts", "Catalogue query failed: code="
                                    + firestoreError.getCode() + ", message="
                                    + firestoreError.getMessage());
                        } else {
                            Log.e("FarmHubProducts", "Catalogue query failed: code=unknown, message="
                                    + error.getMessage());
                        }
                        showError(getString(R.string.products_load_error));
                        return;
                    }

                    loadedProducts.addAll(page);
                    lastDocument = nextDocument;
                    updateCategoryOptions();
                    applyCategoryFilter();
                    loadMoreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);

                    if (products.isEmpty()) {
                        int emptyMessage;
                        if (hasMore) {
                            emptyMessage = R.string.products_no_products_on_page;
                        } else if (searchInput.getText().length() > 0
                                || !selectedCategory.isEmpty()) {
                            emptyMessage = R.string.products_no_matching_products;
                        } else {
                            emptyMessage = R.string.products_empty;
                        }
                        stateMessage.setText(emptyMessage);
                        stateMessage.setVisibility(View.VISIBLE);
                        productList.setVisibility(View.GONE);
                    } else {
                        stateMessage.setVisibility(View.GONE);
                        productList.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void updateCategoryOptions() {
        String categoryToKeep = selectedCategory;
        for (Product product : loadedProducts) {
            if (!categoryIds.contains(product.getCategoryId())) {
                categoryIds.add(product.getCategoryId());
            }
        }
        if (categoryIds.size() > 2) {
            List<String> sortedCategories = new ArrayList<>(categoryIds.subList(1, categoryIds.size()));
            sortedCategories.sort(String.CASE_INSENSITIVE_ORDER);
            categoryIds.subList(1, categoryIds.size()).clear();
            categoryIds.addAll(sortedCategories);
        }

        ArrayList<String> labels = buildCategoryLabels();
        suppressCategorySelection = true;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        int selectedIndex = categoryIds.indexOf(categoryToKeep);
        categorySpinner.setSelection(Math.max(0, selectedIndex), false);
        suppressCategorySelection = false;
    }

    private ArrayList<String> buildCategoryLabels() {
        ArrayList<String> labels = new ArrayList<>();
        labels.add(getString(R.string.products_all_categories));
        for (int i = 1; i < categoryIds.size(); i++) {
            labels.add(formatCategory(categoryIds.get(i)));
        }
        return labels;
    }

    private void applyCategoryFilter() {
        products.clear();
        for (Product product : loadedProducts) {
            if (selectedCategory.isEmpty()
                    || selectedCategory.equals(product.getCategoryId())) {
                products.add(product);
            }
        }
        productAdapter.notifyDataSetChanged();

        if (products.isEmpty()) {
            stateMessage.setText(R.string.products_no_matching_products);
            stateMessage.setVisibility(View.VISIBLE);
            productList.setVisibility(View.GONE);
        } else {
            stateMessage.setVisibility(View.GONE);
            productList.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(String message) {
        progressBar.setVisibility(View.VISIBLE);
        stateMessage.setText(message);
        stateMessage.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        productList.setVisibility(View.GONE);
        loadMoreButton.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loading = false;
        progressBar.setVisibility(View.GONE);
        stateMessage.setText(message);
        stateMessage.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        productList.setVisibility(products.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String formatCategory(String categoryId) {
        String label = categoryId.replace('_', ' ').replace('-', ' ').trim();
        if (label.isEmpty()) {
            return label;
        }
        return label.substring(0, 1).toUpperCase(Locale.getDefault()) + label.substring(1);
    }

    private String formatPrice(Product product) {
        try {
            Currency currency = Currency.getInstance(product.getCurrency());
            int digits = Math.max(0, currency.getDefaultFractionDigits());
            BigDecimal amount = BigDecimal.valueOf(product.getPriceMinor(), digits);
            NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.getDefault());
            formatter.setCurrency(currency);
            formatter.setMinimumFractionDigits(digits);
            formatter.setMaximumFractionDigits(digits);
            return formatter.format(amount);
        } catch (IllegalArgumentException error) {
            return product.getCurrency() + " " + product.getPriceMinor();
        }
    }

    @Override
    protected void onDestroy() {
        searchHandler.removeCallbacksAndMessages(null);
        if (imageLoader != null) {
            imageLoader.close();
        }
        super.onDestroy();
    }

    private final class ProductAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return products.size();
        }

        @Override
        public Product getItem(int position) {
            return products.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(ProductCatalogActivity.this)
                        .inflate(R.layout.item_product_card, parent, false);
            }

            Product product = getItem(position);
            ImageView image = row.findViewById(R.id.product_card_image);
            TextView name = row.findViewById(R.id.product_card_name);
            TextView category = row.findViewById(R.id.product_card_category);
            TextView price = row.findViewById(R.id.product_card_price);
            TextView availability = row.findViewById(R.id.product_card_availability);

            name.setText(product.getDisplayName());
            category.setText(formatCategory(product.getCategoryId()));
            price.setText(getString(R.string.product_price_with_unit,
                    formatPrice(product), product.getUnitLabel()));
            availability.setText(product.isAvailable()
                    ? R.string.product_in_stock : R.string.product_out_of_stock);
            availability.setTextColor(getColor(product.isAvailable()
                    ? R.color.leaf_green_dark : R.color.error_red));
            image.setContentDescription(getString(R.string.product_image_description,
                    product.getDisplayName()));
            imageLoader.loadForProduct(product.getImageUrl(), product.getDisplayName(), image, null);
            return row;
        }
    }
}
