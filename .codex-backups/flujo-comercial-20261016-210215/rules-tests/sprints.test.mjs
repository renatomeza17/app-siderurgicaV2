import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,updateDoc,writeBatch,runTransaction,serverTimestamp,Timestamp} from 'firebase/firestore';
import {ref,uploadBytes,getBytes} from 'firebase/storage';
let env;
const stamp=()=>serverTimestamp();
const uid=()=>crypto.randomUUID();
const client=(u)=>env.authenticatedContext(u,{email:`${u}@example.com`,email_verified:true});
const db=(u='sales')=>client(u).firestore();
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-siderurgica',firestore:{host:'127.0.0.1',port:8080,rules:await readFile('../firestore.rules','utf8')},storage:{host:'127.0.0.1',port:9199,rules:await readFile('../storage.rules','utf8')}})});
after(async()=>{await env?.cleanup()});
beforeEach(async()=>{
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async c=>{
        const d=c.firestore();
        for(const [u,rol] of [['sales','VENTAS'],['warehouse','ALMACEN'],['admin','ADMIN'],['pending','PENDIENTE']])await setDoc(doc(d,'usuarios',u),{uid:u,email:`${u}@example.com`,nombre:u,rol,activo:true,createdAt:Timestamp.now(),updatedAt:Timestamp.now()});
        for(let n=1;n<=3;n++)await setDoc(doc(d,'productos',`p${n}`),{nombre:`Acero ${n}`,categoria:'General',precioCentimos:101,stock:10,stockMinimo:5,createdAt:Timestamp.now()});
        await setDoc(doc(d,'clientes_v2','c1'),{name:'Cliente',ruc:'20100070970',owner:'sales',createdAt:Timestamp.now()});
        await setDoc(doc(d,'proveedores','20100070970'),{nombre:'Proveedor',ruc:'20100070970'});
    });
});
const line=(p='p1',q=2,discount=5)=>({productId:p,name:`Acero ${p.slice(1)}`,quantity:q,unitCents:101,discountPercent:discount,grossCents:q*101,netCents:Math.round(q*101*(100-discount)/100)});
function record(id,lines=[line()],purchase=false){
    const subtotal=lines.reduce((a,l)=>a+l.netCents,0),tax=Math.round(subtotal*.18);
    return {owner:purchase?'warehouse':'sales',client:purchase?'Proveedor':'Cliente',ruc:'20100070970',clientId:purchase?'':'c1',lines,status:'PENDIENTE',subtotalCents:subtotal,taxCents:tax,totalCents:subtotal+tax,reference:'',supplierRuc:purchase?'20100070970':'',budgetCents:purchase?100000:0,lastOperation:id,createdAt:stamp()};
}
async function order(d,lines=[line()]){
    const id=uid(),o=record(id,lines);
    await setDoc(doc(d,'pedidos_v2',id),o);
    await updateDoc(doc(d,'pedidos_v2',id),{status:'APROBADO',lastOperation:uid(),updatedAt:stamp()});
    return id;
}
async function sale(d,id,op=uid()){
    return runTransaction(d,async tx=>{
        const audit=doc(d,'operaciones_v2',op),done=await tx.get(audit);
        if(done.exists())return;
        const oref=doc(d,'pedidos_v2',id),orefdata=(await tx.get(oref)).data();
        assert.equal(orefdata.status,'APROBADO');
        const products=await Promise.all(orefdata.lines.map(l=>tx.get(doc(d,'productos',l.productId))));
        for(let i=0;i<products.length;i++)if(products[i].data().stock<orefdata.lines[i].quantity)throw Error('stock');
        const {updatedAt,...data}=orefdata;
        tx.set(doc(d,'ventas_v2',id),{...data,status:'CONFIRMADA',reference:id,lastOperation:op,createdAt:stamp()});
        tx.update(oref,{status:'FACTURADO',lastOperation:op,updatedAt:stamp()});
        for(let i=0;i<products.length;i++){
            const l=orefdata.lines[i],before=products[i].data().stock,mid=`${op}_${l.productId}`;
            tx.set(doc(d,'movimientos_v2',mid),{productId:l.productId,productName:l.name,type:'VENTA',quantity:l.quantity,beforeStock:before,afterStock:before-l.quantity,reason:'Confirmación de venta',actor:'sales',source:id,operationId:op,createdAt:stamp()});
            tx.update(doc(d,'productos',l.productId),{stock:before-l.quantity,lastMovement:mid,updatedAt:stamp()});
        }
        tx.set(audit,{owner:'sales',action:'SALE_CREATE',target:id,payload:'stable-payload',createdAt:stamp()});
    });
}
test('v2 quote validates three lines, prices, discounts, tax and selected client',async()=>{
    const d=db(),id=uid(),q=record(id,[line(),line('p2',3,10),line('p3',1,0)]);
    await assertSucceeds(setDoc(doc(d,'cotizaciones_v2',id),q));
    for(const patch of [{taxCents:0},{totalCents:1},{client:'Fake'},{lines:[line(),line()]}]){
        const other=uid();await assertFails(setDoc(doc(d,'cotizaciones_v2',other),{...q,lastOperation:other,...patch}));
    }
    await assertFails(setDoc(doc(db('warehouse'),'cotizaciones_v2',uid()),q));
});
test('accepted quote becomes exactly one order with unchanged amounts',async()=>{
    const d=db(),id=uid(),q=record(id);
    await setDoc(doc(d,'cotizaciones_v2',id),q);
    await updateDoc(doc(d,'cotizaciones_v2',id),{status:'APROBADA',lastOperation:uid(),updatedAt:stamp()});
    const op=uid(),batch=writeBatch(d);
    batch.set(doc(d,'pedidos_v2',id),{...q,createdAt:stamp(),reference:id,lastOperation:op});
    batch.update(doc(d,'cotizaciones_v2',id),{status:'CONVERTIDA',lastOperation:op,updatedAt:stamp()});
    await assertSucceeds(batch.commit());
    await assertFails(setDoc(doc(d,'pedidos_v2',id),{...q,reference:id,lastOperation:op}));
});
test('approval preserves stock; multi-product confirmation changes all stocks once',async()=>{
    const d=db(),id=await order(d,[line(),line('p2',3),line('p3',1)]);
    assert.equal((await getDoc(doc(d,'productos','p1'))).data().stock,10);
    const op=uid();await assertSucceeds(sale(d,id,op));await assertSucceeds(sale(d,id,op));
    assert.equal((await getDoc(doc(d,'productos','p1'))).data().stock,8);
    assert.equal((await getDoc(doc(d,'productos','p2'))).data().stock,7);
    assert.equal((await getDoc(doc(d,'productos','p3'))).data().stock,9);
    assert.equal((await getDoc(doc(d,'pedidos_v2',id))).data().status,'FACTURADO');
    await assert.rejects(sale(d,id));
});
test('concurrent sales cannot oversell; losing sale leaves all its products unchanged',async()=>{
    const d=db(),a=await order(d,[line('p1',7,0),line('p2',1,0)]),b=await order(d,[line('p1',7,0),line('p3',1,0)]);
    const results=await Promise.allSettled([sale(d,a),sale(d,b)]);
    assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
    assert.equal((await getDoc(doc(d,'productos','p1'))).data().stock,3);
    const second=(await getDoc(doc(d,'productos','p2'))).data().stock;
    const third=(await getDoc(doc(d,'productos','p3'))).data().stock;
    assert.equal(second+third,19);
});
test('standalone sale and naked stock edit are denied',async()=>{
    const d=db(),id=await order(d);
    await assertFails(setDoc(doc(d,'ventas_v2',id),{...record(uid()),status:'CONFIRMADA',reference:id}));
    await assertFails(updateDoc(doc(d,'productos','p1'),{stock:0,lastMovement:uid(),updatedAt:stamp()}));
});
test('warehouse movement is atomic, audited and immutable; sales cannot forge manual entry',async()=>{
    async function movement(user){const d=db(user),id=uid(),mid=id+'_p1',b=writeBatch(d);
        b.set(doc(d,'movimientos_v2',mid),{productId:'p1',productName:'Acero 1',type:'ENTRADA',quantity:4,beforeStock:10,afterStock:14,reason:'Apertura de almacén',actor:user,source:'',operationId:id,createdAt:stamp()});
        b.update(doc(d,'productos','p1'),{stock:14,lastMovement:mid,updatedAt:stamp()});return b.commit();}
    await assertFails(movement('sales'));await assertSucceeds(movement('warehouse'));
    assert.equal((await getDoc(doc(db('warehouse'),'productos','p1'))).data().stock,14);
});
test('purchase enforces budget and supplier and reception updates three products exactly once',async()=>{
    const d=db('warehouse'),id=uid(),p=record(id,[line('p1',2,0),line('p2',1,0),line('p3',3,0)],true);
    await assertFails(setDoc(doc(d,'compras_v2',id),{...p,budgetCents:1}));
    await assertSucceeds(setDoc(doc(d,'compras_v2',id),p));
    const op=uid(),b=writeBatch(d);
    b.update(doc(d,'compras_v2',id),{status:'RECIBIDA',lastOperation:op,updatedAt:stamp()});
    for(const l of p.lines){const mid=op+'_'+l.productId;
        b.set(doc(d,'movimientos_v2',mid),{productId:l.productId,productName:l.name,type:'COMPRA',quantity:l.quantity,beforeStock:10,afterStock:10+l.quantity,reason:'Recepción de compra',actor:'warehouse',source:id,operationId:op,createdAt:stamp()});
        b.update(doc(d,'productos',l.productId),{stock:10+l.quantity,lastMovement:mid,updatedAt:stamp()});}
    await assertSucceeds(b.commit());
    assert.equal((await getDoc(doc(d,'productos','p1'))).data().stock,12);
    await assertFails(updateDoc(doc(d,'compras_v2',id),{status:'RECIBIDA',lastOperation:uid(),updatedAt:stamp()}));
});
test('purchase cannot be marked received without stock movements',async()=>{
    const d=db('warehouse'),id=uid();await setDoc(doc(d,'compras_v2',id),record(id,[line('p1',2,0)],true));
    await assertFails(updateDoc(doc(d,'compras_v2',id),{status:'RECIBIDA',lastOperation:uid(),updatedAt:stamp()}));
});
test('FCM device tokens are private, role checked and cannot be substituted across accounts',async()=>{
    const id=uid(),data={token:'demo-token-with-at-least-twenty-characters',updatedAt:stamp()};
    await assertSucceeds(setDoc(doc(db('warehouse'),'usuarios','warehouse','dispositivos',id),data));
    await assertFails(getDoc(doc(db('sales'),'usuarios','warehouse','dispositivos',id)));
    await assertFails(setDoc(doc(db('sales'),'usuarios','sales','dispositivos',id),data));
    await assertFails(setDoc(doc(db('admin'),'usuarios','warehouse','dispositivos',id),data));
    await assertFails(setDoc(doc(db('admin'),'alertas_envios','p1'),{signature:'forged'}));
});
test('PDF metadata requires confirmed sale and remains immutable',async()=>{
    const d=db(),id=await order(d);await sale(d,id);
    const key=id+'_FACTURA',data={saleId:id,type:'FACTURA',storagePath:`usuarios/sales/comprobantes/${key}.pdf`,createdAt:stamp()};
    await assertSucceeds(setDoc(doc(d,'comprobantes_v2','sales','items',key),data));
    await assertFails(updateDoc(doc(d,'comprobantes_v2','sales','items',key),{type:'BOLETA'}));
    await assertFails(getDoc(doc(db('warehouse'),'comprobantes_v2','sales','items',key)));
});
test('PDF Storage accepts own sales PDF and denies other owner, wrong MIME and warehouse role',async()=>{
    const key=uid()+'_BOLETA.pdf',bytes=new TextEncoder().encode('%PDF-1.4 demo');
    await assertSucceeds(uploadBytes(ref(client('sales').storage(),`usuarios/sales/comprobantes/${key}`),bytes,{contentType:'application/pdf'}));
    await assertFails(uploadBytes(ref(client('warehouse').storage(),`usuarios/warehouse/comprobantes/${key}`),bytes,{contentType:'application/pdf'}));
    await assertFails(getBytes(ref(client('admin').storage(),`usuarios/sales/comprobantes/${key}`)));
    await assertFails(uploadBytes(ref(client('sales').storage(),`usuarios/sales/comprobantes/${uid()}_FACTURA.pdf`),bytes,{contentType:'image/jpeg'}));
});
