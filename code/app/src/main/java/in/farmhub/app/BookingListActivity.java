package in.farmhub.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Customer booking history or approved-owner request queue. */
public class BookingListActivity extends Activity {
    private static final String EXTRA_OWNER_MODE = "in.farmhub.app.extra.OWNER_BOOKINGS";

    public static Intent customerIntent(Context context) {
        return new Intent(context, BookingListActivity.class).putExtra(EXTRA_OWNER_MODE, false);
    }

    public static Intent ownerIntent(Context context) {
        return new Intent(context, BookingListActivity.class).putExtra(EXTRA_OWNER_MODE, true);
    }

    private final List<Booking> bookings = new ArrayList<>();
    private FirebaseAuth auth;
    private FirebaseUser user;
    private BookingRepository repository;
    private BookingAdapter adapter;
    private boolean ownerMode;
    private boolean accessVerified;
    private boolean actionInProgress;
    private boolean firstResume = true;
    private int requestNumber;
    private ProgressBar progress;
    private TextView state;
    private ListView list;
    private Button retry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_list);
        ownerMode = getIntent().getBooleanExtra(EXTRA_OWNER_MODE, false);
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        if (user == null) {
            returnToLogin();
            return;
        }
        repository = new BookingRepository();
        progress = findViewById(R.id.booking_list_progress);
        state = findViewById(R.id.booking_list_state);
        list = findViewById(R.id.booking_list);
        retry = findViewById(R.id.booking_list_retry);
        TextView title = findViewById(R.id.booking_list_title);
        title.setText(ownerMode ? R.string.owner_booking_title : R.string.customer_booking_title);
        findViewById(R.id.booking_list_back).setOnClickListener(view -> finish());
        retry.setOnClickListener(view -> verifyAccess());
        adapter = new BookingAdapter();
        list.setAdapter(adapter);
        verifyAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else if (accessVerified && !actionInProgress) {
            loadBookings();
        }
    }

    private void verifyAccess() {
        user = auth.getCurrentUser();
        if (user == null) {
            returnToLogin();
            return;
        }
        accessVerified = false;
        showLoading(getString(R.string.booking_list_checking));
        RoleAccess.loadRole(this, user, (role, error) -> {
            if (isFinishing()) return;
            RoleAccess.Role required = ownerMode ? RoleAccess.Role.OWNER : RoleAccess.Role.CUSTOMER;
            if (error != null || role != required) {
                showError(error != null ? getString(R.string.booking_list_access_error)
                        : getString(R.string.booking_list_role_restricted));
                return;
            }
            accessVerified = true;
            loadBookings();
        });
    }

    private void loadBookings() {
        if (!accessVerified || actionInProgress) return;
        int currentRequest = ++requestNumber;
        progress.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        state.setText(R.string.booking_list_loading);
        state.setVisibility(bookings.isEmpty() ? View.VISIBLE : View.GONE);
        if (ownerMode) {
            repository.loadOwnerBookings(user.getUid(), (items, error) -> showBookings(currentRequest, items, error));
        } else {
            repository.loadCustomerBookings(user.getUid(), (items, error) -> showBookings(currentRequest, items, error));
        }
    }

    private void showBookings(int currentRequest, List<Booking> items, Exception error) {
        if (currentRequest != requestNumber || isFinishing()) return;
        progress.setVisibility(View.GONE);
        if (error != null) {
            if (error instanceof FirebaseFirestoreException) {
                FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) error;
                Log.e("FarmHubBookings", "Booking query failed: code="
                        + firestoreError.getCode() + ", message=" + firestoreError.getMessage());
            } else {
                Log.e("FarmHubBookings", "Booking query failed", error);
            }
            showError(getString(R.string.booking_list_load_error));
            return;
        }
        bookings.clear();
        bookings.addAll(items);
        adapter.notifyDataSetChanged();
        list.setVisibility(bookings.isEmpty() ? View.GONE : View.VISIBLE);
        state.setText(ownerMode ? R.string.owner_booking_empty : R.string.customer_booking_empty);
        state.setVisibility(bookings.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void decide(Booking booking, boolean approve) {
        if (actionInProgress) return;
        actionInProgress = true;
        progress.setVisibility(View.VISIBLE);
        repository.decideBooking(booking.getId(), user.getUid(), approve, (id, error) -> {
            actionInProgress = false;
            progress.setVisibility(View.GONE);
            if (isFinishing()) return;
            if (error != null) {
                Toast.makeText(this, getString(R.string.booking_decision_failed,
                        error.getMessage() == null ? getString(R.string.error_try_again)
                                : error.getMessage()), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, approve ? R.string.booking_approved : R.string.booking_rejected,
                        Toast.LENGTH_LONG).show();
            }
            loadBookings();
        });
    }

    private void showLoading(String message) {
        progress.setVisibility(View.VISIBLE);
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
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

    private final class BookingAdapter extends BaseAdapter {
        @Override public int getCount() { return bookings.size(); }
        @Override public Booking getItem(int position) { return bookings.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            View row = recycled;
            if (row == null) {
                row = LayoutInflater.from(BookingListActivity.this)
                        .inflate(R.layout.item_booking_card, parent, false);
            }
            Booking booking = getItem(position);
            TextView equipment = row.findViewById(R.id.booking_equipment_id);
            TextView date = row.findViewById(R.id.booking_date);
            TextView price = row.findViewById(R.id.booking_price);
            TextView status = row.findViewById(R.id.booking_status);
            TextView returnLocation = row.findViewById(R.id.booking_return_location);
            TextView agreement = row.findViewById(R.id.booking_agreement);
            TextView inspection = row.findViewById(R.id.booking_inspection);
            Button approve = row.findViewById(R.id.booking_approve_button);
            Button reject = row.findViewById(R.id.booking_reject_button);
            Button inspectButton = row.findViewById(R.id.booking_inspection_button);
            equipment.setText(getString(R.string.booking_equipment_label, booking.getEquipmentName()));
            SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
            format.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            String dayText = format.format(booking.getRentalDay().toDate());
            String deadlineText;
            if (booking.getReturnDeadline() == null) {
                deadlineText = getString(R.string.booking_legacy_deadline);
            } else {
                SimpleDateFormat timeFormat = new SimpleDateFormat("d MMM yyyy, h:mm a 'IST'", Locale.ENGLISH);
                timeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                deadlineText = timeFormat.format(booking.getReturnDeadline().toDate());
            }
            date.setText(getString(R.string.booking_day_and_deadline, dayText, deadlineText));
            NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            money.setCurrency(Currency.getInstance(booking.getCurrency()));
            price.setText(getString(R.string.booking_price_label,
                    money.format(BigDecimal.valueOf(booking.getAgreedPriceMinor(), 2))));
            returnLocation.setText(booking.getReturnLocation().isEmpty()
                    ? getString(R.string.booking_legacy_location)
                    : getString(R.string.booking_return_location, booking.getReturnLocation()));
            String charge = money.format(BigDecimal.valueOf(
                    booking.getAgreedDamageChargeMinor(), 2));
            agreement.setText(booking.hasAgreementTerms()
                    ? getString(R.string.booking_agreement_accepted, charge)
                    : getString(R.string.booking_agreement_legacy, charge));
            status.setText(getString(R.string.booking_status_label, booking.getStatus()));
            inspection.setText(formatInspection(booking));
            boolean canDecide = ownerMode && "pending".equals(booking.getStatus());
            approve.setVisibility(canDecide ? View.VISIBLE : View.GONE);
            reject.setVisibility(canDecide ? View.VISIBLE : View.GONE);
            approve.setEnabled(!actionInProgress);
            reject.setEnabled(!actionInProgress);
            approve.setOnClickListener(view -> decide(booking, true));
            reject.setOnClickListener(view -> decide(booking, false));
            boolean showInspection = ownerMode && "approved".equals(booking.getStatus());
            inspectButton.setVisibility(showInspection ? View.VISIBLE : View.GONE);
            inspectButton.setEnabled(!actionInProgress);
            inspectButton.setText("returned".equals(booking.getReturnStatus())
                    ? R.string.booking_view_inspection : R.string.booking_record_inspection);
            inspectButton.setOnClickListener(view -> {
                Intent intent = new Intent(BookingListActivity.this, BookingInspectionActivity.class);
                intent.putExtra(BookingInspectionActivity.EXTRA_BOOKING_ID, booking.getId());
                startActivity(intent);
            });
            return row;
        }
    }

    private String formatInspection(Booking booking) {
        String returnStatus = "returned".equals(booking.getReturnStatus())
                ? getString(R.string.booking_returned) : getString(R.string.booking_unreturned);
        String condition;
        if ("no_damage".equals(booking.getInspectionCondition())) {
            condition = getString(R.string.booking_condition_no_damage);
        } else if ("damaged".equals(booking.getInspectionCondition())) {
            condition = getString(R.string.booking_condition_damaged);
        } else {
            condition = getString(R.string.booking_condition_not_inspected);
        }
        NumberFormat damageMoney = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        damageMoney.setCurrency(Currency.getInstance("INR"));
        String charge = damageMoney.format(BigDecimal.valueOf(booking.getInspectionChargeMinor(), 2));
        String description = booking.getDamageDescription().isEmpty()
                ? getString(R.string.booking_damage_description_none)
                : booking.getDamageDescription();
        String inspectedAt;
        if (booking.getInspectedAt() == null) {
            inspectedAt = getString(R.string.booking_inspection_not_recorded);
        } else {
            SimpleDateFormat inspectionTime = new SimpleDateFormat(
                    "d MMM yyyy, h:mm a", Locale.getDefault());
            inspectionTime.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            inspectedAt = inspectionTime.format(booking.getInspectedAt().toDate());
        }
        return getString(R.string.booking_inspection_summary,
                returnStatus, condition, description, charge, inspectedAt);
    }
}

