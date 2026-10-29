package in.farmhub.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class AddEquipmentActivity extends Activity {
    private EditText name, description, category, price, location, imageUrl;
    private Button save;
    private ProgressBar progress;

    private int green = Color.rgb(48, 105, 65);
    private int ink = Color.rgb(43, 55, 43);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(250, 248, 239));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);

        TextView heading = new TextView(this);
        heading.setText("Add Equipment");
        heading.setTextSize(25);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(ink);
        page.addView(heading);

        TextView note = new TextView(this);
        note.setText("Add a machine or tool for customers to rent.");
        note.setTextSize(14);
        note.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams noteLp = params();
        noteLp.bottomMargin = dp(12);
        page.addView(note, noteLp);

        name = field(page, "Equipment name", InputType.TYPE_CLASS_TEXT);
        description = field(page, "Description", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        category = field(page, "Category ID (e.g. tractors)", InputType.TYPE_CLASS_TEXT);
        price = field(page, "Rental price per day (₹)", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        location = field(page, "Location", InputType.TYPE_CLASS_TEXT);
        imageUrl = field(page, "Image URL (optional, HTTPS)", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = params();
        progressLp.topMargin = dp(12);
        page.addView(progress, progressLp);

        save = new Button(this);
        save.setText("Save Equipment");
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setBackgroundColor(green);
        LinearLayout.LayoutParams saveLp = params();
        saveLp.topMargin = dp(14);
        page.addView(save, saveLp);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> finish());
        page.addView(cancel, params());

        setContentView(scroll);
        save.setOnClickListener(v -> submit());
    }

    private EditText field(LinearLayout parent, String hint, int type) {
        EditText input = new EditText(this);
        input.setSingleLine((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        input.setHint(hint);
        input.setTextSize(16);
        input.setTextColor(ink);
        input.setInputType(type);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), Color.rgb(220, 222, 210));
        input.setBackground(bg);
        LinearLayout.LayoutParams lp = params();
        lp.bottomMargin = dp(8);
        parent.addView(input, lp);
        return input;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void submit() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String n = name.getText().toString().trim();
        String d = description.getText().toString().trim();
        String c = category.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        String loc = location.getText().toString().trim();
        String url = imageUrl.getText().toString().trim();
        long minor;

        if (n.isEmpty() || d.isEmpty() || c.isEmpty() || loc.isEmpty()) {
            Toast.makeText(this, "Complete all required fields.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!url.isEmpty() && (!url.startsWith("https://")
                || android.net.Uri.parse(url).getHost() == null)) {
            imageUrl.setError("Enter a valid HTTPS image URL.");
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(price.getText().toString().trim());
            if (amount.signum() <= 0) throw new NumberFormatException();
            minor = amount.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2).longValueExact();
        } catch (Exception e) {
            price.setError("Enter a valid positive price (up to 2 decimal places).");
            return;
        }

        setBusy(true);
        RoleAccess.loadRole(this, user, (role, roleError) -> {
            if (isFinishing()) return;
            if (roleError != null || role != RoleAccess.Role.OWNER) {
                setBusy(false);
                Toast.makeText(this, "Only verified owners can add equipment.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("ownerUid", user.getUid());
            data.put("name", n);
            data.put("description", d);
            data.put("categoryId", c);
            data.put("imageUrl", url);
            data.put("dailyPriceMinor", minor);
            data.put("currency", "INR");
            data.put("location", loc);
            data.put("publicationStatus", "published");
            data.put("availabilityStatus", "available");
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance().collection("equipment")
                    .add(data)
                    .addOnCompleteListener(task -> {
                        if (isFinishing()) return;
                        setBusy(false);
                        if (!task.isSuccessful()) {
                            String reason = task.getException() == null
                                    ? "Please try again."
                                    : task.getException().getMessage();
                            Toast.makeText(this, "Could not save equipment: " + reason,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(this, "Equipment added and published.",
                                Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    });
        });
    }

    private void setBusy(boolean busy) {
        save.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        save.setText(busy ? "Saving..." : "Save Equipment");
    }
}