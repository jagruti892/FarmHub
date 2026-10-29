import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  Timestamp,
  writeBatch,
} from 'firebase/firestore';

const PROJECT_ID = 'farmhub-rules-tests';
// 2030-01-01 at midnight in India (Asia/Kolkata), represented in UTC.
const DAY = Timestamp.fromDate(new Date(Date.UTC(2029, 11, 31, 18, 30)));
let testEnv;

before(async () => {
  const rules = await readFile(new URL('../firestore.rules', import.meta.url), 'utf8');
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules, host: '127.0.0.1', port: 8080 },
  });
});

after(async () => {
  await testEnv?.cleanup();
});

async function clearAndSeed({ owners = [], equipment = [], bookings = [], products = [] } = {}) {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    for (const uid of owners) {
      await setDoc(doc(db, 'ownerApprovals', uid), { status: 'approved' });
    }
    for (const item of equipment) {
      await setDoc(doc(db, 'equipment', item.id), item.data);
    }
    for (const item of bookings) {
      await setDoc(doc(db, 'bookings', item.id), item.data);
    }
    for (const item of products) {
      await setDoc(doc(db, 'products', item.id), item.data);
    }
  });
}

function equipmentData(ownerUid, overrides = {}) {
  return {
    ownerUid,
    name: 'Manual Knapsack Sprayer',
    description: 'A maintained agricultural sprayer listing.',
    categoryId: 'sprayer',
    imageUrl: 'https://example.org/sprayer.jpg',
    dailyPriceMinor: 50000,
    currency: 'INR',
    location: 'Goa',
    publicationStatus: 'published',
    availabilityStatus: 'available',
    damageChargeMinor: 25000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  };
}

function bookingData({ equipmentId = 'sprayer-1', customerUid = 'customer-1', ownerUid = 'owner-1', overrides = {} } = {}) {
  const data = {
    equipmentId,
    equipmentName: 'Manual Knapsack Sprayer',
    customerUid,
    ownerUid,
    rentalDay: DAY,
    returnDeadline: deadlineFor(DAY),
    returnLocation: 'Goa',
    agreedPriceMinor: 50000,
    agreedDamageChargeMinor: 25000,
    currency: 'INR',
    status: 'pending',
    agreementAccepted: true,
    agreementAcceptedAt: serverTimestamp(),
    termsVersion: 'farmhub-rental-v1',
    returnStatus: 'unreturned',
    inspectionCondition: 'not_inspected',
    damageDescription: '',
    inspectionChargeMinor: 0,
    createdAt: serverTimestamp(),
    ...overrides,
  };
  if (overrides.rentalDay && !overrides.returnDeadline) {
    data.returnDeadline = deadlineFor(overrides.rentalDay);
  }
  return data;
}

function deadlineFor(day) {
  return Timestamp.fromDate(new Date(day.toDate().getTime() + 41 * 60 * 60 * 1000));
}

async function createListing(uid, id = 'sprayer-1') {
  const db = testEnv.authenticatedContext(uid).firestore();
  await assertSucceeds(setDoc(doc(db, 'equipment', id), equipmentData(uid)));
  return db;
}

async function createPendingBooking(uid = 'customer-1', id = 'booking-1') {
  const db = testEnv.authenticatedContext(uid).firestore();
  await assertSucceeds(setDoc(doc(db, 'bookings', id), bookingData({ customerUid: uid })));
  return db;
}

async function approveWithReservation(ownerUid, bookingId) {
  const db = testEnv.authenticatedContext(ownerUid).firestore();
  const bookingRef = doc(db, 'bookings', bookingId);
  const lockRef = doc(db, 'equipment', 'sprayer-1', 'rentalDays', '2030-01-01');
  return runTransaction(db, async (transaction) => {
    const booking = await transaction.get(bookingRef);
    const lock = await transaction.get(lockRef);
    if (!booking.exists() || booking.data().status !== 'pending') {
      throw new Error('Booking is not pending.');
    }
    if (lock.exists()) {
      throw new Error('This equipment day is already reserved.');
    }
    transaction.update(bookingRef, {
      status: 'approved',
      reviewedAt: serverTimestamp(),
    });
    transaction.set(lockRef, {
      bookingId,
      rentalDay: DAY,
      createdAt: serverTimestamp(),
    });
  });
}

