package pe.edu.unmsm.siderurgica.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pending", indices = [Index(value = ["owner", "status"])])
data class PendingOperation(@PrimaryKey val id: String, val owner: String, val kind: String, val title: String,
    val payload: String, val status: String, val createdAt: Long, val attempts: Int = 0, val message: String = "")
@Entity(tableName = "cache", primaryKeys = ["owner", "kind"])
data class CacheEntry(val owner: String, val kind: String, val json: String, val cachedAt: Long)
@Entity(tableName = "documents", indices = [Index(value = ["owner"])])
data class LocalDocument(@PrimaryKey val id: String, val owner: String, val imagePath: String, val ruc: String,
    val number: String, val date: String, val amountCents: Long, val code: String, val createdAt: Long,
    val storagePath: String? = null)
@Dao interface LocalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(operation: PendingOperation)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOperations(operations: List<PendingOperation>)
    @Query("SELECT * FROM pending WHERE owner=:owner ORDER BY createdAt DESC") fun observe(owner: String): Flow<List<PendingOperation>>
    @Query("SELECT * FROM pending WHERE owner=:owner AND status='PENDIENTE' ORDER BY createdAt LIMIT 100") suspend fun ready(owner: String): List<PendingOperation>
    @Query("SELECT * FROM pending WHERE id=:id AND owner=:owner") suspend fun operation(id: String, owner: String): PendingOperation?
    @Query("UPDATE pending SET status=:status, message=:message, attempts=:attempts WHERE id=:id AND owner=:owner AND status != 'ENVIADO'")
    suspend fun status(id: String, owner: String, status: String, message: String, attempts: Int)
    @Query("UPDATE pending SET status='PENDIENTE', message='', attempts=0 WHERE id=:id AND owner=:owner AND status IN ('BORRADOR','ERROR')") suspend fun enqueue(id: String, owner: String)
    @Query("DELETE FROM pending WHERE id=:id AND owner=:owner AND status IN ('BORRADOR','ERROR','ENVIADO')") suspend fun delete(id: String, owner: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cache(entry: CacheEntry)
    @Query("SELECT * FROM cache WHERE owner=:owner AND kind=:kind") suspend fun cache(owner: String, kind: String): CacheEntry?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun document(document: LocalDocument)
    @Query("SELECT * FROM documents WHERE owner=:owner ORDER BY createdAt DESC") fun documents(owner: String): Flow<List<LocalDocument>>
    @Query("SELECT * FROM documents WHERE id=:id AND owner=:owner") suspend fun document(id: String, owner: String): LocalDocument?
    @Query("UPDATE documents SET storagePath=:path WHERE id=:id AND owner=:owner") suspend fun uploaded(id: String, owner: String, path: String)
}
@Database(entities = [PendingOperation::class, CacheEntry::class, LocalDocument::class], version = 1, exportSchema = false)
abstract class LocalDatabase : RoomDatabase() { abstract fun dao(): LocalDao }
