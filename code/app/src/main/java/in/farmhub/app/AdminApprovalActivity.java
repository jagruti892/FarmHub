
package in.farmhub.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AdminApprovalActivity extends Activity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private LinearLayout applicationsContainer;
    private TextView emptyApplications;
    private String adminUid;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_admin_approval);

        adminUid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null
                : FirebaseAuth.getInstance().getCurrentUser().getUid();

        applicationsContainer = findViewById(R.id.applications_container);
        emptyApplications = findViewById(R.id.empty_applications);

        Button revenueButton = findViewById(R.id.revenue_button);
        revenueButton.setOnClickListener(v ->
                startActivity(new Intent(this, RevenueActivity.class)));

        loadApplications();
    }

    private TextView makeText(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.parseColor("#203B2B"));
        text.setPadding(0, 6, 0, 6);

        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }

        return text;
    }

    private void loadApplications() {
        if (adminUid == null) {
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        emptyApplications.setVisibility(View.VISIBLE);
        emptyApplications.setText("Loading applications...");
        applicationsContainer.removeAllViews();

        db.collection("admins").document(adminUid).get()
                .addOnSuccessListener(admin -> {
                    if (!admin.exists()
                            || !Boolean.TRUE.equals(admin.getBoolean("active"))) {
                        Toast.makeText(
                                this,
                                "Admin access is not configured for this account.",
                                Toast.LENGTH_LONG
                        ).show();
                        finish();
                        return;
                    }

                    db.collection("ownerApplications")
                            .whereEqualTo("status", "pending")
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                applicationsContainer.removeAllViews();

                                if (snapshot.isEmpty()) {
                                    emptyApplications.setText(
                                            "You're all caught up. No pending owner applications."
                                    );
                                    emptyApplications.setVisibility(View.VISIBLE);
                                    return;
                                }

                                emptyApplications.setVisibility(View.GONE);

                                for (DocumentSnapshot app : snapshot.getDocuments()) {
                                    addApplicationCard(app);
                                }
                            })
                            .addOnFailureListener(e -> {
                                emptyApplications.setText(
                                        "Could not load applications. Please try again."
                                );
                                emptyApplications.setVisibility(View.VISIBLE);
                                Toast.makeText(
                                        this,
                                        "Could not load applications: " + e.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Could not verify admin access.",
                                Toast.LENGTH_LONG
                        ).show());
    }

    private void addApplicationCard(DocumentSnapshot app) {
        String ownerUid = app.getId();
        String email = app.getString("email");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(18, 16, 18, 16);
        card.setBackgroundResource(R.drawable.bg_form_card);
        card.setElevation(2f);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        cardParams.setMargins(0, 8, 0, 12);
        applicationsContainer.addView(card, cardParams);

        TextView label = makeText("OWNER APPLICATION", 12, true);
        label.setTextColor(Color.parseColor("#52745B"));
        card.addView(label);

        TextView ownerEmail = makeText(
                email == null || email.trim().isEmpty()
                        ? "Email not available"
                        : email,
                17,
                true
        );
        ownerEmail.setMaxLines(3);
        card.addView(ownerEmail);

        TextView status = makeText("PENDING REVIEW", 12, true);
        status.setBackgroundResource(R.drawable.bg_status_message);
        status.setPadding(12, 8, 12, 8);

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        statusParams.setMargins(0, 8, 0, 12);
        card.addView(status, statusParams);

        Button approve = new Button(this);
        approve.setText("Approve Owner");
        approve.setAllCaps(false);
        approve.setOnClickListener(v -> decide(ownerUid, true));
        card.addView(approve, matchWidthParams());

        Button reject = new Button(this);
        reject.setText("Reject Application");
        reject.setAllCaps(false);
        reject.setBackgroundResource(R.drawable.bg_secondary_button);
        reject.setTextColor(Color.parseColor("#365C40"));

        LinearLayout.LayoutParams rejectParams = matchWidthParams();
        rejectParams.topMargin = 8;
        card.addView(reject, rejectParams);

        reject.setOnClickListener(v -> decide(ownerUid, false));
    }

    private LinearLayout.LayoutParams matchWidthParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private void decide(String ownerUid, boolean approve) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", approve ? "approved" : "rejected");
        update.put("reviewedAt", FieldValue.serverTimestamp());
        update.put("reviewedBy", adminUid);

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentReference appRef =
                    db.collection("ownerApplications").document(ownerUid);

            com.google.firebase.firestore.DocumentSnapshot app =
                    transaction.get(appRef);

            if (!app.exists() || !"pending".equals(app.getString("status"))) {
                throw new IllegalStateException(
                        "Application is no longer pending."
                );
            }

            transaction.update(appRef, update);

            if (approve) {
                Map<String, Object> approval = new HashMap<>();
                approval.put("status", "approved");
                approval.put("approvedAt", FieldValue.serverTimestamp());
                approval.put("approvedBy", adminUid);

                transaction.set(
                        db.collection("ownerApprovals").document(ownerUid),
                        approval
                );
            }

            return null;
        }).addOnSuccessListener(v -> {
            Toast.makeText(
                    this,
                    approve ? "Owner approved." : "Application rejected.",
                    Toast.LENGTH_SHORT
            ).show();
            loadApplications();
        }).addOnFailureListener(e ->
                Toast.makeText(
                        this,
                        "Could not update application: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
    }
}
