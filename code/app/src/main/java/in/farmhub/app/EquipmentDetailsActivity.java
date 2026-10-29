package in.farmhub.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Equipment details and a single full-day booking request. */
public class EquipmentDetailsActivity extends Activity {
    public static final String EXTRA_EQUIPMENT_ID = "in.farmhub.app.extra.EQUIPMENT_ID";

    private FirebaseAuth auth;
    private EquipmentRepository equipmentRepository;
    private BookingRepository bookingRepository;
    private ProductImageLoader imageLoader;
    private Equipment equipment;
    private FirebaseUser user;
    private Timestamp selectedDay;

    private ProgressBar progress;
    private TextView state;
    private View content;
    private ImageView image;
    private TextView name;
    private TextView price;
    private TextView location;
    private TextView description;
    private TextView selectedDate;
    private TextView agreementDetails;
    private CheckBox agreementAccepted;
    private Button dateButton;
    private Button requestButton;
    private Button retryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equipment_details);
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        if (user == null) {
            returnToLogin();
            return;
        }
        String equipmentId = getIntent().getStringExtra(EXTRA_EQUIPMENT_ID);
        if (equipmentId == null || equipmentId.trim().isEmpty()) {
            finish();
            return;
        }

        equipmentRepository = new EquipmentRepository();
        bookingRepository = new BookingRepository();
        imageLoader = new ProductImageLoader();
        bindViews();
        findViewById(R.id.equipment_details_back).setOnClickListener(view -> finish());
        retryButton.setOnClickListener(view -> loadEquipment(equipmentId));
        dateButton.setOnClickListener(view -> showDatePicker());
        requestButton.setOnClickListener(view -> submitRequest());
        verifyCustomerAndLoad(equipmentId);
    }

    private void bindViews() {
        progress = findViewById(R.id.equipment_details_progress);
        state = findViewById(R.id.equipment_details_state);
        content = findViewById(R.id.equipment_details_content);
        image = findViewById(R.id.equipment_details_image);
        name = findViewById(R.id.equipment_details_name);
        price = findViewById(R.id.equipment_details_price);
        location = findViewById(R.id.equipment_details_location);
        description = findViewById(R.id.equipment_details_description);
        selectedDate = findViewById(R.id.equipment_selected_date);
        agreementDetails = findViewById(R.id.equipment_agreement_details);
        agreementAccepted = findViewById(R.id.equipment_agreement_checkbox);
        dateButton = findViewById(R.id.equipment_choose_date_button);
        requestButton = findViewById(R.id.equipment_request_button);
        retryButton = findViewById(R.id.equipment_details_retry);
    }

    private void verifyCustomerAndLoad(String equipmentId) {
        showLoading(getString(R.string.equipment_details_checking_access));
        RoleAccess.loadRole(this, user, (role, error) -> {
            if (isFinishing()) return;
            if (error != null || role != RoleAccess.Role.CUSTOMER) {
                showError(getString(R.string.equipment_details_access_error));
                return;
            }
            loadEquipment(equipmentId);
        });
    }

    private void loadEquipment(String equipmentId) {
        showLoading(getString(R.string.equipment_details_loading));
        equipmentRepository.loadPublishedEquipment(equipmentId, (item, error) -> {
            if (isFinishing()) return;
            progress.setVisibility(View.GONE);
            if (error != null) {
                showError(getString(R.string.equipment_details_load_error));
                return;
            }
            if (item == null) {
                showError(getString(R.string.equipment_details_unavailable));
                retryButton.setVisibility(View.GONE);
                return;
            }
            equipment = item;
            showEquipment();
        });
    }

    private void showEquipment() {
        state.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        name.setText(equipment.getName());
        price.setText(getString(R.string.equipment_details_price, formatPrice(equipment)));
        description.setText(equipment.getDescription().isEmpty()
                ? getString(R.string.equipment_details_no_description)
                : equipment.getDescription());
        location.setText(equipment.getLocation().isEmpty() ? ""
                : getString(R.string.equipment_location, equipment.getLocation()));
        image.setContentDescription(getString(
                R.string.equipment_card_image_description, equipment.getName()));
        imageLoader.loadForEquipment(equipment.getId(), equipment.getImageUrl(), image,
                R.drawable.bg_status_message, null);
        selectedDay = tomorrowInIndia();
        displaySelectedDate();
        boolean canRequest = equipment.isAvailable() && !equipment.getOwnerUid().equals(user.getUid())
                && !equipment.getLocation().isEmpty();
        dateButton.setEnabled(canRequest);
        requestButton.setEnabled(canRequest);
        if (!canRequest) {
            requestButton.setText(equipment.getLocation().isEmpty()
                    ? R.string.equipment_details_missing_return_location
                    : R.string.equipment_details_not_bookable);
        }
        updateAgreementDetails();
    }

    private void showDatePicker() {
        Calendar initial = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"), Locale.US);
        initial.setTime(selectedDay.toDate());
        Calendar minimum = tomorrowCalendar();
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            Calendar selected = Calendar.getInstance(
                    TimeZone.getTimeZone("Asia/Kolkata"), Locale.US);
            selected.clear();
            selected.set(year, month, day, 0, 0, 0);
            Timestamp candidate = new Timestamp(selected.getTime());
            if (candidate.compareTo(new Timestamp(new Date())) <= 0) {
                Toast.makeText(this, R.string.equipment_details_future_date_only,
                        Toast.LENGTH_LONG).show();
                return;
            }
            selectedDay = candidate;
            agreementAccepted.setChecked(false);
            displaySelectedDate();
            updateAgreementDetails();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(minimum.getTimeInMillis());
        dialog.show();
    }

    private void submitRequest() {
        if (equipment == null || selectedDay == null || user == null) return;
        if (!agreementAccepted.isChecked()) {
            Toast.makeText(this, R.string.equipment_agreement_accept_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedDay.compareTo(new Timestamp(new Date())) <= 0) {
            Toast.makeText(this, R.string.equipment_details_future_date_only,
                    Toast.LENGTH_LONG).show();
            return;
        }
        setSubmitting(true);
        bookingRepository.createPendingBooking(equipment, selectedDay, user,
                agreementAccepted.isChecked(),
                (bookingId, error) -> {
                    if (isFinishing()) return;
                    setSubmitting(false);
                    if (error != null) {
                        Toast.makeText(this, getString(R.string.booking_request_failed,
                                friendlyError(error)), Toast.LENGTH_LONG).show();
                        if (error.getMessage() != null
                                && error.getMessage().contains("listing terms changed")) {
                            agreementAccepted.setChecked(false);
                            loadEquipment(equipment.getId());
                        }
                        return;
                    }
                    Toast.makeText(this, R.string.booking_request_sent, Toast.LENGTH_LONG).show();
                    startActivity(BookingListActivity.customerIntent(this));
                });
    }

    private void setSubmitting(boolean submitting) {
        progress.setVisibility(submitting ? View.VISIBLE : View.GONE);
        boolean canRequest = equipment != null && equipment.isAvailable()
                && !equipment.getOwnerUid().equals(user == null ? "" : user.getUid())
                && !equipment.getLocation().isEmpty();
        requestButton.setEnabled(!submitting && canRequest);
        dateButton.setEnabled(!submitting && canRequest);
        agreementAccepted.setEnabled(!submitting);
        requestButton.setText(submitting ? R.string.booking_sending
                : R.string.equipment_request_button);
    }

    private void displaySelectedDate() {
        SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
        format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        selectedDate.setText(getString(R.string.equipment_selected_date,
                format.format(selectedDay.toDate())));
    }

    private void updateAgreementDetails() {
        if (equipment == null || selectedDay == null) return;
        Calendar deadline = Calendar.getInstance(
                TimeZone.getTimeZone("Asia/Kolkata"), Locale.US);
        deadline.setTime(selectedDay.toDate());
        deadline.set(Calendar.HOUR_OF_DAY, 20);
        deadline.set(Calendar.MINUTE, 0);
        deadline.set(Calendar.SECOND, 0);
        deadline.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat deadlineFormat = new SimpleDateFormat(
                "EEE, d MMM yyyy 'at' h:mm a", Locale.ENGLISH);
        deadlineFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        String deadlineText = deadlineFormat.format(deadline.getTime());
        agreementDetails.setText(getString(R.string.equipment_agreement_summary,
                deadlineText, equipment.getLocation(), formatPrice(equipment),
                formatMinor(equipment.getDamageChargeMinor())));
    }

    private String formatMinor(long amountMinor) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        formatter.setCurrency(Currency.getInstance("INR"));
        return formatter.format(BigDecimal.valueOf(Math.max(0, amountMinor), 2));
    }

    private String formatPrice(Equipment item) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        formatter.setCurrency(Currency.getInstance("INR"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(BigDecimal.valueOf(item.getDailyPriceMinor(), 2));
    }

    private static Timestamp tomorrowInIndia() {
        return new Timestamp(tomorrowCalendar().getTime());
    }

    private static Calendar tomorrowCalendar() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"), Locale.US);
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private String friendlyError(Exception error) {
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return getString(R.string.booking_permission_error);
        }
        return error.getMessage() == null ? getString(R.string.error_try_again)
                : error.getMessage();
    }

    private void showLoading(String message) {
        progress.setVisibility(View.VISIBLE);
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (imageLoader != null) imageLoader.close();
        super.onDestroy();
    }
}