// Submit both writes without reading the booking, so authorization is checked
// against the complete approval + reservation-lock operation.
async function submitApprovalBatch(ownerUid, bookingId, lockBookingId = bookingId) {
  const db = testEnv.authenticatedContext(ownerUid).firestore();
  const batch = writeBatch(db);
  batch.update(doc(db, 'bookings', bookingId), {
    status: 'approved',
    reviewedAt: serverTimestamp(),
  });
  batch.set(doc(db, 'equipment', 'sprayer-1', 'rentalDays', '2030-01-01'), {
    bookingId: lockBookingId,
    rentalDay: DAY,
    createdAt: serverTimestamp(),
  });
  return batch.commit();
}

test('unapproved users cannot create or manage equipment', async () => {
  await clearAndSeed({
    equipment: [{ id: 'unapproved-listing', data: equipmentData('unapproved-owner') }],
  });
  const db = testEnv.authenticatedContext('unapproved-owner').firestore();

  await assertFails(setDoc(doc(db, 'ownerApprovals', 'unapproved-owner'), { status: 'approved' }));
  await assertFails(setDoc(doc(db, 'equipment', 'new-listing'), equipmentData('unapproved-owner')));
  await assertFails(updateDoc(doc(db, 'equipment', 'unapproved-listing'), {
    availabilityStatus: 'unavailable',
    updatedAt: serverTimestamp(),
  }));
});

test('approved owners can manage only their own equipment', async () => {
  await clearAndSeed({
    owners: ['owner-1', 'owner-2'],
    equipment: [{ id: 'other-listing', data: equipmentData('owner-2') }],
  });
  const db = await createListing('owner-1');

  await assertSucceeds(updateDoc(doc(db, 'equipment', 'sprayer-1'), {
    availabilityStatus: 'unavailable',
    updatedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(doc(db, 'equipment', 'sprayer-1'), {
    damageChargeMinor: 0,
    updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(db, 'equipment', 'sprayer-1'), {
    damageChargeMinor: -1,
    updatedAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(db, 'equipment', 'forged-owner'), equipmentData('owner-2')));
  await assertFails(updateDoc(doc(db, 'equipment', 'other-listing'), {
    availabilityStatus: 'unavailable',
    updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(db, 'equipment', 'sprayer-1'), {
    ownerUid: 'owner-2',
    updatedAt: serverTimestamp(),
  }));
});

test('customers cannot change booking status or agreed price', async () => {
  await clearAndSeed({ owners: ['owner-1'], equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }] });
  await createPendingBooking();
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();

  await assertFails(updateDoc(doc(customerDb, 'bookings', 'booking-1'), { status: 'approved' }));
  await assertFails(updateDoc(doc(customerDb, 'bookings', 'booking-1'), { agreedPriceMinor: 1 }));
  await assertFails(updateDoc(doc(customerDb, 'bookings', 'booking-1'), { ownerUid: 'owner-2' }));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'forged-approved'), bookingData({
    overrides: { status: 'approved' },
  })));
});

test('customers cannot forge customerUid or ownerUid or submit a different price', async () => {
  await clearAndSeed({
    owners: ['owner-1', 'owner-2'],
    equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
  });
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();

  await assertFails(setDoc(doc(customerDb, 'bookings', 'forged-customer'), bookingData({
    overrides: { customerUid: 'customer-2' },
  })));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'forged-owner'), bookingData({
    overrides: { ownerUid: 'owner-2' },
  })));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'wrong-price'), bookingData({
    overrides: { agreedPriceMinor: 1 },
  })));
});

test('booking creation requires acceptance and exact immutable agreement snapshots', async () => {
  await clearAndSeed({
    owners: ['owner-1'],
    equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
  });
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();

  await assertFails(setDoc(doc(customerDb, 'bookings', 'not-accepted'), bookingData({
    overrides: { agreementAccepted: false },
  })));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'forged-terms'), bookingData({
    overrides: { agreedDamageChargeMinor: 1 },
  })));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'forged-location'), bookingData({
    overrides: { returnLocation: 'Different location' },
  })));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'forged-deadline'), bookingData({
    overrides: { returnDeadline: Timestamp.fromDate(
      new Date(deadlineFor(DAY).toDate().getTime() + 60 * 1000)) },
  })));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'forged-version'), bookingData({
    overrides: { termsVersion: 'changed-by-client' },
  })));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'forged-acceptance-time'), bookingData({
    overrides: { agreementAcceptedAt: DAY },
  })));

  await createPendingBooking();
  await assertFails(updateDoc(doc(customerDb, 'bookings', 'booking-1'), {
    agreedDamageChargeMinor: 0,
  }));
  await assertFails(updateDoc(doc(customerDb, 'bookings', 'booking-1'), {
    returnLocation: 'Changed later',
  }));
  await assertFails(updateDoc(doc(customerDb, 'bookings', 'booking-1'), {
    agreementAccepted: false,
  }));
});

