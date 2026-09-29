package com.nestor.cuentasclaras.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position, id")
    fun observe(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY position, id")
    suspend fun all(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: Category): Long

    @Insert
    suspend fun insertAll(items: List<Category>)

    @Delete
    suspend fun delete(item: Category)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY position, id")
    fun observe(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY position, id")
    suspend fun all(): List<Account>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: Account): Long

    @Insert
    suspend fun insertAll(items: List<Account>)

    @Delete
    suspend fun delete(item: Account)
}

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observe(): Flow<List<Tx>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    suspend fun all(): List<Tx>

    @Upsert
    suspend fun upsert(item: Tx): Long

    @Delete
    suspend fun delete(item: Tx)

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Query("UPDATE transactions SET accountId = :to WHERE accountId = :from")
    suspend fun moveAccount(from: Long, to: Long)

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategory(from: Long, to: Long)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurrings ORDER BY dayOfMonth, id")
    fun observe(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurrings ORDER BY dayOfMonth, id")
    suspend fun all(): List<Recurring>

    @Query("SELECT * FROM recurrings WHERE id = :id")
    suspend fun get(id: Long): Recurring?

    @Upsert
    suspend fun upsert(item: Recurring): Long

    @Delete
    suspend fun delete(item: Recurring)

    @Query("UPDATE recurrings SET accountId = :to WHERE accountId = :from")
    suspend fun moveAccount(from: Long, to: Long)

    @Query("UPDATE recurrings SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategory(from: Long, to: Long)
}
