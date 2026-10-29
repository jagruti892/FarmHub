package in.farmhub.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Checks the signed-in role before opening role-specific sections. */
public class RoleDestinationActivity extends Activity {
    public static final String EXTRA_DESTINATION = "in.farmhub.app.extra.DESTINATION";

    public static final String DESTINATION_PRODUCTS = "products";
    public static final String DESTINATION_RENTAL = "rental";
    public static final String DESTINATION_ORDERS = "orders";
    public static final String DESTINATION_PRODUCT_ORDERS = "product_orders";
    public static final String DESTINATION_LISTINGS = "listings";
    public static final String DESTINATION_MANAGE_PRODUCTS = "manage_products";
    public static final String DESTINATION_BOOKINGS = "bookings";
    public static final String DESTINATION_ADMIN = "admin";
    public static final String DESTINATION_PROFILE = "profile";

    private TextView destinationTitle;
    private TextView destinationMessage;
    private Button homeButton;
    private Button signOutButton;

    private FirebaseAuth firebaseAuth;
    private FirebaseUser currentUser;
    private DestinationInfo destinationInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            returnToLogin();
            return;
        }

        destinationInfo = getDestinationInfo(getIntent().getStringExtra(EXTRA_DESTINATION));
        if (destinationInfo == null) {
            returnToHome();
            return;
        }

        setContentView(R.layout.activity_destination);
        destinationTitle = findViewById(R.id.destination_title);
        destinationMessage = findViewById(R.id.destination_message);
        homeButton = findViewById(R.id.destination_home_button);
        signOutButton = findViewById(R.id.destination_sign_out_button);
        homeButton.setOnClickListener(view -> returnToHome());
        signOutButton.setOnClickListener(view -> signOut());

        destinationTitle.setText(destinationInfo.titleResource);
        if (destinationInfo.role == null) {
            signOutButton.setVisibility(View.VISIBLE);
            showProfile();
        } else {
            verifyRoleAndShowPlaceholder();
        }
    }

    private void verifyRoleAndShowPlaceholder() {
        destinationMessage.setText(R.string.destination_checking_access);
        RoleAccess.loadRole(this, currentUser, (role, error) -> {
            if (error != null) {
                destinationMessage.setText(R.string.destination_role_check_failed);
                return;
            }
            if (role != destinationInfo.role) {
                destinationTitle.setText(R.string.destination_access_restricted_title);
                destinationMessage.setText(R.string.destination_access_restricted_message);
                return;
            }

            if (DESTINATION_RENTAL.equals(
                    getIntent().getStringExtra(EXTRA_DESTINATION))) {
                startActivity(new Intent(this, EquipmentCatalogActivity.class));
                finish();
                return;
            }

            if (DESTINATION_PRODUCT_ORDERS.equals(getIntent().getStringExtra(EXTRA_DESTINATION))) {
                startActivity(new Intent(this, ProductOrdersActivity.class));
                finish();
                return;
            }
            if (DESTINATION_ORDERS.equals(
                    getIntent().getStringExtra(EXTRA_DESTINATION))) {
                startActivity(BookingListActivity.customerIntent(this));
                finish();
                return;
            }
            if (DESTINATION_BOOKINGS.equals(
                    getIntent().getStringExtra(EXTRA_DESTINATION))) {
                startActivity(BookingListActivity.ownerIntent(this));
                finish();
                return;
            }
            if (DESTINATION_LISTINGS.equals(
                    getIntent().getStringExtra(EXTRA_DESTINATION))) {
                startActivity(new Intent(this, OwnerEquipmentActivity.class));
                finish();
                return;
            }
            if (DESTINATION_MANAGE_PRODUCTS.equals(
                    getIntent().getStringExtra(EXTRA_DESTINATION))) {
                startActivity(new Intent(this, SellerProductsActivity.class));
                finish();
                return;
            }

            destinationMessage.setText(destinationInfo.messageResource);
        });
    }

    private void showProfile() {
        TextView profileAvatar = findViewById(R.id.profile_avatar);
        TextView profileName = findViewById(R.id.profile_name);
        TextView profileEmail = findViewById(R.id.profile_email);
        TextView profileRole = findViewById(R.id.profile_role);
        TextView overviewTitle = findViewById(R.id.account_overview_title);
        TextView overviewMessage = findViewById(R.id.account_overview_message);
        TextView helpTitle = findViewById(R.id.profile_help_title);
        TextView helpMessage = findViewById(R.id.profile_help_message);

        String email = currentUser.getEmail();
        if (email == null || email.trim().isEmpty()) {
            email = getString(R.string.profile_email_unavailable);
        }

        String name = currentUser.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            name = email.equals(getString(R.string.profile_email_unavailable))
                    ? "FarmHub User"
                    : email.substring(0, email.indexOf('@') > 0
                            ? email.indexOf('@')
                            : email.length());
        }

        String initials = "";
        String[] parts = name.trim().split("\\s+");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            initials += parts[0].substring(0, 1).toUpperCase();
        }
        if (parts.length > 1 && !parts[parts.length - 1].isEmpty()) {
            initials += parts[parts.length - 1].substring(0, 1).toUpperCase();
        }

        profileAvatar.setText("👤");
        profileName.setText(name);
        profileEmail.setText(email);

        profileAvatar.setVisibility(View.VISIBLE);
        profileName.setVisibility(View.VISIBLE);
        profileEmail.setVisibility(View.VISIBLE);
        profileRole.setVisibility(View.VISIBLE);

        overviewTitle.setVisibility(View.VISIBLE);
        overviewMessage.setVisibility(View.VISIBLE);
        helpTitle.setVisibility(View.VISIBLE);
        helpMessage.setVisibility(View.VISIBLE);
        destinationMessage.setVisibility(View.GONE);

        overviewMessage.setText(
                "Email: " + email
                + "\n\nAccount status: Signed in"
                + "\n\nVerified role: Shown above"
                + "\n\nYour account role is managed by FarmHub."
        );

        destinationMessage.setGravity(android.view.Gravity.START);
        destinationMessage.setText(
                "Account details\n\n"
                        + "Your account information is shown above.\n\n"
                        + "For security, account roles are managed by FarmHub support.");

        profileRole.setText("Verifying account role...");

        RoleAccess.loadRole(this, currentUser, (role, error) -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (error != null || role == null
                    || role == RoleAccess.Role.UNASSIGNED) {
                profileRole.setText("Role unavailable");
                return;
            }

            switch (role) {
                case CUSTOMER:
                    profileRole.setText("Customer");
                    break;
                case OWNER:
                    profileRole.setText("Equipment Owner");
                    break;
                case ADMIN:
                    profileRole.setText("Administrator");
                    break;
                default:
                    profileRole.setText("Role unavailable");
                    break;
            }
        });
    }

    private DestinationInfo getDestinationInfo(String destination) {
        if (DESTINATION_RENTAL.equals(destination)) {
            return new DestinationInfo(R.string.nav_rental, R.string.placeholder_rental,
                    RoleAccess.Role.CUSTOMER);
        }
        if (DESTINATION_PRODUCT_ORDERS.equals(destination)) {
            return new DestinationInfo(
                    R.string.nav_product_orders,
                    R.string.placeholder_orders,
                    RoleAccess.Role.CUSTOMER);
        }

        if (DESTINATION_ORDERS.equals(destination)) {
            return new DestinationInfo(R.string.nav_orders, R.string.placeholder_orders,
                    RoleAccess.Role.CUSTOMER);
        }
        if (DESTINATION_LISTINGS.equals(destination)) {
            return new DestinationInfo(R.string.nav_listings, R.string.placeholder_listings,
                    RoleAccess.Role.OWNER);
        }
        if (DESTINATION_MANAGE_PRODUCTS.equals(destination)) {
            return new DestinationInfo(R.string.nav_manage_products,
                    R.string.nav_manage_products_subtitle, RoleAccess.Role.OWNER);
        }
        if (DESTINATION_BOOKINGS.equals(destination)) {
            return new DestinationInfo(R.string.nav_bookings, R.string.placeholder_bookings,
                    RoleAccess.Role.OWNER);
        }
        if (DESTINATION_ADMIN.equals(destination)) {
            return new DestinationInfo(R.string.nav_admin, R.string.placeholder_admin,
                    RoleAccess.Role.ADMIN);
        }
        if (DESTINATION_PROFILE.equals(destination)) {
            return new DestinationInfo(R.string.nav_profile, 0, null);
        }
        return null;
    }

    private void signOut() {
        firebaseAuth.signOut();
        returnToLogin();
    }

    private void returnToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static class DestinationInfo {
        final int titleResource;
        final int messageResource;
        final RoleAccess.Role role;

        DestinationInfo(int titleResource, int messageResource, RoleAccess.Role role) {
            this.titleResource = titleResource;
            this.messageResource = messageResource;
            this.role = role;
        }
    }
}

