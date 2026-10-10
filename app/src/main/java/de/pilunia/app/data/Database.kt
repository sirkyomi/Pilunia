package de.pilunia.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun formToString(v: MedForm) = v.name
    @TypeConverter fun stringToForm(v: String) = MedForm.valueOf(v)
    @TypeConverter fun colorToString(v: MedColor) = v.name
    @TypeConverter fun stringToColor(v: String) = MedColor.valueOf(v)
    @TypeConverter fun typeToString(v: ScheduleType) = v.name
    @TypeConverter fun stringToType(v: String) = ScheduleType.valueOf(v)
    @TypeConverter fun statusToString(v: IntakeStatus) = v.name
    @TypeConverter fun stringToStatus(v: String) = IntakeStatus.valueOf(v)
}

@Dao
interface MedicationDao {
    @Transaction
    @Query("SELECT * FROM medication ORDER BY createdAt")
    fun observeAll(): Flow<List<MedWithDoses>>

    @Transaction
    @Query("SELECT * FROM medication ORDER BY createdAt")
    suspend fun getAll(): List<MedWithDoses>

    @Transaction
    @Query("SELECT * FROM medication WHERE id = :id")
    fun observe(id: Long): Flow<MedWithDoses?>

    @Transaction
    @Query("SELECT * FROM medication WHERE id = :id")
    suspend fun get(id: Long): MedWithDoses?

    @Insert suspend fun insert(med: Medication): Long
    @Update suspend fun update(med: Medication)
    @Delete suspend fun delete(med: Medication)

    @Query("DELETE FROM dose WHERE medId = :medId")
    suspend fun deleteDoses(medId: Long)

    @Insert suspend fun insertDoses(doses: List<Dose>)

    @Query("UPDATE medication SET stock = MAX(0, stock + :delta) WHERE id = :id")
    suspend fun changeStock(id: Long, delta: Int)

    @Query("DELETE FROM medication")
    suspend fun deleteAll()
}

@Dao
interface IntakeDao {
    @Query("SELECT * FROM intake WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, minuteOfDay")
    fun observeBetween(from: Long, to: Long): Flow<List<Intake>>

    @Query("SELECT * FROM intake WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, minuteOfDay")
    suspend fun getBetween(from: Long, to: Long): List<Intake>

    @Query("SELECT * FROM intake ORDER BY epochDay, minuteOfDay")
    fun observeAll(): Flow<List<Intake>>

    @Query("SELECT * FROM intake ORDER BY epochDay, minuteOfDay")
    suspend fun getAll(): List<Intake>

    @Query("SELECT * FROM intake WHERE medId = :medId AND doseId = :doseId AND epochDay = :day")
    suspend fun find(medId: Long, doseId: Long, day: Long): Intake?

    @Upsert suspend fun upsert(intake: Intake): Long
    @Delete suspend fun delete(intake: Intake)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Intake>)

    @Query("DELETE FROM intake")
    suspend fun deleteAll()
}

@Dao
interface SnoozeDao {
    @Query("SELECT * FROM snooze")
    fun observeAll(): Flow<List<Snooze>>

    @Query("SELECT * FROM snooze")
    suspend fun getAll(): List<Snooze>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(snooze: Snooze)

    @Query("DELETE FROM snooze WHERE epochDay = :day AND minuteOfDay = :minute")
    suspend fun clear(day: Long, minute: Int)

    @Query("DELETE FROM snooze WHERE epochDay < :day")
    suspend fun clearBefore(day: Long)

    @Query("DELETE FROM snooze")
    suspend fun deleteAll()
}

@Database(entities = [Medication::class, Dose::class, Intake::class, Snooze::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class PiluniaDatabase : RoomDatabase() {
    abstract fun medications(): MedicationDao
    abstract fun intakes(): IntakeDao
    abstract fun snoozes(): SnoozeDao

    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, PiluniaDatabase::class.java, "pilunia.db").build()
    }
}
