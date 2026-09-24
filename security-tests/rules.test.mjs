import { before, after, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, getDoc, getDocs, setDoc, updateDoc } from 'firebase/firestore';
import { ref, uploadBytes, deleteObject, getMetadata } from 'firebase/storage';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-mzbotanicals',
    firestore: { host: '127.0.0.1', port: 8085, rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
    storage: { host: '127.0.0.1', port: 9199, rules: await readFile(new URL('../storage.rules', import.meta.url), 'utf8') },
  });
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'customer/alice'), { firstName: 'Alice', isAdmin: false });
    await setDoc(doc(context.firestore(), 'customer/bob'), { firstName: 'Bob' });
    await setDoc(doc(context.firestore(), 'product/p1'), { price: 12.34 });
    await setDoc(doc(context.firestore(), 'order/legacy'), { customerId: 'alice' });
  });
});
after(async () => { await env?.cleanup(); });
const customer = uid => env.authenticatedContext(uid, { email_verified: true }).firestore();
const admin = () => env.authenticatedContext('operator', { email_verified: true, admin: true });

test('verified users can read the catalog and their own profile', async () => {
  await assertSucceeds(getDoc(doc(customer('alice'), 'customer/alice')));
  await assertSucceeds(getDocs(collection(customer('alice'), 'product')));
});
test('anonymous and unverified users cannot read customer data or catalog', async () => {
  for (const context of [env.unauthenticatedContext(), env.authenticatedContext('alice', { email_verified: false })]) {
    await assertFails(getDoc(doc(context.firestore(), 'customer/alice')));
    await assertFails(getDoc(doc(context.firestore(), 'product/p1')));
  }
});
test('users cannot read other profiles or enumerate customers', async () => {
  await assertFails(getDoc(doc(customer('alice'), 'customer/bob')));
  await assertFails(getDocs(collection(customer('alice'), 'customer')));
});
test('customer cannot promote themselves or bypass API profile and cart validation', async () => {
  for (const data of [{ isAdmin: true }, { cart: [{ productId: 'p1', quantity: -10 }] }, { firstName: 'Changed' }]) {
    await assertFails(updateDoc(doc(customer('alice'), 'customer/alice'), data));
  }
  await assertFails(setDoc(doc(customer('alice'), 'customer/new'), { id: 'alice', isAdmin: true }));
});
test('even claimed admins cannot bypass product API validation', async () => {
  await assertFails(setDoc(doc(admin().firestore(), 'product/p1'), { price: -100 }));
});
test('order, draft, audit and unknown collection access is denied', async () => {
  for (const path of ['order/legacy', 'orderDraft/forged', 'auditLog/forged', 'unknown/secret']) {
    await assertFails(getDoc(doc(customer('alice'), path)));
    await assertFails(setDoc(doc(customer('alice'), path), { customerId: 'alice', status: 'PAID', subtotalMinor: 1 }));
  }
});
test('only verified admins can upload approved product images', async () => {
  const data = new Uint8Array([137, 80, 78, 71]);
  await assertFails(uploadBytes(ref(env.authenticatedContext('alice', { email_verified: true }).storage(), 'images/customer'), data, { contentType: 'image/png' }));
  await assertFails(uploadBytes(ref(env.authenticatedContext('operator', { admin: true, email_verified: false }).storage(), 'images/unverified'), data, { contentType: 'image/png' }));
  await assertSucceeds(uploadBytes(ref(admin().storage(), 'images/approved'), data, { contentType: 'image/png' }));
  await assertSucceeds(getMetadata(ref(admin().storage(), 'images/approved')));
  await assertSucceeds(deleteObject(ref(admin().storage(), 'images/approved')));
});
test('storage rejects active content, oversized uploads and arbitrary paths', async () => {
  const storage = admin().storage();
  await assertFails(uploadBytes(ref(storage, 'images/script'), new Uint8Array([1]), { contentType: 'image/svg+xml' }));
  await assertFails(uploadBytes(ref(storage, 'images/huge'), new Uint8Array(5 * 1024 * 1024 + 1), { contentType: 'image/png' }));
  await assertFails(uploadBytes(ref(storage, 'private/secret'), new Uint8Array([1]), { contentType: 'image/png' }));
});
