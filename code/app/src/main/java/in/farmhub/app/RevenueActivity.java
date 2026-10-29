
package in.farmhub.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.Locale;

public class RevenueActivity extends Activity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private TextView totalCommission;
    private TextView productCommission;
    private TextView rentalCommission;
    private TextView orderCount;
    private TextView bookingCount;
    private TextView revenueMessage;

    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_revenue);

        totalCommission = findViewById(R.id.total_commission);
        productCommission = findViewById(R.id.product_commission);
        rentalCommission = findViewById(R.id.rental_commission);
        orderCount = findViewById(R.id.order_count);
        bookingCount = findViewById(R.id.booking_count);
        revenueMessage = findViewById(R.id.revenue_message);

        uid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();

        loadRevenue();
    }

    private String money(long minorUnits) {
        NumberFormat format =
                NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        return format.format(minorUnits / 100.0);
    }

    private void loadRevenue() {
        if (uid == null) {
            Toast.makeText(
                    this,
                    "Please sign in again.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        revenueMessage.setText("Checking admin access...");
        revenueMessage.setVisibility(View.VISIBLE);

        db.collection("admins")
                .document(uid)
                .get()
                .addOnSuccessListener(admin -> {
                    if (!admin.exists()
                            || !Boolean.TRUE.equals(admin.getBoolean("active"))) {
                        Toast.makeText(
                                this,
                                "Admin access required.",
                                Toast.LENGTH_LONG
                        ).show();
                        finish();
                        return;
                    }

                    loadOrders();
                })
                .addOnFailureListener(e -> showError(
                        "Could not verify admin access. Check your connection and rules."
                ));
    }

    private void loadOrders() {
        revenueMessage.setText("Loading product-order data...");

        db.collection("orders")
                .get()
                .addOnSuccessListener(orders -> {
                    long orderCommissionMinor = 0;

                    for (DocumentSnapshot document : orders.getDocuments()) {
                        Long commission = document.getLong("commissionMinor");

                        if (commission != null) {
                            orderCommissionMinor += commission;
                        }
                    }

                    loadBookings(orderCommissionMinor, orders.size());
                })
                .addOnFailureListener(e -> showError(
                        "Could not load orders. Check your Firestore rules."
                ));
    }

    private void loadBookings(long orderCommissionMinor, int numberOfOrders) {
        revenueMessage.setText("Loading equipment-rental data...");

        db.collection("bookings")
                .get()
                .addOnSuccessListener(bookings -> {
                    long rentalCommissionMinor = 0;

                    for (DocumentSnapshot document : bookings.getDocuments()) {
                        Long commission = document.getLong("commissionMinor");

                        if (commission != null) {
                            rentalCommissionMinor += commission;
                        }
                    }

                    displayRevenue(
                            orderCommissionMinor,
                            rentalCommissionMinor,
                            numberOfOrders,
                            bookings.size()
                    );
                })
                .addOnFailureListener(e -> showError(
                        "Could not load bookings. Check your Firestore rules."
                ));
    }

    private void displayRevenue(
            long orderCommissionMinor,
            long rentalCommissionMinor,
            int numberOfOrders,
            int numberOfBookings
    ) {
        long totalMinor = orderCommissionMinor + rentalCommissionMinor;

        productCommission.setText(money(orderCommissionMinor));
        rentalCommission.setText(money(rentalCommissionMinor));
        totalCommission.setText(money(totalMinor));

        orderCount.setText("Orders counted: " + numberOfOrders);
        bookingCount.setText("Bookings counted: " + numberOfBookings);

        revenueMessage.setText(
                "Revenue summary updated. Commission is calculated from recorded " +
                "commission fields; it does not represent money collected."
        );
    }

    private void showError(String message) {
        revenueMessage.setText(message);
        revenueMessage.setVisibility(View.VISIBLE);

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
