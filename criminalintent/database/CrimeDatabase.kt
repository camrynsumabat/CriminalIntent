package android.bignerdranch.criminalintent.database

import android.bignerdranch.criminalintent.Crime
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Database(entities = [ Crime::class ], version = 1)

@TypeConverters(CrimeTypeConverters::class)

abstract class CrimeDatabase : RoomDatabase() {
    abstract fun crimeDao(): CrimeDao
}