test('legacy equipment without a configured damage charge uses the accepted zero default', async () => {
  const legacyEquipment = equipmentData('owner-1');
  delete legacyEquipment.damageChargeMinor;
  await clearAndSeed({
    owners: ['owner-1'],
    equipment: [{ id: 'sprayer-1', data: legacyEquipment }],
  });
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();
  await assertSucceeds(setDoc(doc(customerDb, 'bookings', 'zero-default'), bookingData({
    overrides: { agreedDamageChargeMinor: 0 },
  })));
});

test('only the approved equipment owner can record a valid capped return inspection', async () => {
  await clearAndSeed({
    owners: ['owner-1', 'owner-2'],
    equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
  });
  await createPendingBooking();
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();
  const unauthorizedInspection = {
    returnStatus: 'returned',
    inspectionCondition: 'damaged',
    damageDescription: 'Scratched handle',
    inspectionChargeMinor: 10000,
    inspectedAt: serverTimestamp(),
  };
  await assertFails(updateDoc(doc(customerDb, 'bookings', 'booking-1'), unauthorizedInspection));

  const ownerDb = testEnv.authenticatedContext('owner-1').firestore();
  await assertFails(updateDoc(doc(ownerDb, 'bookings', 'booking-1'), unauthorizedInspection));
  await assertSucceeds(approveWithReservation('owner-1', 'booking-1'));

  const otherOwnerDb = testEnv.authenticatedContext('owner-2').firestore();
  await assertFails(updateDoc(doc(otherOwnerDb, 'bookings', 'booking-1'), unauthorizedInspection));
  await assertFails(updateDoc(doc(ownerDb, 'bookings', 'booking-1'), {
    ...unauthorizedInspection,
    inspectionChargeMinor: 25001,
  }));

  await assertSucceeds(updateDoc(doc(ownerDb, 'bookings', 'booking-1'), unauthorizedInspection));
  const customerBooking = await assertSucceeds(getDoc(doc(customerDb, 'bookings', 'booking-1')));
  assert.equal(customerBooking.data().inspectionCondition, 'damaged');
  assert.equal(customerBooking.data().inspectionChargeMinor, 10000);
  assert.equal(customerBooking.data().agreedDamageChargeMinor, 25000);
  await assertFails(updateDoc(doc(customerDb, 'bookings', 'booking-1'), {
    inspectionChargeMinor: 0,
  }));
});

test('approved owner can record an unreturned inspection before a later return', async () => {
  await clearAndSeed({
    owners: ['owner-1'],
    equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
  });
  await createPendingBooking();
  await approveWithReservation('owner-1', 'booking-1');
  const ownerDb = testEnv.authenticatedContext('owner-1').firestore();
  const bookingRef = doc(ownerDb, 'bookings', 'booking-1');

  await assertSucceeds(updateDoc(bookingRef, {
    returnStatus: 'unreturned',
    inspectionCondition: 'not_inspected',
    damageDescription: '',
    inspectionChargeMinor: 0,
    inspectedAt: serverTimestamp(),
  }));
  await assertSucceeds(updateDoc(bookingRef, {
    returnStatus: 'returned',
    inspectionCondition: 'no_damage',
    damageDescription: '',
    inspectionChargeMinor: 0,
    inspectedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(bookingRef, {
    returnStatus: 'unreturned',
    inspectionCondition: 'not_inspected',
    damageDescription: '',
    inspectionChargeMinor: 0,
    inspectedAt: serverTimestamp(),
  }));
});

test('a customer cannot request equipment marked unavailable', async () => {
  await clearAndSeed({
    owners: ['owner-1'],
    equipment: [{
      id: 'sprayer-1',
      data: equipmentData('owner-1', { availabilityStatus: 'unavailable' }),
    }],
  });
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();
  await assertFails(setDoc(doc(customerDb, 'bookings', 'unavailable-request'), bookingData()));
});

test('signed-in customers can query published equipment only', async () => {
  await clearAndSeed({
    equipment: [
      { id: 'published-item', data: equipmentData('owner-1') },
      { id: 'draft-item', data: equipmentData('owner-1', { publicationStatus: 'draft' }) },
    ],
  });
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();
  await assertSucceeds(getDoc(doc(customerDb, 'equipment', 'published-item')));
  await assertFails(getDoc(doc(customerDb, 'equipment', 'draft-item')));
  const publishedQuery = query(
    collection(customerDb, 'equipment'),
    where('publicationStatus', '==', 'published'),
  );
  const results = await assertSucceeds(getDocs(publishedQuery));
  assert.deepEqual(results.docs.map((item) => item.id), ['published-item']);
});

test('booking dates require future midnight in Asia/Kolkata', async () => {
  await clearAndSeed({
    owners: ['owner-1'],
    equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
  });
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();

  // DAY is exactly 2030-01-01 00:00 in India, represented as 2029-12-31 18:30 UTC.
  await assertSucceeds(setDoc(doc(customerDb, 'bookings', 'valid-india-midnight'), bookingData()));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'invalid-time'), bookingData({
    overrides: {
      rentalDay: Timestamp.fromDate(new Date(Date.UTC(2029, 11, 31, 18, 31))),
    },
  })));
  await assertFails(setDoc(doc(customerDb, 'bookings', 'past-day'), bookingData({
    overrides: {
      rentalDay: Timestamp.fromDate(new Date(Date.UTC(2019, 11, 31, 18, 30))),
    },
  })));
});

