package in.farmhub.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Signed-in landing screen. A missing role claim gets basic Customer navigation only. */
public class HomeActivity extends Activity {
    private FirebaseAuth firebaseAuth;
    private FirebaseUser currentUser;

    private TextView homeTitle;
    private TextView homeSubtitle;
    private TextView userEmail;
    private TextView roleLabel;
    private TextView roleMessage;
    private LinearLayout navigationItems;
    private ProgressBar roleProgress;
    private Button retryButton;
    private Button signOutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            returnToLogin();
            return;
        }

        bindViews();
        userEmail.setText(currentUser.getEmail());
        retryButton.setOnClickListener(view -> loadRole());
        signOutButton.setOnClickListener(view -> signOut());
        loadRole();
    }

    private void bindViews() {
        homeTitle = findViewById(R.id.home_title);
        homeSubtitle = findViewById(R.id.home_subtitle);
        userEmail = findViewById(R.id.user_email);
        roleLabel = findViewById(R.id.role_label);
        roleMessage = findViewById(R.id.role_message);
        navigationItems = findViewById(R.id.navigation_items);
        roleProgress = findViewById(R.id.role_progress);
        retryButton = findViewById(R.id.retry_role_button);
        signOutButton = findViewById(R.id.sign_out_button);
    }

    private void loadRole() {
        roleProgress.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        roleMessage.setText(R.string.role_checking);
        roleMessage.setVisibility(View.VISIBLE);
        navigationItems.removeAllViews();

        RoleAccess.loadRole(this, currentUser, (role, error) -> {
            roleProgress.setVisibility(View.GONE);
            if (error != null) {
                showRoleLoadError();
                return;
            }
            showNavigationForRole(role);
        });
    }

    private void showRoleLoadError() {
        roleLabel.setText(R.string.role_unverified);
        homeTitle.setText(R.string.home_title_generic);
        homeSubtitle.setText(R.string.home_subtitle_generic);
        roleMessage.setText(R.string.role_load_error);
        roleMessage.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        addNavigationItem(
                R.string.nav_profile,
                R.string.nav_profile_subtitle,
                RoleDestinationActivity.DESTINATION_PROFILE);
    }

    private void showNavigationForRole(RoleAccess.Role role) {
        switch (role) {
            case CUSTOMER:

                roleLabel.setText(R.string.role_customer);
                homeTitle.setText(R.string.home_title_customer);
                homeSubtitle.setText(R.string.home_subtitle_customer);
                roleMessage.setText(R.string.role_customer_message);
                roleMessage.setVisibility(View.VISIBLE);
                addNavigationItem(R.string.nav_products, R.string.nav_products_subtitle,
                        RoleDestinationActivity.DESTINATION_PRODUCTS);
                addNavigationItem(R.string.nav_rental, R.string.nav_rental_subtitle,
                        RoleDestinationActivity.DESTINATION_RENTAL);
                addNavigationItem(R.string.nav_orders, R.string.nav_orders_subtitle,
                        RoleDestinationActivity.DESTINATION_ORDERS);
                addNavigationItem(R.string.nav_product_orders, R.string.nav_product_orders_subtitle,
                        RoleDestinationActivity.DESTINATION_PRODUCT_ORDERS);
                break;
            case OWNER:
                roleLabel.setText(R.string.role_owner);
                homeTitle.setText(R.string.home_title_owner);
                homeSubtitle.setText(R.string.home_subtitle_owner);
                roleMessage.setText(R.string.role_owner_message);
                roleMessage.setVisibility(View.VISIBLE);
                addNavigationItem(R.string.nav_listings, R.string.nav_listings_subtitle,
                        RoleDestinationActivity.DESTINATION_LISTINGS);
                addNavigationItem(R.string.nav_bookings, R.string.nav_bookings_subtitle,                        RoleDestinationActivity.DESTINATION_BOOKINGS);
                addNavigationItem(R.string.nav_manage_products,
                        R.string.nav_manage_products_subtitle,
                        RoleDestinationActivity.DESTINATION_MANAGE_PRODUCTS);
                break;
            case ADMIN:
                roleLabel.setText(R.string.role_admin);
                homeTitle.setText(R.string.home_title_admin);
                homeSubtitle.setText(R.string.home_subtitle_admin);
                roleMessage.setText(R.string.role_admin_message);
                roleMessage.setVisibility(View.VISIBLE);
                addNavigationItem(R.string.nav_admin, R.string.nav_admin_subtitle,
                        RoleDestinationActivity.DESTINATION_ADMIN);
                addNavigationItem(R.string.nav_revenue, R.string.nav_revenue_subtitle,
                        "revenue");
                break;
            case UNASSIGNED:
            default:
                roleLabel.setText(R.string.role_unassigned);
                homeTitle.setText(R.string.home_title_generic);
                homeSubtitle.setText(R.string.home_subtitle_generic);
                roleMessage.setText(R.string.role_unassigned_message);
                roleMessage.setVisibility(View.VISIBLE);
                break;
        }

        // Profile is available to any signed-in user; its destination checks authentication.
        addNavigationItem(R.string.nav_profile, R.string.nav_profile_subtitle,
                RoleDestinationActivity.DESTINATION_PROFILE);
    }

    private void addNavigationItem(int titleResource, int subtitleResource,
                                   String destination) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_home_navigation, navigationItems, false);
        TextView title = row.findViewById(R.id.navigation_title);
        TextView subtitle = row.findViewById(R.id.navigation_subtitle);
        title.setText(titleResource);
        subtitle.setText(subtitleResource);
        row.setOnClickListener(view -> openDestination(destination));
        navigationItems.addView(row);
    }

    private void openDestination(String destination) {
        if (RoleDestinationActivity.DESTINATION_ADMIN.equals(destination)) {
            startActivity(new Intent(this, AdminApprovalActivity.class));
            return;
        }
        if ("revenue".equals(destination)) {
            startActivity(new Intent(this, RevenueActivity.class));
            return;
        }
        if (RoleDestinationActivity.DESTINATION_PRODUCTS.equals(destination)) {
            startActivity(new Intent(this, ProductCatalogActivity.class));
            return;
        }

        Intent intent = new Intent(this, RoleDestinationActivity.class);
        intent.putExtra(RoleDestinationActivity.EXTRA_DESTINATION, destination);
        startActivity(intent);
    }

    private void signOut() {
        firebaseAuth.signOut();
        returnToLogin();
    }

    private void returnToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
