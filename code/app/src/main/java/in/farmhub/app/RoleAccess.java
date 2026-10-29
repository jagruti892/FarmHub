package in.farmhub.app;

import android.app.Activity;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Locale;
import java.util.Map;

/** Resolves roles from trusted admin/approval records, saved profiles and legacy server claims. */
public final class RoleAccess {
    public static final String CLAIM_NAME = "role";
    private RoleAccess() { }
    public enum Role { CUSTOMER, OWNER, ADMIN, UNASSIGNED }
    public interface RoleCallback { void onRoleLoaded(Role role, Exception error); }

    public static void loadRole(Activity activity, FirebaseUser user, RoleCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();
        db.collection("admins").document(uid).get().addOnCompleteListener(activity, adminTask -> {
            if (!adminTask.isSuccessful()) { callback.onRoleLoaded(Role.UNASSIGNED, adminTask.getException()); return; }
            if (adminTask.getResult().exists() && Boolean.TRUE.equals(adminTask.getResult().getBoolean("active"))) {
                callback.onRoleLoaded(Role.ADMIN, null); return;
            }
            db.collection("ownerApprovals").document(uid).get().addOnCompleteListener(activity, approvalTask -> {
                if (!approvalTask.isSuccessful()) { callback.onRoleLoaded(Role.UNASSIGNED, approvalTask.getException()); return; }
                if (approvalTask.getResult().exists() && "approved".equals(approvalTask.getResult().getString("status"))) {
                    callback.onRoleLoaded(Role.OWNER, null); return;
                }
                db.collection("ownerApplications").document(uid).get().addOnCompleteListener(activity, applicationTask -> {
                    if (!applicationTask.isSuccessful()) { callback.onRoleLoaded(Role.UNASSIGNED, applicationTask.getException()); return; }
                    if (applicationTask.getResult().exists()) { callback.onRoleLoaded(Role.UNASSIGNED, null); return; }
                    db.collection("userProfiles").document(uid).get().addOnCompleteListener(activity, profileTask -> {
                        if (!profileTask.isSuccessful()) { callback.onRoleLoaded(Role.UNASSIGNED, profileTask.getException()); return; }
                        String profileRole = profileTask.getResult().exists() ? profileTask.getResult().getString("role") : null;
                        if ("customer".equals(profileRole)) { callback.onRoleLoaded(Role.CUSTOMER, null); return; }
                        // Keep supporting pre-existing accounts that were assigned server-side claims.
                        user.getIdToken(true).addOnCompleteListener(activity, tokenTask -> {
                            if (!tokenTask.isSuccessful()) { callback.onRoleLoaded(Role.UNASSIGNED, tokenTask.getException()); return; }
                            Map<String, Object> claims = tokenTask.getResult().getClaims();
                            Object raw = claims.get(CLAIM_NAME);
                            String role = raw instanceof String ? ((String) raw).trim().toLowerCase(Locale.US) : "";
                            if ("admin".equals(role)) callback.onRoleLoaded(Role.ADMIN, null);
                            else if ("customer".equals(role)) callback.onRoleLoaded(Role.CUSTOMER, null);
                            else if ("owner".equals(role)) callback.onRoleLoaded(Role.UNASSIGNED, null);
                            else if (profileRole == null) callback.onRoleLoaded(Role.CUSTOMER, null);
                            else callback.onRoleLoaded(Role.UNASSIGNED, null);
                        });
                    });
                });
            });
        });
    }
}