test('owner rejection remains possible after equipment is unavailable or unpublished', async () => {
  for (const changedFields of [
    { availabilityStatus: 'unavailable' },
    { publicationStatus: 'archived' },
  ]) {
    await clearAndSeed({
      owners: ['owner-1'],
      equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
    });
    await createPendingBooking();
    const ownerDb = testEnv.authenticatedContext('owner-1').firestore();
    await assertSucceeds(updateDoc(doc(ownerDb, 'equipment', 'sprayer-1'), {
      ...changedFields,
      updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(ownerDb, 'bookings', 'booking-1'), {
      status: 'rejected',
      reviewedAt: serverTimestamp(),
    }));
  }
});

test('owner cannot approve if equipment is no longer available or published', async () => {
  for (const changedFields of [
    { availabilityStatus: 'unavailable' },
    { publicationStatus: 'archived' },
  ]) {
    await clearAndSeed({
      owners: ['owner-1'],
      equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
    });
    await createPendingBooking();
    const ownerDb = testEnv.authenticatedContext('owner-1').firestore();
    await assertSucceeds(updateDoc(doc(ownerDb, 'equipment', 'sprayer-1'), {
      ...changedFields,
      updatedAt: serverTimestamp(),
    }));
    await assertFails(submitApprovalBatch('owner-1', 'booking-1'));
  }
});

test('pending requests do not create or grant access to reservation locks', async () => {
  await clearAndSeed({ owners: ['owner-1'], equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }] });
  await createPendingBooking();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const lockPath = doc(context.firestore(), 'equipment', 'sprayer-1', 'rentalDays', '2030-01-01');
    assert.equal((await getDoc(lockPath)).exists(), false);
  });

  const customerDb = testEnv.authenticatedContext('customer-1').firestore();
  await assertFails(setDoc(doc(customerDb, 'equipment', 'sprayer-1', 'rentalDays', '2030-01-01'), {
    bookingId: 'booking-1', rentalDay: DAY, createdAt: serverTimestamp(),
  }));

  const ownerDb = testEnv.authenticatedContext('owner-1').firestore();
  const prematureLockBatch = writeBatch(ownerDb);
  prematureLockBatch.set(
    doc(ownerDb, 'equipment', 'sprayer-1', 'rentalDays', '2030-01-01'),
    { bookingId: 'booking-1', rentalDay: DAY, createdAt: serverTimestamp() },
  );
  await assertFails(prematureLockBatch.commit());
});

test('the approved equipment owner can approve an available day with its lock', async () => {
  await clearAndSeed({ owners: ['owner-1'], equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }] });
  await createPendingBooking();
  const ownerDb = testEnv.authenticatedContext('owner-1').firestore();
  await assertFails(updateDoc(doc(ownerDb, 'bookings', 'booking-1'), {
    status: 'approved',
    reviewedAt: serverTimestamp(),
  }));

  await assertSucceeds(approveWithReservation('owner-1', 'booking-1'));
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    assert.equal((await getDoc(doc(adminDb, 'bookings', 'booking-1'))).data().status, 'approved');
    const lock = await getDoc(doc(adminDb, 'equipment', 'sprayer-1', 'rentalDays', '2030-01-01'));
    assert.equal(lock.data().bookingId, 'booking-1', 'lock must reference the approved booking');
  });
});

