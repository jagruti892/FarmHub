package in.farmhub.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/** Read-only equipment catalogue for signed-in Customer accounts. */
public class EquipmentCatalogActivity extends Activity {
    private final List<Equipment> equipment = new ArrayList<>();

    private FirebaseAuth firebaseAuth;
    private EquipmentRepository repository;
    private ProductImageLoader imageLoader;
    private EquipmentAdapter adapter;
    private DocumentSnapshot lastDocument;
    private boolean customerVerified;
    private boolean loading;
    private int requestNumber;

    private ProgressBar progressBar;
    private TextView stateMessage;
    private ListView equipmentList;
    private Button retryButton;
    private Button loadMoreButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equipment_catalog);

        firebaseAuth = FirebaseAuth.getInstance();
        if (firebaseAuth.getCurrentUser() == null) {
            returnToLogin();
            return;
        }

        repository = new EquipmentRepository();
        imageLoader = new ProductImageLoader();
        bindViews();
        findViewById(R.id.equipment_back_button).setOnClickListener(view -> finish());
        retryButton.setOnClickListener(view -> verifyCustomerAndLoad());
        loadMoreButton.setOnClickListener(view -> loadEquipment(false));
        verifyCustomerAndLoad();
    }

    private void bindViews() {
        progressBar = findViewById(R.id.equipment_progress);
        stateMessage = findViewById(R.id.equipment_state_message);
        equipmentList = findViewById(R.id.equipment_list);
        retryButton = findViewById(R.id.equipment_retry_button);
        loadMoreButton = findViewById(R.id.equipment_load_more_button);
        adapter = new EquipmentAdapter();
        equipmentList.setAdapter(adapter);
        equipmentList.setOnItemClickListener((parent, view, position, id) -> {
            Equipment selected = adapter.getItem(position);
            Intent intent = new Intent(this, EquipmentDetailsActivity.class);
            intent.putExtra(EquipmentDetailsActivity.EXTRA_EQUIPMENT_ID, selected.getId());
            startActivity(intent);
        });
    }

    private void verifyCustomerAndLoad() {
        requestNumber++;
        loading = false;
        customerVerified = false;
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            returnToLogin();
            return;
        }

        showLoading(getString(R.string.equipment_catalog_checking_access));
        RoleAccess.loadRole(this, currentUser, (role, error) -> {
            if (isFinishing()) {
                return;
            }
            if (error != null) {
                showError(getString(R.string.equipment_catalog_access_error));
                return;
            }
            if (role != RoleAccess.Role.CUSTOMER) {
                showError(getString(R.string.equipment_catalog_customer_only));
                retryButton.setVisibility(View.GONE);
                return;
            }
            customerVerified = true;
            loadEquipment(true);
        });
    }

    private void loadEquipment(boolean reset) {
        if (!customerVerified || loading) {
            return;
        }
        if (reset) {
            equipment.clear();
            adapter.notifyDataSetChanged();
            lastDocument = null;
            requestNumber++;
        } else if (lastDocument == null) {
            return;
        }

        loading = true;
        int currentRequest = ++requestNumber;
        progressBar.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        loadMoreButton.setEnabled(false);
        stateMessage.setText(reset
                ? R.string.equipment_catalog_loading
                : R.string.equipment_catalog_loading_more);
        stateMessage.setVisibility(equipment.isEmpty() ? View.VISIBLE : View.GONE);

        repository.loadPublishedEquipment(reset ? null : lastDocument,
                (page, nextDocument, hasMore, error) -> {
                    if (currentRequest != requestNumber || isFinishing()) {
                        return;
                    }
                    loading = false;
                    progressBar.setVisibility(View.GONE);
                    loadMoreButton.setEnabled(true);

                    if (error != null) {
                        logLoadError(error);
                        showError(getString(R.string.equipment_catalog_load_error));
                        return;
                    }

                    equipment.addAll(page);
                    lastDocument = nextDocument;
                    adapter.notifyDataSetChanged();
                    loadMoreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);

                    if (equipment.isEmpty()) {
                        stateMessage.setText(hasMore
                                ? R.string.equipment_catalog_no_valid_items
                                : R.string.equipment_catalog_empty);
                        stateMessage.setVisibility(View.VISIBLE);
                        equipmentList.setVisibility(View.GONE);
                    } else {
                        stateMessage.setVisibility(View.GONE);
                        equipmentList.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void showLoading(String message) {
        progressBar.setVisibility(View.VISIBLE);
        stateMessage.setText(message);
        stateMessage.setVisibility(View.VISIBLE);
        equipmentList.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        loadMoreButton.setVisibility(View.GONE);
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        stateMessage.setText(message);
        stateMessage.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        equipmentList.setVisibility(View.GONE);
        loadMoreButton.setVisibility(View.GONE);
    }

    private void logLoadError(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) error;
            Log.e("FarmHubEquipment", "Equipment query failed: code="
                    + firestoreError.getCode() + ", message=" + firestoreError.getMessage());
        } else {
            Log.e("FarmHubEquipment", "Equipment query failed: " + error.getMessage());
        }
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

    private final class EquipmentAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return equipment.size();
        }

        @Override
        public Equipment getItem(int position) {
            return equipment.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            View row = recycled;
            if (row == null) {
                row = LayoutInflater.from(EquipmentCatalogActivity.this)
                        .inflate(R.layout.item_equipment_card, parent, false);
            }

            Equipment item = getItem(position);
            TextView name = row.findViewById(R.id.equipment_card_name);
            TextView price = row.findViewById(R.id.equipment_card_price);
            TextView location = row.findViewById(R.id.equipment_card_location);
            TextView availability = row.findViewById(R.id.equipment_card_availability);
            ImageView image = row.findViewById(R.id.equipment_card_image);
            TextView photoUnavailable = row.findViewById(R.id.equipment_card_photo_unavailable);

            name.setText(item.getName());
            price.setText(getString(R.string.equipment_daily_price, formatPrice(item)));
            image.setContentDescription(getString(
                    R.string.equipment_card_image_description, item.getName()));

            if (item.getLocation().isEmpty()) {
                location.setVisibility(View.GONE);
            } else {
                location.setText(getString(R.string.equipment_location, item.getLocation()));
                location.setVisibility(View.VISIBLE);
            }

            if (item.isAvailable()) {
                availability.setText(R.string.equipment_available);
                availability.setTextColor(getColor(R.color.leaf_green_dark));
            } else if (item.isUnavailable()) {
                availability.setText(R.string.equipment_unavailable);
                availability.setTextColor(getColor(R.color.error_red));
            } else {
                availability.setText(R.string.equipment_availability_unknown);
                availability.setTextColor(getColor(R.color.text_secondary));
            }

            imageLoader.loadForEquipment(item.getId(), item.getImageUrl(), image,
                    R.drawable.bg_status_message,
                    success -> photoUnavailable.setVisibility(
                            success ? View.GONE : View.VISIBLE));
            photoUnavailable.setVisibility(View.VISIBLE);
            return row;
        }
    }

    private String formatPrice(Equipment item) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        formatter.setCurrency(Currency.getInstance("INR"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(BigDecimal.valueOf(item.getDailyPriceMinor(), 2));
    }
}
