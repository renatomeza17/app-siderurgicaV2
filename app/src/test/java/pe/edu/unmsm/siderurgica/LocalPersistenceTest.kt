package pe.edu.unmsm.siderurgica

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pe.edu.unmsm.siderurgica.data.local.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class LocalPersistenceTest {
    @Test fun commercialCommandsSurviveReopenWithoutChangingIdentityOrPayload() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "commercial-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, LocalDatabase::class.java, name).allowMainThreadQueries().build()
        val commands = listOf("QUOTE_CREATE", "ORDER_CREATE", "ORDER_STATE", "SALE_CREATE", "PURCHASE_CREATE", "PURCHASE_STATE", "BUDGET_SET", "PURCHASE_RECEIVE", "MOVEMENT")
        try {
            commands.forEachIndexed { index, action ->
                val command = pe.edu.unmsm.siderurgica.domain.model.BusinessCommand(action, target = "target-$index",
                    lines = listOf(pe.edu.unmsm.siderurgica.domain.model.BusinessLineDraft("p1", 3, 101, 5)),
                    budgetPeriod="2026-10",deliveryDate="2026-10-25",supplierQuote="COT-001",receiptDocument="F001-123",receiptNotes="Conforme")
                db.dao().insert(PendingOperation("stable-$index", "alice", "BUSINESS", action,
                    com.google.gson.Gson().toJson(command), "PENDIENTE", index.toLong()))
            }
            db.close()
            db = Room.databaseBuilder(context, LocalDatabase::class.java, name).allowMainThreadQueries().build()
            assertTrue(db.dao().ready("bob").isEmpty())
            val restored = db.dao().ready("alice")
            assertEquals(commands.size, restored.size)
            restored.forEachIndexed { index, row ->
                val command = com.google.gson.Gson().fromJson(row.payload, pe.edu.unmsm.siderurgica.domain.model.BusinessCommand::class.java)
                assertEquals("stable-$index", row.id)
                assertEquals(commands[index], command.action)
                assertEquals(3L, command.lines.single().quantity)
                assertEquals(101L, command.lines.single().priceCents)
                assertEquals("2026-10",command.budgetPeriod)
                assertEquals("2026-10-25",command.deliveryDate)
                assertEquals("COT-001",command.supplierQuote)
                assertEquals("F001-123",command.receiptDocument)
                assertEquals("Conforme",command.receiptNotes)
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun draftSurvivesDatabaseReopenAndIsPartitionedByUser() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "queue-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, LocalDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            db.dao().insert(PendingOperation("stable-id", "alice", "QUOTE", "Cliente", "{}", "BORRADOR", 1))
            db.close()
            db = Room.databaseBuilder(context, LocalDatabase::class.java, name).allowMainThreadQueries().build()
            assertEquals("stable-id", db.dao().observe("alice").first().single().id)
            assertTrue(db.dao().observe("bob").first().isEmpty())
            assertTrue(db.dao().ready("alice").isEmpty())
            db.dao().enqueue("stable-id", "bob")
            assertEquals("BORRADOR", db.dao().operation("stable-id", "alice")!!.status)
            db.dao().enqueue("stable-id", "alice")
            assertEquals("stable-id", db.dao().ready("alice").single().id)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun acknowledgedOperationCannotBeRequeuedOrChanged() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            dao.insert(PendingOperation("id", "alice", "QUOTE", "Cliente", "{}", "PENDIENTE", 1))
            dao.delete("id", "alice")
            assertNotNull(dao.operation("id", "alice"))
            dao.status("id", "alice", "ERROR", "Error", 1)
            dao.enqueue("id", "alice")
            assertEquals("id", dao.ready("alice").single().id)
            dao.status("id", "alice", "ENVIADO", "Confirmado", 2)
            dao.enqueue("id", "alice")
            dao.status("id", "alice", "ERROR", "No debe cambiar", 3)
            assertEquals("ENVIADO", dao.operation("id", "alice")!!.status)
            assertTrue(dao.ready("alice").isEmpty())
        } finally { db.close() }
    }
    @Test fun cacheAndDocumentsAreIsolatedByOwner() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LocalDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            dao.cache(CacheEntry("alice", "profile", "profile-json", 1))
            assertNull(dao.cache("bob", "profile"))
            dao.document(LocalDocument("doc", "alice", "/local.jpg", "", "Código", "12/10/2026", 0, "QR", 1))
            assertTrue(dao.documents("bob").first().isEmpty())
            dao.uploaded("doc", "bob", "wrong")
            assertNull(dao.document("doc", "alice")!!.storagePath)
            dao.uploaded("doc", "alice", "correct")
            assertEquals("correct", dao.document("doc", "alice")!!.storagePath)
        } finally { db.close() }
    }
    @Test fun receiptAndPhotoPersistTogetherAndPhotoRetriesDoNotRequeueStock()=runBlocking {
        val context=RuntimeEnvironment.getApplication();val name="purchase-photo-${java.util.UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,LocalDatabase::class.java,name).allowMainThreadQueries().build()
        val gson=com.google.gson.Gson()
        try {
            val receipt=PendingOperation("receipt","alice","BUSINESS","Recepción",gson.toJson(pe.edu.unmsm.siderurgica.domain.model.BusinessCommand("PURCHASE_RECEIVE",target="order",receiptDocument="G001-123")),"PENDIENTE",1)
            val photo=PendingOperation("photo","alice","PURCHASE_PHOTO","Foto",gson.toJson(pe.edu.unmsm.siderurgica.domain.model.PurchasePhoto("order","/local/image.jpg","receipt")),"PENDIENTE",2)
            db.dao().insertOperations(listOf(receipt,photo));db.close()
            db=Room.databaseBuilder(context,LocalDatabase::class.java,name).allowMainThreadQueries().build()
            assertEquals(listOf("receipt","photo"),db.dao().ready("alice").map{it.id})
            assertTrue(db.dao().ready("bob").isEmpty())
            assertEquals("receipt",gson.fromJson(db.dao().operation("photo","alice")!!.payload,pe.edu.unmsm.siderurgica.domain.model.PurchasePhoto::class.java).receiptOperation)
            db.dao().status("receipt","alice","ENVIADO","Confirmada",1)
            db.dao().status("photo","alice","ERROR","Sin Storage",1);db.dao().enqueue("photo","alice")
            assertEquals(listOf("photo"),db.dao().ready("alice").map{it.id})
            assertEquals("ENVIADO",db.dao().operation("receipt","alice")!!.status)
            assertTrue(runCatching{db.dao().insertOperations(listOf(receipt.copy(id="must-rollback"),photo))}.isFailure)
            assertNull(db.dao().operation("must-rollback","alice"))
        } finally {db.close();context.deleteDatabase(name)}
    }
}