test('two competing approvals for one equipment day cannot both succeed', async () => {
  await clearAndSeed({ owners: ['owner-1'], equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }] });
  await createPendingBooking('customer-1', 'booking-1');
  await createPendingBooking('customer-2', 'booking-2');

  const results = await Promise.allSettled([
    approveWithReservation('owner-1', 'booking-1'),
    approveWithReservation('owner-1', 'booking-2'),
  ]);
  assert.equal(results.filter((result) => result.status === 'fulfilled').length, 1);
  assert.equal(results.filter((result) => result.status === 'rejected').length, 1);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const statuses = await Promise.all(['booking-1', 'booking-2'].map(async (id) =>
      (await getDoc(doc(adminDb, 'bookings', id))).data().status));
    assert.equal(statuses.filter((status) => status === 'approved').length, 1);
    assert.equal(statuses.filter((status) => status === 'pending').length, 1);
    const lock = await getDoc(doc(adminDb, 'equipment', 'sprayer-1', 'rentalDays', '2030-01-01'));
    assert.ok(['booking-1', 'booking-2'].includes(lock.data().bookingId));
    assert.equal((await getDoc(doc(adminDb, 'bookings', lock.data().bookingId))).data().status, 'approved');
  });
});

test('an approval fails if its day lock names a different booking', async () => {
  await clearAndSeed({
    owners: ['owner-1'],
    equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
    bookings: [
      { id: 'booking-1', data: bookingData() },
      { id: 'booking-2', data: bookingData({ overrides: { status: 'approved' } }) },
    ],
  });

  await assertFails(submitApprovalBatch('owner-1', 'booking-1', 'booking-2'));
});

test('customers cannot read another customer’s booking', async () => {
  await clearAndSeed({
    owners: ['owner-1'],
    equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
    bookings: [{ id: 'booking-2', data: bookingData({ customerUid: 'customer-2' }) }],
  });
  await createPendingBooking('customer-1', 'booking-1');
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();
  await assertSucceeds(getDoc(doc(customerDb, 'bookings', 'booking-1')));
  await assertFails(getDoc(doc(customerDb, 'bookings', 'booking-2')));
  await assertFails(getDocs(query(collection(customerDb, 'bookings'), where('customerUid', '==', 'customer-2'))));

  const unrelatedOwnerDb = testEnv.authenticatedContext('owner-2').firestore();
  await assertFails(getDoc(doc(unrelatedOwnerDb, 'bookings', 'booking-2')));
  const equipmentOwnerDb = testEnv.authenticatedContext('owner-1').firestore();
  await assertSucceeds(getDoc(doc(equipmentOwnerDb, 'bookings', 'booking-2')));
});

test('an owner cannot approve a booking for equipment owned by someone else', async () => {
  await clearAndSeed({
    owners: ['owner-1', 'owner-2'],
    equipment: [{ id: 'sprayer-1', data: equipmentData('owner-1') }],
    bookings: [{ id: 'booking-1', data: bookingData() }],
  });
  await assertFails(submitApprovalBatch('owner-2', 'booking-1'));
});

test('published product catalogue reads still work and unpublished products stay hidden', async () => {
  await clearAndSeed({
    products: [
      { id: 'published', data: { publicationStatus: 'published', name: 'Paddy Seeds' } },
      { id: 'draft', data: { publicationStatus: 'draft', name: 'Private draft' } },
    ],
  });
  const customerDb = testEnv.authenticatedContext('customer-1').firestore();
  await assertSucceeds(getDoc(doc(customerDb, 'products', 'published')));
  const catalogueQuery = query(
    collection(customerDb, 'products'),
    where('publicationStatus', '==', 'published'),
    limit(25),
  );
  assert.equal((await assertSucceeds(getDocs(catalogueQuery))).size, 1);
  await assertFails(getDoc(doc(customerDb, 'products', 'draft')));
  await assertFails(getDocs(query(collection(customerDb, 'products'), limit(25))));
  await assertFails(setDoc(doc(customerDb, 'products', 'client-write'), {
    publicationStatus: 'published',
  }));
  await assertFails(updateDoc(doc(customerDb, 'products', 'published'), { name: 'Changed' }));
  await assertFails(deleteDoc(doc(customerDb, 'products', 'published')));
  await assertFails(getDoc(doc(customerDb, 'privateRecords', 'someone-else')));
  await assertFails(setDoc(doc(customerDb, 'unmatchedCollection', 'document'), { value: true }));
});
