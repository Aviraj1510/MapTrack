package com.example.maptrack;



import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {LocationEntity.class}, version = 1)
public abstract class LocationDatabase extends RoomDatabase {
    private static LocationDatabase instance;

    public abstract LocationDao locationDao();

    public static synchronized LocationDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            LocationDatabase.class, "location_database")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
