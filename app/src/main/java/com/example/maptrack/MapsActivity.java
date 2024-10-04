package com.example.maptrack;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private LocationDatabase locationDatabase;
    private LocationDao locationDao;

    private Button btnSave;
    private Executor executor;
    private SearchView searchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        searchView = findViewById(R.id.idSearchView);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationDatabase = LocationDatabase.getInstance(this);
        locationDao = locationDatabase.locationDao();
        executor = Executors.newSingleThreadExecutor();

        btnSave = findViewById(R.id.SaveData);

        setupLocationRequest();
        initializeLocationCallback();

        btnSave.setOnClickListener(v -> exportDatabaseToFile());

        checkPermissions();
        searchView();
        startForegroundService();
    }

    private void setupLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private boolean firstLocationAdded = false;

    private void initializeLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null || mMap == null) {
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                        Geocoder geocoder = new Geocoder(MapsActivity.this);
                        try {
                            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                            if (addresses != null && !addresses.isEmpty()) {
                                String locationName = addresses.get(0).getAddressLine(0);

                                executor.execute(() -> {
                                    LocationEntity lastSavedLocationEntity = locationDao.getLastLocationEntity();

                                    // If this is the first location update, add the first marker
                                    if (!firstLocationAdded) {
                                        firstLocationAdded = true;
                                        insertLocationToDatabase(locationName, location.getLatitude(), location.getLongitude());
                                        runOnUiThread(() -> {
                                            mMap.addMarker(new MarkerOptions().position(currentLatLng)
                                                    .title("Location 1: " + locationName)
                                                    .snippet("Location 1"));
                                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                                        });
                                    } else if (lastSavedLocationEntity != null) {
                                        Location lastSavedLocation = new Location("lastLocation");
                                        lastSavedLocation.setLatitude(lastSavedLocationEntity.getLatitude());
                                        lastSavedLocation.setLongitude(lastSavedLocationEntity.getLongitude());

                                        float distanceInMeters = location.distanceTo(lastSavedLocation);

                                        if (distanceInMeters > 1) {
                                            insertLocationToDatabase(locationName, location.getLatitude(), location.getLongitude());
                                            int locationCount = locationDao.getLocationCount();
                                            runOnUiThread(() -> {
                                                mMap.addMarker(new MarkerOptions().position(currentLatLng)
                                                        .title("Location " + locationCount + ": " + locationName)
                                                        .snippet("Location " + locationCount));
                                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                                            });
                                        }
                                    }
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
    }


    private void insertLocationToDatabase(String locationName, double latitude, double longitude) {
        executor.execute(() -> {
            Log.d("MapsActivity", "Inserting location: " + locationName);
            int locationCount = locationDao.getLocationCount();
            int locationNumber = locationCount + 1;

            LocationEntity locationEntity = new LocationEntity(locationName, latitude, longitude, System.currentTimeMillis(), locationNumber);
            locationDao.insert(locationEntity);

            Log.d("MapsActivity", "Location inserted: " + locationName);
        });
    }

    public void searchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                String location = searchView.getQuery().toString();
                if (location != null && !location.equals("")) {
                    Geocoder geocoder = new Geocoder(MapsActivity.this);
                    try {
                        List<Address> addressList = geocoder.getFromLocationName(location, 1);
                        if (addressList != null && !addressList.isEmpty()) {
                            Address address = addressList.get(0);
                            LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                            runOnUiThread(() -> {
                                mMap.addMarker(new MarkerOptions().position(latLng).title(location));
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10));
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        startLocationUpdates();
        displayLocations();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void displayLocations() {
        executor.execute(() -> {
            LocationEntity firstLocationEntity = locationDao.getFirstLocationEntity();
            LocationEntity lastLocationEntity = locationDao.getLastLocationEntity();

            Log.d("MapsActivity", "First Location: " + (firstLocationEntity != null ? firstLocationEntity.getAddress() : "No data"));
            Log.d("MapsActivity", "Last Location: " + (lastLocationEntity != null ? lastLocationEntity.getAddress() : "No data"));

            runOnUiThread(() -> {
                if (firstLocationEntity != null && lastLocationEntity != null) {
                    String firstLocationName = firstLocationEntity.getAddress();
                    String lastLocationName = lastLocationEntity.getAddress();

                    Toast.makeText(MapsActivity.this,
                            "First Location: " + firstLocationName + "\nLast Location: " + lastLocationName,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MapsActivity.this, "No locations found", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void exportDatabaseToFile() {
        executor.execute(() -> {
            List<LocationEntity> locations = locationDao.getAllLocations();
            if (locations.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(MapsActivity.this, "No data to export", Toast.LENGTH_SHORT).show());
                return;
            }

            File file = new File(getExternalFilesDir(null), "locations.txt");
            try (FileWriter writer = new FileWriter(file)) {
                for (LocationEntity location : locations) {
                    writer.write("Location " + location.getLocationNumber() + ": "
                            + location.getAddress() + ", Latitude: "
                            + location.getLatitude() + ", Longitude: "
                            + location.getLongitude() + "\n");
                }
                runOnUiThread(() -> Toast.makeText(MapsActivity.this, "Data exported to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MapsActivity.this, "Failed to export data", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1);
        } else {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0) {
                boolean locationGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean storageGranted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                if (locationGranted && storageGranted) {
                    startLocationUpdates();
                } else {

                }
            }
        }

    }

    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
    private void stopForegroundService() {
        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        stopService(serviceIntent);
    }
}
