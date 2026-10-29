package in.farmhub.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private FirebaseAuth firebaseAuth;

    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private TextView confirmPasswordLabel;
    private TextView formTitle;
    private TextView formSubtitle;
    private TextView modePrompt;
    private TextView statusMessage;
    private Button submitButton;
    private Button modeButton;
    private ProgressBar progressBar;
    private Spinner roleSpinner;
    private TextView roleLabel;
    private FirebaseFirestore firestore;

    private boolean isRegistrationMode;
    private boolean requestInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        bindViews();
        showCurrentMode();

        submitButton.setOnClickListener(view -> submitForm());
        modeButton.setOnClickListener(view -> switchMode());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (firebaseAuth.getCurrentUser() != null) {
            openHome();
        }
    }

    private void bindViews() {
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        confirmPasswordInput = findViewById(R.id.confirm_password_input);
        confirmPasswordLabel = findViewById(R.id.confirm_password_label);
        formTitle = findViewById(R.id.form_title);
        formSubtitle = findViewById(R.id.form_subtitle);
        modePrompt = findViewById(R.id.mode_prompt);
        statusMessage = findViewById(R.id.status_message);
        submitButton = findViewById(R.id.submit_button);
        modeButton = findViewById(R.id.mode_button);
        progressBar = findViewById(R.id.progress_bar);
        roleSpinner = findViewById(R.id.role_spinner);
        roleLabel = findViewById(R.id.role_label);
        ArrayAdapter<CharSequence> roleAdapter = ArrayAdapter.createFromResource(this,
                R.array.registration_roles, android.R.layout.simple_spinner_item);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roleSpinner.setAdapter(roleAdapter);
    }

    private void switchMode() {
        if (requestInProgress) {
            return;
        }

        isRegistrationMode = !isRegistrationMode;
        clearMessages();
        showCurrentMode();
    }

    private void showCurrentMode() {
        if (isRegistrationMode) {
            formTitle.setText(R.string.create_account_title);
            formSubtitle.setText(R.string.create_account_subtitle);
            modePrompt.setText(R.string.mode_prompt_register);
            submitButton.setText(R.string.create_account_button);
            modeButton.setText(R.string.have_account_button);
            confirmPasswordLabel.setVisibility(View.VISIBLE);
            confirmPasswordInput.setVisibility(View.VISIBLE);
            roleSpinner.setVisibility(View.VISIBLE);
            roleLabel.setVisibility(View.VISIBLE);
        } else {
            formTitle.setText(R.string.login_title);
            formSubtitle.setText(R.string.login_subtitle);
            modePrompt.setText(R.string.mode_prompt_login);
            submitButton.setText(R.string.login_button);
            modeButton.setText(R.string.create_account_option);
            confirmPasswordLabel.setVisibility(View.GONE);
            confirmPasswordInput.setVisibility(View.GONE);
            roleSpinner.setVisibility(View.GONE);
            roleLabel.setVisibility(View.GONE);
        }
    }

    private void submitForm() {
        clearMessages();

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError(getString(R.string.error_valid_email));
            emailInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError(getString(R.string.error_password_required));
            passwordInput.requestFocus();
            return;
        }

        if (isRegistrationMode) {
            if (password.length() < 6) {
                passwordInput.setError(getString(R.string.error_password_short));
                passwordInput.requestFocus();
                return;
            }

            String confirmation = confirmPasswordInput.getText().toString();
            if (!password.equals(confirmation)) {
                confirmPasswordInput.setError(getString(R.string.error_password_mismatch));
                confirmPasswordInput.requestFocus();
                return;
            }

            createAccount(email, password);
        } else {
            signIn(email, password);
        }
    }

    private void createAccount(String email, String password) {
        setRequestInProgress(true);
        String selectedRole = roleSpinner.getSelectedItemPosition() == 1 ? "owner" : "customer";
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful() || firebaseAuth.getCurrentUser() == null) {
                        setRequestInProgress(false);
                        showStatus(getFriendlyError(task.getException()), true);
                        return;
                    }
                    String uid = firebaseAuth.getCurrentUser().getUid();
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("uid", uid);
                    profile.put("email", email);
                    profile.put("role", selectedRole);
                    profile.put("createdAt", FieldValue.serverTimestamp());
                    firestore.collection("userProfiles").document(uid).set(profile)
                            .addOnSuccessListener(unused -> {
                                if ("owner".equals(selectedRole)) {
                                    Map<String, Object> application = new HashMap<>();
                                    application.put("uid", uid);
                                    application.put("email", email);
                                    application.put("status", "pending");
                                    application.put("submittedAt", FieldValue.serverTimestamp());
                                    firestore.collection("ownerApplications").document(uid).set(application)
                                            .addOnSuccessListener(done -> {
                                                setRequestInProgress(false);
                                                firebaseAuth.signOut();
                                                showStatus("Owner application submitted. Please wait for admin approval before signing in to owner features.", false);
                                                isRegistrationMode = false;
                                                showCurrentMode();
                                            })
                                            .addOnFailureListener(error -> {
                                                setRequestInProgress(false);
                                                showStatus("Account created, but the owner application could not be submitted. Contact FarmHub support.", true);
                                            });
                                } else {
                                    setRequestInProgress(false);
                                    openHome();
                                }
                            })
                            .addOnFailureListener(error -> {
                                setRequestInProgress(false);
                                showStatus("Account created, but the profile could not be saved: " + error.getMessage(), true);
                            });
                });
    }

    private void signIn(String email, String password) {
        setRequestInProgress(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setRequestInProgress(false);
                    if (task.isSuccessful()) {
                        openHome();
                    } else {
                        showStatus(getFriendlyError(task.getException()), true);
                    }
                });
    }

    private String getFriendlyError(Exception exception) {
        if (exception instanceof FirebaseNetworkException) {
            return getString(R.string.error_network);
        }
        if (exception instanceof FirebaseTooManyRequestsException) {
            return getString(R.string.error_too_many_requests);
        }
        if (exception instanceof FirebaseAuthUserCollisionException) {
            return getString(R.string.error_email_in_use);
        }
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return getString(R.string.error_account_not_found);
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return getString(R.string.error_credentials);
        }
        if (exception instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) exception).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return getString(R.string.error_provider_disabled);
            }
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
                return getString(R.string.error_email_in_use);
            }
            if ("ERROR_WEAK_PASSWORD".equals(code)) {
                return getString(R.string.error_password_short);
            }
        }
        return getString(R.string.error_try_again);
    }

    private void setRequestInProgress(boolean inProgress) {
        requestInProgress = inProgress;
        submitButton.setEnabled(!inProgress);
        modeButton.setEnabled(!inProgress);
        progressBar.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        submitButton.setAlpha(inProgress ? 0.65f : 1.0f);
    }

    private void showStatus(String message, boolean isError) {
        statusMessage.setText(message);
        statusMessage.setTextColor(getColor(isError ? R.color.error_red : R.color.leaf_green_dark));
        statusMessage.setVisibility(View.VISIBLE);
    }

    private void openHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void clearMessages() {
        emailInput.setError(null);
        passwordInput.setError(null);
        confirmPasswordInput.setError(null);
        statusMessage.setText(null);
        statusMessage.setVisibility(View.GONE);
    }
}
