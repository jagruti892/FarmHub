
package in.farmhub.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class EditEquipmentActivity extends Activity {

    private EditText nameInput;
    private EditText descriptionInput;
    private EditText categoryInput;
    private EditText priceInput;
    private EditText locationInput;
    private EditText imageUrlInput;

    private Button saveButton;
    private Button cancelButton;
    private ProgressBar progressBar;
    private TextView statusMessage;

    private FirebaseFirestore db;
    private EquipmentRepository repository;

    private String equipmentId;
    private String ownerUid;

    private boolean loading = true;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_equipment);

        equipmentId = getIntent().getStringExtra("equipmentId");

        if (equipmentId == null || equipmentId.trim().isEmpty()) {
            Toast.makeText(this, "Equipment ID is missing",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Please log in first",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ownerUid = FirebaseAuth.getInstance()
                .getCurrentUser().getUid();

        db = FirebaseFirestore.getInstance();
        repository = new EquipmentRepository();

        bindViews();

        saveButton.setEnabled(false);
        cancelButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveChanges());

        loadEquipment();
    }

    private void bindViews() {
        nameInput = findViewById(R.id.name_input);
        descriptionInput = findViewById(R.id.description_input);
        categoryInput = findViewById(R.id.category_input);
        priceInput = findViewById(R.id.price_input);
        locationInput = findViewById(R.id.location_input);
        imageUrlInput = findViewById(R.id.image_url_input);

        saveButton = findViewById(R.id.save_button);
        cancelButton = findViewById(R.id.cancel_button);
        progressBar = findViewById(R.id.progress_bar);
        statusMessage = findViewById(R.id.status_message);
    }

    private void loadEquipment() {
        showLoading(true);
        showStatus("Loading equipment details...", true);

        db.collection("equipment")
                .document(equipmentId)
                .get()
                .addOnSuccessListener(document -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (!document.exists()) {
                        showError("Equipment was not found.");
                        return;
                    }

                    String actualOwner = document.getString("ownerUid");

                    if (actualOwner == null
                            || !ownerUid.equals(actualOwner)) {
                        showError(
                                "You are not allowed to edit this equipment.");
                        return;
                    }

                    nameInput.setText(document.getString("name"));
                    descriptionInput.setText(
                            document.getString("description"));
                    categoryInput.setText(
                            document.getString("categoryId"));
                    locationInput.setText(
                            document.getString("location"));

                    String imageUrl = document.getString("imageUrl");
                    imageUrlInput.setText(
                            imageUrl == null ? "" : imageUrl);

                    Object priceObject = document.get("dailyPriceMinor");

                    if (priceObject instanceof Number) {
                        long priceMinor = ((Number) priceObject).longValue();

                        priceInput.setText(String.format(
                                Locale.US,
                                "%.2f",
                                priceMinor / 100.0));
                    }

                    loading = false;
                    showLoading(false);
                    showStatus("", false);
                    saveButton.setEnabled(true);
                })
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    showError("Could not load equipment details.");
                });
    }

    private void saveChanges() {
        if (loading || saving) {
            return;
        }

        String name = nameInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();
        String category = categoryInput.getText().toString().trim();
        String priceText = priceInput.getText().toString().trim();
        String location = locationInput.getText().toString().trim();
        String imageUrl = imageUrlInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Enter the equipment name");
            nameInput.requestFocus();
            return;
        }

        if (description.isEmpty()) {
            descriptionInput.setError("Enter a description");
            descriptionInput.requestFocus();
            return;
        }

        if (category.isEmpty()) {
            categoryInput.setError("Enter the category ID");
            categoryInput.requestFocus();
            return;
        }

        if (location.isEmpty()) {
            locationInput.setError("Enter the location");
            locationInput.requestFocus();
            return;
        }

        if (!imageUrl.isEmpty()
                && !imageUrl.matches("(?i)^https://\\S+$")) {
            imageUrlInput.setError("Enter a valid HTTPS image URL");
            imageUrlInput.requestFocus();
            return;
        }

        final long priceMinor;

        try {
            double rupees = Double.parseDouble(priceText);

            if (!Double.isFinite(rupees)
                    || rupees <= 0
                    || rupees > Long.MAX_VALUE / 100.0) {
                throw new NumberFormatException();
            }

            priceMinor = Math.round(rupees * 100);

            if (priceMinor <= 0) {
                throw new NumberFormatException();
            }

        } catch (NumberFormatException exception) {
            priceInput.setError(
                    "Enter a valid daily price greater than ₹0");
            priceInput.requestFocus();
            return;
        }

        saving = true;
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);

        showLoading(true);
        showStatus("Saving your changes...", true);

        repository.updateEquipment(
                equipmentId,
                name,
                description,
                category,
                imageUrl,
                priceMinor,
                location,
                new EquipmentRepository.ActionCallback() {
                    @Override
                    public void onComplete(Exception error) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            saving = false;
                            showLoading(false);

                            if (error != null) {
                                saveButton.setEnabled(true);
                                cancelButton.setEnabled(true);

                                String message = error.getMessage();

                                showStatus(
                                        message != null && !message.isEmpty()
                                                ? message
                                                : "Could not update equipment.",
                                        true);
                                return;
                            }

                            Toast.makeText(
                                    EditEquipmentActivity.this,
                                    "Equipment updated successfully.",
                                    Toast.LENGTH_SHORT).show();

                            setResult(RESULT_OK);
                            finish();
                        });
                    }
                });
    }

    private void showLoading(boolean visible) {
        progressBar.setVisibility(
                visible ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message, boolean visible) {
        statusMessage.setText(message);
        statusMessage.setVisibility(
                visible && !message.isEmpty()
                        ? View.VISIBLE
                        : View.GONE);
    }

    private void showError(String message) {
        loading = true;
        showLoading(false);
        showStatus(message, true);
        saveButton.setEnabled(false);

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
