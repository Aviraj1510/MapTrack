package com.example.maptrack;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LocationDao {
    @Insert
    void insert(LocationEntity locationEntity);

    @Query("SELECT * FROM locations ORDER BY timestamp ASC LIMIT 1")
    LocationEntity getFirstLocationEntity();

    @Query("SELECT * FROM locations ORDER BY timestamp DESC LIMIT 1")
    LocationEntity getLastLocationEntity();

    @Query("SELECT COUNT(*) FROM locations")
    int getLocationCount();

    @Query("SELECT * FROM locations ORDER BY timestamp ASC")
    List<LocationEntity> getAllLocations();
}

