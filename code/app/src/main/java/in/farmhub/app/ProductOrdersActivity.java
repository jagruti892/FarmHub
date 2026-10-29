package in.farmhub.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class ProductOrdersActivity extends Activity {
    private final int green = Color.rgb(24, 63, 43);
    private final int background = Color.rgb(244, 243, 232);
    private LinearLayout list;
    private TextView status;
    private ProgressBar progress;
    private FirebaseFirestore db;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        user = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("My Orders");
        title.setTextSize(28);
        title.setTextColor(green);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your farm product orders and payment status");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, 8, 0, 16);
        root.addView(subtitle);

        status = new TextView(this);
        status.setText("Loading product orders...");
        status.setTextSize(15);
        root.addView(status);

        progress = new ProgressBar(this);
        root.addView(progress);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        Button refresh = new Button(this);
        refresh.setText("Refresh Orders");
        refresh.setOnClickListener(v -> loadOrders());
        root.addView(refresh);

        Button back = new Button(this);
        back.setText("Back");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        setContentView(scroll);

        if (user == null) {
            status.setText("Please sign in to view your orders.");
            progress.setVisibility(View.GONE);
        } else {
            loadOrders();
        }
    }

    private void loadOrders() {
        if (user == null) return;

        progress.setVisibility(View.VISIBLE);
        status.setText("Loading product orders...");
        list.removeAllViews();

        db.collection("orders")
                .whereEqualTo("customerUid", user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    progress.setVisibility(View.GONE);
                    list.removeAllViews();

                    if (snapshot.isEmpty()) {
                        status.setText("No product orders yet.");
                        return;
                    }

                    status.setText("Product orders: " + snapshot.size());

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(16, 16, 16, 16);
                        card.setBackgroundColor(Color.WHITE);

                        LinearLayout.LayoutParams params =
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.bottomMargin = 16;
                        list.addView(card, params);

                        TextView heading = new TextView(this);
                        heading.setText("Order #" + doc.getId());
                        heading.setTextColor(green);
                        heading.setTextSize(17);
                        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                        card.addView(heading);

                        StringBuilder details = new StringBuilder();
                        Object items = doc.get("items");
                        if (items instanceof java.util.List) {
                            for (Object entry : (java.util.List<?>) items) {
                                if (entry instanceof java.util.Map) {
                                    java.util.Map<?, ?> item =
                                            (java.util.Map<?, ?>) entry;
                                    details.append(item.get("name"))
                                            .append(" x ")
                                            .append(item.get("quantity"))
                                            .append("\n");
                                }
                            }
                        }

                        if (details.length() > 0) {
                            TextView products = new TextView(this);
                            products.setText(details.toString().trim());
                            products.setPadding(0, 8, 0, 8);
                            card.addView(products);
                        }

                        Number total = doc.getLong("totalMinor");
                        TextView amount = new TextView(this);
                        amount.setText("Total: " + String.format(
                                Locale.US, "%.2f",
                                (total == null ? 0 : total.longValue()) / 100.0));
                        card.addView(amount);

                        TextView payment = new TextView(this);
                        payment.setText("Payment: "
                                + safe(doc.getString("paymentMethod"), "COD"));
                        card.addView(payment);

                        TextView orderStatus = new TextView(this);
                        orderStatus.setText("Status: "
                                + safe(doc.getString("status"), "Placed"));
                        card.addView(orderStatus);

                        Date created = doc.getDate("createdAt");
                        if (created != null) {
                            TextView date = new TextView(this);
                            date.setText("Placed: "
                                    + DateFormat.getDateTimeInstance().format(created));
                            card.addView(date);
                        }
                    }
                })
                .addOnFailureListener(error -> {
                    progress.setVisibility(View.GONE);
                    status.setText("Could not load orders. Check your connection and Firestore rules.");
                    Toast.makeText(this, error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
