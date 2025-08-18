package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.orangesoftware.financisto.model.MyLocation

@Dao
interface MyLocationDao {
    @Query("SELECT * FROM ${DatabaseHelper.LOCATIONS_TABLE}")
    fun getAll(): List<MyLocation>

    @Insert
    fun insert(myLocation: MyLocation): Long

    @Update
    fun update(myLocation: MyLocation)

    @Delete
    fun delete(myLocation: MyLocation)
}
