# FarmHub role setup and security notes

## Current app behavior

- Firebase Authentication creates and signs in users. Registration does not assign a role or write a Firestore user profile.
- `HomeActivity` reads the `role` custom claim from the signed-in Firebase ID token and forces a token refresh before showing navigation.
- Supported explicit claim values are `customer`, `owner`, and `admin` (case-insensitive).
- A genuinely missing `role` claim maps to the basic Customer role. This allows signed-in users to see Customer navigation only; it does not grant Owner or Admin access.
- An explicit `owner` or `admin` claim is required for those respective navigation sections. A present but malformed or unknown claim remains unassigned.
- If token refresh fails, the app reports an error and does not grant Customer, Owner, or Admin navigation based on that failed request.
- Customer product browsing is a read-only catalogue of published Firestore documents. Rental, order, Owner listing/booking, and Admin destinations remain placeholders.

## Trusted role assignment

Role claims must be assigned by a trusted server using the Firebase Admin SDK after appropriate approval or verification. Never accept a role supplied by public registration, a client-editable Firestore field, or an untrusted request. In particular, public registration must never grant `owner` or `admin`.

No role-assignment backend is included in this Android project. Until one exists, new authenticated users can use basic Customer navigation because the app treats an absent claim as Customer. Owner and Admin sections remain unavailable unless a trusted administrator/backend has assigned the corresponding claim.

## Firestore data protection

`firestore.rules` contains a deny-by-default starting point for a narrowly scoped user profile and example private notes under `/users/{uid}`. Each record is readable only by the matching authenticated UID. The profile example permits only a display name; the private-note example permits only its body and server-time creation timestamp. These whitelists prevent clients from writing role fields or changing another user's records.

The rules intentionally do not grant access to marketplace collections whose schemas and workflows are not implemented. Add and test collection-specific rules before adding marketplace reads or writes. Android screen visibility and role checks are navigation controls, not data security; Firestore Security Rules must independently enforce each data operation. Do not use public read/write rules.

The examples are not deployed. Review and test them in the Firebase Emulator Suite or Rules Playground before deploying through an explicitly approved process. No Cloud Functions, billing changes, service-account credentials, or role-assignment backend are included.

## Customer product catalogue

`ProductCatalogActivity` and `ProductDetailsActivity` require Firebase Authentication and a successfully refreshed Customer role from `RoleAccess`. The catalogue uses Firestore reads only. `firestore.rules` locally proposes reads for published products; this local file may differ from rules currently deployed in Firebase. Product reads will fail until the deployed rules allow them.

The proposed document path is `products/{productId}`. Required fields are `name` (string), `nameSearch` (lowercase normalized name used for prefix search), `categoryId` (string), `priceMinor` (integer in the currency's smallest unit), `currency` (ISO 4217 code), `unitLabel` (string), `stockQuantity` (non-negative integer), `publicationStatus` (`published`, `draft`, or `archived`), and `sellerUid` (Firebase Auth UID). Optional display fields are `description`, `imageUrl` (HTTPS), `sellerDisplayName`, and `sellerArea`. Optional timestamps are `createdAt` and `updatedAt`.

Firestore client reads return the whole product document; Rules cannot hide selected fields from a readable document. Therefore product documents must contain only public catalogue data. Do not put seller email, phone, exact private address, payment information, or other private values in this collection. `sellerUid` is part of the approved schema and is technically readable as an opaque account identifier, although the app does not show it. Keep any private seller profile in a separate path that has no client read grant.

These fields are a proposed client read model, not evidence that product documents already exist. There is no seller-write workflow. Product writes remain denied to Android clients. Only public seller display name and area are shown; the seller UID and private contact information are not displayed. The catalogue does not add demo products.
