import { readFile } from 'node:fs/promises';
import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { ref, uploadBytes, getBytes } from 'firebase/storage';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, serverTimestamp, Timestamp, writeBatch, runTransaction } from 'firebase/firestore';
let env;
const profile = (uid, role = 'PENDIENTE') => ({ uid, email: `${uid}@example.com`, nombre: 'Usuario', rol: role, activo: true, createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
const client = (uid, verified = true) => env.authenticatedContext(uid, { email: `${uid}@example.com`, email_verified: verified }).firestore();
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-siderurgica', firestore: { host: '127.0.0.1', port: 8080, rules: await readFile('../firestore.rules', 'utf8') }, storage: { host: '127.0.0.1', port: 9199, rules: await readFile('../storage.rules', 'utf8') } });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    for (const [uid, role] of [['admin', 'ADMIN'], ['sales', 'VENTAS'], ['warehouse', 'ALMACEN'], ['pending', 'PENDIENTE']]) await setDoc(doc(db, 'usuarios', uid), profile(uid, role));
    await setDoc(doc(db, 'productos', 'p1'), { nombre: 'Acero', categoria: 'General', precioCentimos: 101, stock: 10, stockMinimo: 5, createdAt: Timestamp.now() });
  });
});
test('new verified user can only create own pending profile', async () => {
  const db = client('new');
  await assertFails(setDoc(doc(db, 'usuarios', 'new'), { ...profile('new', 'ADMIN'), createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(db, 'usuarios', 'new'), { ...profile('new'), createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'usuarios', 'new'), { rol: 'ADMIN', updatedAt: serverTimestamp() }));
});
test('profile permissions enforce identity and immutable fields', async () => {
  const db = client('sales');
  await assertSucceeds(updateDoc(doc(db, 'usuarios', 'sales'), { nombre: 'Ana', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'usuarios', 'sales'), { activo: false, updatedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(db, 'usuarios', 'warehouse')));
  await assertFails(updateDoc(doc(db, 'usuarios', 'sales'), { email: 'other@example.com', updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(client('admin'), 'usuarios', 'pending'), { rol: 'VENTAS', updatedAt: serverTimestamp() }));
});
test('unverified, pending, inactive and anonymous accounts cannot read business data', async () => {
  await assertFails(getDoc(doc(client('sales', false), 'productos', 'p1')));
  await assertFails(getDoc(doc(client('pending'), 'productos', 'p1')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'productos', 'p1')));
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(), 'usuarios', 'sales'), { activo: false }));
  await assertFails(getDoc(doc(client('sales'), 'productos', 'p1')));
});
test('warehouse creates products but sales cannot create or change stock', async () => {
  const product = { nombre: 'Acero', categoria: 'General', precioCentimos: 100, stock: 10, stockMinimo: 2, createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(client('warehouse'), 'productos', 'p2'), product));
  await assertFails(setDoc(doc(client('sales'), 'productos', 'p3'), product));
  await assertFails(updateDoc(doc(client('warehouse'), 'productos', 'p1'), { stock: 999 }));
});
test('quote creation enforces creator, price, discount, total and immutable state', async () => {
  const db = client('sales');
  const quote = { creatorUid: 'sales', cliente: 'Ana', ruc: '', productoId: 'p1', productoNombre: 'Acero', cantidad: 10, precioCentimos: 101, subtotalCentimos: 1010, descuentoPorcentaje: 5, totalCentimos: 960, estado: 'PENDIENTE', createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(db, 'cotizaciones', 'q1'), quote));
  for (const patch of [{ creatorUid: 'admin' }, { precioCentimos: 1 }, { totalCentimos: 1 }, { descuentoPorcentaje: 99 }, { totalCentimos: 959 }, { estado: 'APROBADA' }]) await assertFails(setDoc(doc(db, 'cotizaciones', crypto.randomUUID()), { ...quote, ...patch }));
  await assertFails(setDoc(doc(client('warehouse'), 'cotizaciones', 'q2'), { ...quote, creatorUid: 'warehouse' }));
  await assertFails(updateDoc(doc(db, 'cotizaciones', 'q1'), { estado: 'APROBADA' }));
});
test('unknown collections are closed', async () => {
  await assertFails(setDoc(doc(client('admin'), 'ventas', 'x'), { total: 100 }));
});


