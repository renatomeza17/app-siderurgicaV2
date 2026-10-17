import {readFile} from 'node:fs/promises';
import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc,updateDoc,writeBatch,runTransaction,serverTimestamp,Timestamp} from 'firebase/firestore';
import {ref,uploadBytes,getBytes} from 'firebase/storage';
let env;
const stamp=()=>serverTimestamp();
const uid=()=>crypto.randomUUID();
const decision=(quote=false)=>({decisionBy:'sales',decisionAt:stamp(),decisionReason:'Confirmación de prueba documentada',responseChannel:quote?'CORREO':'INTERNO'});
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
const line=(p='p1',q=2,discount=(q>=100?10:q>=50?7:q>=10?5:0))=>({productId:p,name:`Acero ${p.slice(1)}`,quantity:q,unitCents:101,discountPercent:discount,grossCents:q*101,netCents:Math.round(q*101*(100-discount)/100)});
function record(id,lines=[line()],purchase=false){
    const subtotal=lines.reduce((a,l)=>a+l.netCents,0),tax=Math.round(subtotal*.18);
    return {owner:purchase?'warehouse':'sales',client:purchase?'Proveedor':'Cliente',ruc:'20100070970',clientId:purchase?'':'c1',lines,status:'PENDIENTE',subtotalCents:subtotal,taxCents:tax,totalCents:subtotal+tax,reference:'',supplierRuc:purchase?'20100070970':'',budgetCents:purchase?100000:0,lastOperation:id,createdAt:stamp()};
}
async function order(d,lines=[line()]){
    const id=uid(),o=record(id,lines);
    await setDoc(doc(d,'pedidos_v2',id),o);
    await updateDoc(doc(d,'pedidos_v2',id),{status:'APROBADO',lastOperation:uid(),updatedAt:stamp(),...decision()});
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
    const d=db(),id=uid(),q=record(id,[line(),line('p2',3),line('p3',1,0)]);
    await assertSucceeds(setDoc(doc(d,'cotizaciones_v2',id),q));
    for(const patch of [{taxCents:0},{totalCents:1},{client:'Fake'},{lines:[line(),line()]}]){
        const other=uid();await assertFails(setDoc(doc(d,'cotizaciones_v2',other),{...q,lastOperation:other,...patch}));
    }
    await assertFails(setDoc(doc(db('warehouse'),'cotizaciones_v2',uid()),q));
});
test('accepted quote becomes exactly one order with unchanged amounts',async()=>{
    const d=db(),id=uid(),q=record(id);
    await setDoc(doc(d,'cotizaciones_v2',id),q);
    await updateDoc(doc(d,'cotizaciones_v2',id),{status:'APROBADA',lastOperation:uid(),updatedAt:stamp(),...decision(true)});
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
const period='2026-10';
const purchaseRecord=(id,lines=[line('p1',2,0)])=>({...record(id,lines,true),budgetCents:0,budgetPeriod:period,deliveryDate:'2026-10-25',supplierQuote:'COT-PROV-001'});
async function budget(total=100000) {
    return setDoc(doc(db('admin'),'presupuestos_compras',period),{totalCents:total,reservedCents:0,spentCents:0,lastOrder:'',lastOperation:uid(),updatedAt:stamp()});
}
async function purchase(lines=[line('p1',2,0)]) {
    const id=uid();await setDoc(doc(db('warehouse'),'compras_v2',id),purchaseRecord(id,lines));return id;
}
async function purchaseState(id,state='APROBADA',user='admin') {
    const d=db(user),op=uid();
    return runTransaction(d,async tx=>{
        const o=doc(d,'compras_v2',id),old=(await tx.get(o)).data();
        const b=doc(d,'presupuestos_compras',period),data=(await tx.get(b)).data();
        tx.update(o,{status:state,lastOperation:op,updatedAt:stamp(),decisionBy:user,decisionAt:stamp(),decisionReason:'Autorización de reposición registrada',responseChannel:'INTERNO'});
        if(state==='APROBADA'||old.status==='APROBADA')tx.update(b,{reservedCents:data.reservedCents+(state==='APROBADA'?old.totalCents:-old.totalCents),lastOperation:op,lastOrder:id,updatedAt:stamp()});
    });
}
async function receivePurchase(id,op=uid(),omitReceipt=false) {
    const d=db('warehouse');
    return runTransaction(d,async tx=>{
        const audit=doc(d,'operaciones_v2',op),done=await tx.get(audit);if(done.exists())return;
        const o=doc(d,'compras_v2',id),p=(await tx.get(o)).data();
        const b=doc(d,'presupuestos_compras',period),bd=(await tx.get(b)).data();
        const products=await Promise.all(p.lines.map(l=>tx.get(doc(d,'productos',l.productId))));
        tx.update(o,{status:'RECIBIDA',lastOperation:op,updatedAt:stamp(),receivedBy:'warehouse',receivedAt:stamp(),receiptDocument:'F001-123',receiptNotes:'Entrega conforme'});
        tx.update(b,{reservedCents:bd.reservedCents-p.totalCents,spentCents:bd.spentCents+p.totalCents,lastOrder:id,lastOperation:op,updatedAt:stamp()});
        if(!omitReceipt)tx.set(doc(d,'recepciones_v2',id),{orderId:id,operationId:op,receivedBy:'warehouse',createdAt:stamp(),document:'F001-123',notes:'Entrega conforme',lines:p.lines});
        p.lines.forEach((l,i)=>{const mid=op+'_'+l.productId,before=products[i].data().stock;
            tx.set(doc(d,'movimientos_v2',mid),{productId:l.productId,productName:l.name,type:'COMPRA',quantity:l.quantity,beforeStock:before,afterStock:before+l.quantity,reason:'Recepción de compra',actor:'warehouse',source:id,operationId:op,createdAt:stamp()});
            tx.update(doc(d,'productos',l.productId),{stock:before+l.quantity,lastMovement:mid,updatedAt:stamp()});});
        tx.set(audit,{owner:'warehouse',action:'PURCHASE_RECEIVE',target:id,payload:'stable-receipt',createdAt:stamp()});
    });
}
test('purchase authorization and full receipt atomically update three products and budget exactly once',async()=>{
    await budget();const id=await purchase([line('p1',2,0),line('p2',1,0),line('p3',3,0)]);
    await assertSucceeds(purchaseState(id));
    const total=(await getDoc(doc(db('warehouse'),'compras_v2',id))).data().totalCents;
    assert.equal((await getDoc(doc(db('warehouse'),'productos','p1'))).data().stock,10);
    assert.equal((await getDoc(doc(db('warehouse'),'presupuestos_compras',period))).data().reservedCents,total);
    const op=uid();await assertSucceeds(receivePurchase(id,op));await assertSucceeds(receivePurchase(id,op));
    assert.equal((await getDoc(doc(db('warehouse'),'productos','p1'))).data().stock,12);
    assert.equal((await getDoc(doc(db('warehouse'),'productos','p2'))).data().stock,11);
    assert.equal((await getDoc(doc(db('warehouse'),'productos','p3'))).data().stock,13);
    const b=(await getDoc(doc(db('warehouse'),'presupuestos_compras',period))).data();assert.equal(b.reservedCents,0);assert.equal(b.spentCents,total);
    await assertFails(receivePurchase(id));
    await assertFails(updateDoc(doc(db('warehouse'),'recepciones_v2',id),{document:'FORGED'}));
});
test('receipt requires approval, documentary record and atomic stock changes',async()=>{
    await budget();const id=await purchase();
    await assertFails(receivePurchase(id));
    await purchaseState(id);
    await assertFails(receivePurchase(id,uid(),true));
    await assertFails(updateDoc(doc(db('warehouse'),'compras_v2',id),{status:'RECIBIDA',lastOperation:uid(),updatedAt:stamp()}));
    assert.equal((await getDoc(doc(db('warehouse'),'productos','p1'))).data().stock,10);
});
test('only admin allocates and authorizes, insufficient capital rejects and cancellation releases once',async()=>{
    await assertFails(setDoc(doc(db('warehouse'),'presupuestos_compras',period),{totalCents:1000,reservedCents:0,spentCents:0,lastOrder:'',lastOperation:uid(),updatedAt:stamp()}));
    await budget(1);const id=await purchase();
    await assertFails(purchaseState(id));await budget();
    await assertFails(purchaseState(id,'APROBADA','warehouse'));
    await assertSucceeds(purchaseState(id));
    await assertSucceeds(purchaseState(id,'CANCELADA'));
    await assertFails(purchaseState(id,'CANCELADA'));
    assert.equal((await getDoc(doc(db('warehouse'),'presupuestos_compras',period))).data().reservedCents,0);
    await assertFails(receivePurchase(id));
});
test('budget cannot be forged or reduced below reservations; simultaneous approvals cannot overcommit',async()=>{
    await budget(400);const a=await purchase(),b=await purchase();
    const results=await Promise.allSettled([purchaseState(a),purchaseState(b)]);
    assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
    await assertFails(updateDoc(doc(db('admin'),'presupuestos_compras',period),{totalCents:1,lastOrder:'',lastOperation:uid(),updatedAt:stamp()}));
    await assertFails(updateDoc(doc(db('warehouse'),'presupuestos_compras',period),{reservedCents:0,lastOrder:a,lastOperation:uid(),updatedAt:stamp()}));
    await assertFails(getDoc(doc(db('sales'),'presupuestos_compras',period)));
});
test('purchase validates negotiated price separately from sales price and required metadata',async()=>{
    const d=db('warehouse'),id=uid(),l={...line(),unitCents:80,grossCents:160,netCents:160};
    await assertSucceeds(setDoc(doc(d,'compras_v2',id),purchaseRecord(id,[l])));
    const bad=uid();await assertFails(setDoc(doc(d,'compras_v2',bad),{...purchaseRecord(bad),supplierQuote:''}));
    const bad2=uid();await assertFails(setDoc(doc(d,'compras_v2',bad2),record(bad2,[line()],true)));
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

test('new quotes and direct orders reject manually chosen discounts',async()=>{
    const d=db();
    for(const kind of ['cotizaciones_v2','pedidos_v2']){
        const id=uid(),bad=record(id,[line('p1',2,30)]);
        await assertFails(setDoc(doc(d,kind,id),bad));
    }
    const id=uid();
    await assertSucceeds(setDoc(doc(d,'cotizaciones_v2',id),record(id,[line('p1',10,5)])));
});
test('acceptance requires channel, reason, authenticated actor and server timestamp',async()=>{
    const d=db(),id=uid();await setDoc(doc(d,'cotizaciones_v2',id),record(id));
    const base={status:'APROBADA',lastOperation:uid(),updatedAt:stamp()};
    await assertFails(updateDoc(doc(d,'cotizaciones_v2',id),base));
    await assertFails(updateDoc(doc(d,'cotizaciones_v2',id),{...base,...decision(true),decisionBy:'admin'}));
    await assertFails(updateDoc(doc(d,'cotizaciones_v2',id),{...base,...decision(true),decisionReason:''}));
    await assertFails(updateDoc(doc(d,'cotizaciones_v2',id),{...base,...decision(true),responseChannel:'INTERNO'}));
    await assertSucceeds(updateDoc(doc(d,'cotizaciones_v2',id),{...base,...decision(true)}));
    assert.equal((await getDoc(doc(d,'cotizaciones_v2',id))).data().decisionBy,'sales');
});
test('legacy accepted quote keeps its original discount during conversion',async()=>{
    const d=db(),id=uid(),q={...record(id,[line('p1',2,10)]),status:'APROBADA'};
    await env.withSecurityRulesDisabled(ctx=>setDoc(doc(ctx.firestore(),'cotizaciones_v2',id),{...q,createdAt:Timestamp.now()}));
    const op=uid(),b=writeBatch(d);
    b.set(doc(d,'pedidos_v2',id),{...q,status:'PENDIENTE',reference:id,lastOperation:op,createdAt:stamp()});
    b.update(doc(d,'cotizaciones_v2',id),{status:'CONVERTIDA',lastOperation:op,updatedAt:stamp()});
    await assertSucceeds(b.commit());
    assert.equal((await getDoc(doc(d,'pedidos_v2',id))).data().lines[0].discountPercent,10);
});

test('three-line accepted quote completes conversion validation and sale with its audit context',async()=>{
    const d=db(),id=uid(),q=record(id,[line(),line('p2',3),line('p3',1)]);
    await setDoc(doc(d,'cotizaciones_v2',id),q);
    await updateDoc(doc(d,'cotizaciones_v2',id),{status:'APROBADA',lastOperation:uid(),updatedAt:stamp(),...decision(true)});
    const op=uid(),batch=writeBatch(d);
    batch.set(doc(d,'pedidos_v2',id),{...q,createdAt:stamp(),reference:id,lastOperation:op});
    batch.update(doc(d,'cotizaciones_v2',id),{status:'CONVERTIDA',lastOperation:op,updatedAt:stamp()});
    await assertSucceeds(batch.commit());
    await assertSucceeds(updateDoc(doc(d,'pedidos_v2',id),{status:'APROBADO',lastOperation:uid(),updatedAt:stamp(),...decision()}));
    await assertSucceeds(sale(d,id));
    const result=(await getDoc(doc(d,'ventas_v2',id))).data();
    assert.equal(result.totalCents,q.totalCents);
    assert.equal(result.decisionBy,'sales');
    assert.equal(result.decisionReason,'Confirmación de prueba documentada');
    assert.equal(result.lines.length,3);
});
