package in.farmhub.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import android.text.InputType;
import android.widget.Toast;

/** Read-only list of equipment matching the verified owner's UID. */
public class OwnerEquipmentActivity extends Activity {
    private final List<Equipment> items = new ArrayList<>();
    private FirebaseAuth auth;
    private EquipmentRepository repository;
    private ProductImageLoader imageLoader;
    private EquipmentAdapter adapter;
    private ProgressBar progress;
    private TextView state;
    private ListView list;
    private Button retry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_equipment);
        auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            returnToLogin();
            return;
        }
        repository = new EquipmentRepository();
        imageLoader = new ProductImageLoader();
        progress = findViewById(R.id.owner_equipment_progress);
        state = findViewById(R.id.owner_equipment_state);
        list = findViewById(R.id.owner_equipment_list);
        retry = findViewById(R.id.owner_equipment_retry);
        findViewById(R.id.owner_equipment_back).setOnClickListener(view -> finish());
        findViewById(R.id.owner_equipment_add)
                .setOnClickListener(view -> startActivity(new Intent(this, AddEquipmentActivity.class)));
        retry.setOnClickListener(view -> verifyAndLoad());
        adapter = new EquipmentAdapter();
        list.setAdapter(adapter);
        verifyAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth != null && repository != null && list != null) {
            verifyAndLoad();
        }
    }

    private void verifyAndLoad() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            returnToLogin();
            return;
        }
        progress.setVisibility(View.VISIBLE);
        state.setText(R.string.owner_equipment_checking);
        state.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        RoleAccess.loadRole(this, user, (role, error) -> {
            if (isFinishing())
                return;
            if (error != null || role != RoleAccess.Role.OWNER) {
                showError(getString(R.string.booking_list_role_restricted));
                return;
            }
            repository.loadOwnerEquipment(user.getUid(), (page, last, hasMore, loadError) -> {
                if (isFinishing())
                    return;
                progress.setVisibility(View.GONE);
                if (loadError != null) {
                    if (loadError instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) loadError;
                        Log.e("FarmHubOwnerEquipment", "Owner listing query failed: code="
                                + firestoreError.getCode() + ", message="
                                + firestoreError.getMessage());
                    }
                    showError(getString(R.string.owner_equipment_error));
                    return;
                }
                items.clear();
                items.addAll(page);
                adapter.notifyDataSetChanged();
                list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                state.setText(R.string.owner_equipment_empty);
                state.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        retry.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (imageLoader != null)
            imageLoader.close();
        super.onDestroy();
    }

    private final class EquipmentAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Equipment getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            View row = recycled;
            if (row == null) {
                row = LayoutInflater.from(OwnerEquipmentActivity.this)
                        .inflate(R.layout.item_owner_equipment, parent, false);
            }
            Equipment item = getItem(position);
            ((TextView) row.findViewById(R.id.equipment_card_name)).setText(item.getName());
            TextView price = row.findViewById(R.id.equipment_card_price);
            NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            money.setCurrency(Currency.getInstance("INR"));
            price.setText(getString(R.string.equipment_daily_price,
                    money.format(BigDecimal.valueOf(item.getDailyPriceMinor(), 2))));
            TextView location = row.findViewById(R.id.equipment_card_location);
            location.setText(item.getLocation());
            location.setVisibility(item.getLocation().isEmpty() ? View.GONE : View.VISIBLE);
            TextView availability = row.findViewById(R.id.equipment_card_availability);
            availability.setText(item.isAvailable()
                    ? R.string.equipment_available
                    : R.string.equipment_unavailable);

            availability.setTextColor(getColor(item.isAvailable()
                    ? R.color.leaf_green_dark
                    : R.color.error_red));
            ImageView image = row.findViewById(R.id.equipment_card_image);
            TextView missing = row.findViewById(R.id.equipment_card_photo_unavailable);
            imageLoader.loadForEquipment(item.getId(), item.getImageUrl(), image,
                    R.drawable.bg_status_message,
                    success -> missing.setVisibility(success ? View.GONE : View.VISIBLE));
            missing.setVisibility(View.VISIBLE);
            TextView charge = row.findViewById(R.id.owner_equipment_damage_charge);
            charge.setText(getString(R.string.owner_damage_charge_current,
                    formatDamageCharge(item.getDamageChargeMinor())));

            Button editEquipment = row.findViewById(R.id.owner_equipment_edit);
            editEquipment.setOnClickListener(view -> {
                android.content.Intent intent = new android.content.Intent(
                        OwnerEquipmentActivity.this,
                        EditEquipmentActivity.class);
                intent.putExtra("equipmentId", item.getId());
                startActivity(intent);
            });

            Button editCharge = row.findViewById(
                    R.id.owner_equipment_edit_damage_charge);
            editCharge.setOnClickListener(view -> promptDamageCharge(item));

            return row;

        }
    }

    private void promptDamageCharge(Equipment item) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);
        input.setHint(R.string.owner_damage_charge_hint);
        input.setText(BigDecimal.valueOf(item.getDamageChargeMinor(), 2).toPlainString());
        input.setSelectAllOnFocus(true);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.owner_damage_charge_title)
                .setMessage(R.string.owner_damage_charge_explanation)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    final long minor;
                    try {
                        BigDecimal rupees = new BigDecimal(input.getText().toString().trim());
                        if (rupees.signum() < 0)
                            throw new ArithmeticException("negative");
                        minor = rupees.setScale(2, RoundingMode.UNNECESSARY)
                                .movePointRight(2).longValueExact();
                    } catch (NumberFormatException | ArithmeticException invalid) {
                        input.setError(getString(R.string.owner_damage_charge_invalid));
                        return;
                    }

                    repository.updateDamageCharge(item.getId(), minor, error -> {
                        if (isFinishing())
                            return;
                        dialog.dismiss();
                        if (error != null) {
                            Toast.makeText(this, getString(R.string.owner_damage_charge_save_error,
                                    error.getMessage() == null ? getString(R.string.error_try_again)
                                            : error.getMessage()),
                                    Toast.LENGTH_LONG).show();
                            verifyAndLoad();
                            return;
                        }
                        Toast.makeText(this, R.string.owner_damage_charge_saved,
                                Toast.LENGTH_SHORT).show();
                        verifyAndLoad();
                    });
                }));
        dialog.show();
    }

    private String formatDamageCharge(long minor) {
        java.text.NumberFormat money = java.text.NumberFormat
                .getCurrencyInstance(new Locale("en", "IN"));
        money.setCurrency(java.util.Currency.getInstance("INR"));
        return money.format(BigDecimal.valueOf(minor, 2));
    }
}