const quoteData = (quantity = 6) => {
  const subtotal = quantity * 101;
  const discount = quantity >= 100 ? 10 : quantity >= 50 ? 7 : quantity >= 10 ? 5 : 0;
  return { creatorUid: 'sales', cliente: 'Ana', ruc: '', productoId: 'p1', productoNombre: 'Acero', cantidad: quantity,
    precioCentimos: 101, subtotalCentimos: subtotal, descuentoPorcentaje: discount, totalCentimos: Math.round(subtotal * (100 - discount) / 100), estado: 'PENDIENTE', createdAt: serverTimestamp() };
};
async function prepareOrder(db, id, quantity = 6) {
  const q = quoteData(quantity);
  await setDoc(doc(db, 'cotizaciones', id), q);
  await updateDoc(doc(db, 'cotizaciones', id), { estado: 'APROBADA', updatedBy: 'sales', updatedAt: serverTimestamp() });
  const o = { cotizacionId: id, creatorUid: 'sales', convertedBy: 'sales', cliente: q.cliente, ruc: q.ruc,
    productoId: q.productoId, productoNombre: q.productoNombre, cantidad: quantity, precioCentimos: q.precioCentimos,
    descuentoPorcentaje: q.descuentoPorcentaje, subtotalCentimos: q.totalCentimos, igvCentimos: Math.round(q.totalCentimos * .18),
    totalCentimos: q.totalCentimos + Math.round(q.totalCentimos * .18), estado: 'PENDIENTE', createdAt: serverTimestamp() };
  const batch = writeBatch(db);
  batch.set(doc(db, 'pedidos', id), o);
  batch.update(doc(db, 'cotizaciones', id), { estado: 'CONVERTIDA', pedidoId: id, updatedBy: 'sales', updatedAt: serverTimestamp() });
  await batch.commit();
  return o;
}
async function approve(db, id) {
  return runTransaction(db, async tx => {
    const ref = doc(db, 'pedidos', id);
    const order = (await tx.get(ref)).data();
    if (['APROBADO', 'FACTURADO'].includes(order.estado)) return;
    const productRef = doc(db, 'productos', order.productoId);
    const stock = (await tx.get(productRef)).data().stock;
    if (stock < order.cantidad) throw new Error('Stock insuficiente');
    tx.update(productRef, { stock: stock - order.cantidad, ultimoPedidoId: id, updatedAt: serverTimestamp() });
    tx.update(ref, { estado: 'APROBADO', updatedBy: 'sales', updatedAt: serverTimestamp() });
  });
}
test('conversion requires approved quote and atomic paired writes; discount is preserved', async () => {
  const db = client('sales');
  const q = quoteData(10);
  await setDoc(doc(db, 'cotizaciones', 'alone'), q);
  await assertFails(updateDoc(doc(db, 'cotizaciones', 'alone'), { estado: 'CONVERTIDA', pedidoId: 'alone', updatedBy: 'sales', updatedAt: serverTimestamp() }));
  await updateDoc(doc(db, 'cotizaciones', 'alone'), { estado: 'APROBADA', updatedBy: 'sales', updatedAt: serverTimestamp() });
  await assertFails(updateDoc(doc(db, 'cotizaciones', 'alone'), { estado: 'CONVERTIDA', pedidoId: 'alone', updatedBy: 'sales', updatedAt: serverTimestamp() }));
  await prepareOrder(db, 'paired', 10);
  const order = (await getDoc(doc(db, 'pedidos', 'paired'))).data();
  assert.equal(order.subtotalCentimos, 960);
  assert.equal(order.igvCentimos, 173);
  assert.equal(order.totalCentimos, 1133);
  await assertFails(setDoc(doc(db, 'pedidos', 'duplicate'), { ...order, cotizacionId: 'paired', createdAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'pedidos', 'paired'), { totalCentimos: 1 }));
});
test('approval and stock change must be atomic and cannot be replayed', async () => {
  const db = client('sales');
  await prepareOrder(db, 'o1', 6);
  await assertFails(updateDoc(doc(db, 'pedidos', 'o1'), { estado: 'APROBADO', updatedBy: 'sales', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'productos', 'p1'), { stock: 4, ultimoPedidoId: 'o1', updatedAt: serverTimestamp() }));
  await approve(db, 'o1');
  await approve(db, 'o1');
  assert.equal((await getDoc(doc(db, 'productos', 'p1'))).data().stock, 4);
  await assertFails(updateDoc(doc(db, 'productos', 'p1'), { stock: 0, ultimoPedidoId: 'o1', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'pedidos', 'o1'), { estado: 'ANULADO', updatedBy: 'sales', updatedAt: serverTimestamp() }));
});
test('two concurrent approvals cannot oversell the same product', async () => {
  const db = client('sales');
  await prepareOrder(db, 'o1', 6);
  await prepareOrder(db, 'o2', 6);
  const outcomes = await Promise.allSettled([approve(db, 'o1'), approve(db, 'o2')]);
  assert.equal(outcomes.filter(x => x.status === 'fulfilled').length, 1);
  assert.equal((await getDoc(doc(db, 'productos', 'p1'))).data().stock, 4);
});
test('pending rejection cannot modify stock or return to approval', async () => {
  const db = client('sales');
  await prepareOrder(db, 'o1');
  await assertSucceeds(updateDoc(doc(db, 'pedidos', 'o1'), { estado: 'RECHAZADO', updatedBy: 'sales', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(db, 'pedidos', 'o1'), { estado: 'APROBADO', updatedBy: 'sales', updatedAt: serverTimestamp() }));
  assert.equal((await getDoc(doc(db, 'productos', 'p1'))).data().stock, 10);
});
test('sale requires approved order, atomic invoicing, matching amounts and unique order ID', async () => {
  const db = client('sales');
  const o = await prepareOrder(db, 'o1');
  const sale = { pedidoId: 'o1', createdBy: 'sales', creatorUid: 'sales', cliente: o.cliente, ruc: o.ruc,
    productoId: o.productoId, productoNombre: o.productoNombre, cantidad: o.cantidad,
    subtotalCentimos: o.subtotalCentimos, igvCentimos: o.igvCentimos, totalCentimos: o.totalCentimos,
    estado: 'PENDIENTE', createdAt: serverTimestamp() };
  await assertFails(setDoc(doc(db, 'ventas', 'o1'), sale));
  await approve(db, 'o1');
  await assertFails(setDoc(doc(db, 'ventas', 'o1'), sale));
  const bad = writeBatch(db);
  bad.set(doc(db, 'ventas', 'o1'), { ...sale, totalCentimos: 1 });
  bad.update(doc(db, 'pedidos', 'o1'), { estado: 'FACTURADO', ventaId: 'o1', updatedBy: 'sales', updatedAt: serverTimestamp() });
  await assertFails(bad.commit());
  const batch = writeBatch(db);
  batch.set(doc(db, 'ventas', 'o1'), sale);
  batch.update(doc(db, 'pedidos', 'o1'), { estado: 'FACTURADO', ventaId: 'o1', updatedBy: 'sales', updatedAt: serverTimestamp() });
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(db, 'pedidos', 'o1'))).data().estado, 'FACTURADO');
  assert.equal((await getDoc(doc(db, 'productos', 'p1'))).data().stock, 4);
  await assertFails(setDoc(doc(db, 'ventas', 'second'), sale));
  await assertFails(updateDoc(doc(db, 'ventas', 'o1'), { totalCentimos: 1 }));
});
test('warehouse and pending users cannot perform commercial transitions', async () => {
  const db = client('sales');
  await prepareOrder(db, 'o1');
  await assertFails(updateDoc(doc(client('warehouse'), 'pedidos', 'o1'), { estado: 'APROBADO', updatedBy: 'warehouse', updatedAt: serverTimestamp() }));
  await assertFails(getDoc(doc(client('pending'), 'pedidos', 'o1')));
});


