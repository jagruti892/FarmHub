package in.farmhub.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.TimeZone;

/** One-time owner return inspection for an approved rental. */
public class BookingInspectionActivity extends Activity {
    public static final String EXTRA_BOOKING_ID = "in.farmhub.app.extra.BOOKING_ID";

    private BookingRepository repository;
    private FirebaseUser user;
    private Booking booking;
    private ProgressBar progress;
    private TextView state;
    private View form;
    private TextView summary;
    private RadioGroup returnChoice;
    private RadioGroup conditionChoice;
    private View conditionFields;
    private EditText description;
    private EditText charge;
    private Button save;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_inspection);
        progress = findViewById(R.id.inspection_progress);
        state = findViewById(R.id.inspection_state);
        form = findViewById(R.id.inspection_form);
        summary = findViewById(R.id.inspection_booking_summary);
        returnChoice = findViewById(R.id.inspection_return_choice);
        conditionChoice = findViewById(R.id.inspection_condition_choice);
        conditionFields = findViewById(R.id.inspection_condition_fields);
        description = findViewById(R.id.inspection_damage_description);
        charge = findViewById(R.id.inspection_damage_charge);
        save = findViewById(R.id.inspection_save);
        findViewById(R.id.inspection_back).setOnClickListener(view -> finish());
        findViewById(R.id.inspection_retry).setOnClickListener(view -> {
    String retryBookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
    if (retryBookingId != null && !retryBookingId.trim().isEmpty()) {
        verifyOwnerAndLoad(retryBookingId);
    }
});
        returnChoice.setOnCheckedChangeListener((group, checkedId) -> updateConditionVisibility());
        save.setOnClickListener(view -> saveInspection());

        user = FirebaseAuth.getInstance().getCurrentUser();
        String bookingId = getIntent().getStringExtra(EXTRA_BOOKING_ID);
        if (user == null) {
            returnToLogin();
        } else if (bookingId == null || bookingId.trim().isEmpty()) {
            showError(getString(R.string.inspection_booking_missing));
        } else {
            repository = new BookingRepository();
            verifyOwnerAndLoad(bookingId);
        }
    }

    private void verifyOwnerAndLoad(String bookingId) {
        progress.setVisibility(View.VISIBLE);
        state.setText(R.string.inspection_checking_access);
        state.setVisibility(View.VISIBLE);
        findViewById(R.id.inspection_retry).setVisibility(View.GONE);
        form.setVisibility(View.GONE);
        RoleAccess.loadRole(this, user, (role, error) -> {
            if (isFinishing()) return;
            if (error != null || role != RoleAccess.Role.OWNER) {
                showError(getString(R.string.inspection_access_denied));
                return;
            }
            repository.loadBooking(bookingId, (loaded, loadError) -> {
                if (isFinishing()) return;
                progress.setVisibility(View.GONE);
                if (loadError != null) {
                    showError(getString(R.string.inspection_load_error));
                    return;
                }
                if (loaded == null || !user.getUid().equals(loaded.getOwnerUid())
                        || !"approved".equals(loaded.getStatus())) {
                    showError(getString(R.string.inspection_not_eligible));
                    return;
                }
                booking = loaded;
                showBooking();
            });
        });
    }

    private void showBooking() {
        progress.setVisibility(View.GONE);
        state.setVisibility(View.GONE);
        form.setVisibility(View.VISIBLE);
        String day = formatDate(booking);
        String deadline = booking.getReturnDeadline() == null
                ? getString(R.string.booking_legacy_deadline)
                : formatTime(booking);
        summary.setText(getString(R.string.inspection_booking_details,
                booking.getEquipmentName(), day, deadline,
                booking.getReturnLocation().isEmpty()
                        ? getString(R.string.booking_legacy_location) : booking.getReturnLocation(),
                formatMinor(booking.getAgreedDamageChargeMinor())));
        returnChoice.check("returned".equals(booking.getReturnStatus())
                ? R.id.inspection_returned : R.id.inspection_unreturned);
        if ("no_damage".equals(booking.getInspectionCondition())) {
            conditionChoice.check(R.id.inspection_no_damage);
        } else if ("damaged".equals(booking.getInspectionCondition())) {
            conditionChoice.check(R.id.inspection_damaged);
        }
        description.setText(booking.getDamageDescription());
        charge.setText(BigDecimal.valueOf(booking.getInspectionChargeMinor(), 2).toPlainString());
        updateConditionVisibility();

        if (!booking.hasAgreementTerms()) {
            save.setEnabled(false);
            showInlineNotice(R.string.inspection_legacy_booking);
        } else if ("returned".equals(booking.getReturnStatus())) {
            save.setEnabled(false);
            showInlineNotice(R.string.inspection_already_recorded);
        } else {
            save.setEnabled(true);
        }
    }

    private void updateConditionVisibility() {
        boolean returned = returnChoice.getCheckedRadioButtonId() == R.id.inspection_returned;
        conditionFields.setVisibility(returned ? View.VISIBLE : View.GONE);
    }

    private void saveInspection() {
        if (booking == null || !booking.hasAgreementTerms()
                || "returned".equals(booking.getReturnStatus())) return;
        boolean returned = returnChoice.getCheckedRadioButtonId() == R.id.inspection_returned;
        String returnStatus = returned ? "returned" : "unreturned";
        String condition;
        String damageText = "";
        long chargeMinor = 0;
        if (!returned) {
            condition = "not_inspected";
        } else if (conditionChoice.getCheckedRadioButtonId() == R.id.inspection_no_damage) {
            condition = "no_damage";
        } else if (conditionChoice.getCheckedRadioButtonId() == R.id.inspection_damaged) {
            condition = "damaged";
            damageText = description.getText().toString().trim();
            if (damageText.isEmpty()) {
                description.setError(getString(R.string.inspection_damage_description_required));
                return;
            }
            try {
                BigDecimal rupees = new BigDecimal(charge.getText().toString().trim());
                if (rupees.signum() < 0) throw new ArithmeticException("negative");
                chargeMinor = rupees.setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2).longValueExact();
            } catch (NumberFormatException | ArithmeticException invalid) {
                charge.setError(getString(R.string.inspection_damage_charge_invalid));
                return;
            }
            
        } else {
            Toast.makeText(this, R.string.inspection_choose_condition, Toast.LENGTH_LONG).show();
            return;
        }

        save.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        repository.recordInspection(booking.getId(), returnStatus, condition,
                damageText, chargeMinor, (id, error) -> {
                    if (isFinishing()) return;
                    progress.setVisibility(View.GONE);
                    if (error != null) {
                        save.setEnabled(true);
                        Toast.makeText(this, getString(R.string.inspection_save_error,
                                error.getMessage() == null ? getString(R.string.error_try_again)
                                        : error.getMessage()), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.inspection_saved, Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    private String formatDate(Booking item) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
        format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return format.format(item.getRentalDay().toDate());
    }

    private String formatTime(Booking item) {
        SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy, h:mm a 'IST'", Locale.ENGLISH);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return format.format(item.getReturnDeadline().toDate());
    }

    private String formatMinor(long minor) {
        NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        money.setCurrency(Currency.getInstance("INR"));
        return money.format(BigDecimal.valueOf(minor, 2));
    }

    private void showInlineNotice(int messageId) {
        state.setText(messageId);
        state.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        form.setVisibility(View.GONE);
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        findViewById(R.id.inspection_retry).setVisibility(View.VISIBLE);
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

