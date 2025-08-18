package ru.orangesoftware.financisto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ru.orangesoftware.financisto.model.Project

@Dao
interface ProjectDao {
    @Query("SELECT * FROM ${DatabaseHelper.PROJECT_TABLE}")
    fun getAll(): List<Project>

    @Insert
    fun insert(project: Project): Long

    @Update
    fun update(project: Project)

    @Delete
    fun delete(project: Project)
}