test('supplier RUC is unique and only warehouse/admin can create', async () => {
  const db = client('warehouse');
  const supplier = { ruc: '20100070970', nombre: 'Proveedor', direccion: 'Lima', contacto: 'Ana', telefono: '999123456',
    creatorUid: 'warehouse', operationId: crypto.randomUUID(), createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(db, 'proveedores', supplier.ruc), supplier));
  await assertFails(setDoc(doc(db, 'proveedores', supplier.ruc), { ...supplier, operationId: crypto.randomUUID() }));
  await assertFails(setDoc(doc(client('sales'), 'proveedores', '20601030013'), { ...supplier, ruc: '20601030013', creatorUid: 'sales' }));
  await assertFails(setDoc(doc(db, 'proveedores', '20601030013'), { ...supplier, ruc: '20601030013', creatorUid: 'admin' }));
  await assertFails(setDoc(doc(db, 'proveedores', '111'), { ...supplier, ruc: '111' }));
});
test('document metadata is owner-scoped, typed and has the expected storage path', async () => {
  const db = client('warehouse');
  const id = crypto.randomUUID();
  const data = { ownerUid: 'warehouse', ruc: '20100070970', numero: 'F001-123', fecha: '12/10/2026', montoCentimos: 100,
    codigo: '', storagePath: `usuarios/warehouse/documentos/${id}.jpg`, createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(db, 'documentos', 'warehouse', 'items', id), data));
  await assertFails(getDoc(doc(client('admin'), 'documentos', 'warehouse', 'items', id)));
  await assertFails(setDoc(doc(db, 'documentos', 'admin', 'items', id), { ...data, ownerUid: 'admin' }));
  await assertFails(setDoc(doc(db, 'documentos', 'warehouse', 'items', crypto.randomUUID()), { ...data, storagePath: 'external/path' }));
  await assertFails(updateDoc(doc(db, 'documentos', 'warehouse', 'items', id), { montoCentimos: -1 }));
});
const storageClient = (uid, verified = true) => env.authenticatedContext(uid, { email: `${uid}@example.com`, email_verified: verified }).storage();
test('Storage permits owner warehouse JPEG and rejects other owner, roles, anonymous and MIME', async () => {
  const id = crypto.randomUUID();
  const path = `usuarios/warehouse/documentos/${id}.jpg`;
  const bytes = new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
  await assertSucceeds(uploadBytes(ref(storageClient('warehouse'), path), bytes, { contentType: 'image/jpeg' }));
  await assertSucceeds(getBytes(ref(storageClient('warehouse'), path)));
  await assertFails(getBytes(ref(storageClient('admin'), path)));
  await assertFails(uploadBytes(ref(storageClient('warehouse'), `usuarios/admin/documentos/${id}.jpg`), bytes, { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(ref(storageClient('sales'), `usuarios/sales/documentos/${id}.jpg`), bytes, { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(ref(storageClient('warehouse', false), path), bytes, { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(ref(env.unauthenticatedContext().storage(), path), bytes, { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(ref(storageClient('warehouse'), path), bytes, { contentType: 'application/pdf' }));
});
test('Storage rejects oversized uploads and inactive profiles', async () => {
  const path = `usuarios/warehouse/documentos/${crypto.randomUUID()}.jpg`;
  await assertFails(uploadBytes(ref(storageClient('warehouse'), path), new Uint8Array(10 * 1024 * 1024), { contentType: 'image/jpeg' }));
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(), 'usuarios', 'warehouse'), { activo: false }));
  await assertFails(uploadBytes(ref(storageClient('warehouse'), path), new Uint8Array([1]), { contentType: 'image/jpeg' }));
});
