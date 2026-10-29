# FarmHub: owner approval and commission features

This update adds registration role selection, pending owner applications, an admin review screen, approval records, a 5% calculated commission on new orders/bookings, and a revenue summary screen.

## Important: build could not be verified in this environment

The project uses Gradle 8.11.1, but this environment could not download the Gradle distribution because network access to services.gradle.org was unavailable. XML files were parsed successfully, but run a full build locally before distributing an APK.

## 1. Configure your admin account (required once)

Do **not** let users type "admin" during registration. Admin status is granted only by an `admins/{Firebase UID}` record created by a trusted project administrator.

1. Create the admin account using Firebase Authentication (or use your existing admin account).
2. In Firebase Console → Authentication → Users, copy that account's UID.
3. Open Firestore Database → Data and create a collection named `admins`.
4. Create a document whose document ID is exactly that UID.
5. Add field `active` with type Boolean and value `true`.
6. Deploy the updated `firestore.rules` file from this project (Firestore Database → Rules → paste the file contents → Publish).
7. Sign out and sign in to the app again. The Home screen should show Owner Applications and Revenue Summary for that admin.

The app cannot create or promote its own admin record. Keep this UID private and only bootstrap trusted administrators.

## 2. Owner approval flow

- Registration offers Customer or Equipment Owner.
- Customer registration creates a `userProfiles/{uid}` document with role `customer` and opens the app.
- Owner registration creates a `userProfiles/{uid}` document and an `ownerApplications/{uid}` record with status `pending`, then signs out and shows a waiting message.
- Admin reviews pending applications from the Owner Applications screen.
- Approval updates the application status and creates `ownerApprovals/{uid}` with status `approved`.
- Existing product/equipment Firestore rules continue to require an approved owner record for owner actions.

## 3. Commission and revenue behavior

- Commission rate is 5% (`commissionRateBps = 500`).
- Product order commission is calculated from the recorded cart order total (unit listing price × quantity, summed across cart items).
- Rental commission is calculated from the recorded rental price. Current booking flow records one rental day per booking, so the commission uses the agreed daily rental price.
- Amounts in `priceMinor`, `totalMinor`, `commissionMinor`, and related fields are in minor currency units (paise). UI displays rupees.
- Revenue Summary sums stored commission values from orders and bookings. Old records without commission fields are ignored.
- This is calculated/projected commission, not money actually collected. Product orders use Cash on Delivery and no commission transfer/payment processing is added.

## 4. Rebuild and test locally

From PowerShell in the project root:

```powershell
.\gradlew.bat assembleDebug
```

If successful, APK output is:

`app\build\outputs\apk\debug\app-debug.apk`

Test with at least these cases before sharing:
1. New Customer account → Home screen and product browsing.
2. New Owner account → pending message; owner listing/booking controls must not be accessible.
3. Admin account → Owner Applications and Revenue Summary.
4. Admin approves a pending owner → owner signs in again and gets owner dashboard.
5. Admin rejects a pending owner → owner access remains unavailable.
6. Customer places a COD order → order includes 5% commission calculated from order total.
7. Customer requests a one-day rental → booking includes 5% commission calculated from agreed rental price.
8. Revenue Summary totals recorded order and rental commissions.
9. Re-test existing login, cart, COD order, booking, approval/rejection, and return inspection flows.

## 5. Security notes

- Deploy the rules together with this APK. The new app writes commission fields; rules accept those fields only when the rate and amount match 5%, while still permitting older clients to write records without commission fields.
- Firestore rules are not a substitute for a trusted server for actual financial accounting. Since the current project has no payment collection/commission transfer, the screen labels this as calculated commission.
- The revenue summary is protected by the `admins/{uid}` active record. Do not create admin records from the client app.
