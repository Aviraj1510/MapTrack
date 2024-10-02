package com.example.maptrack;



import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;

import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;

import android.util.Log;
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
    private String firstLocation = null;
    private String lastLocation = null;

    SearchView searchView;

    private Executor executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);


        searchView = findViewById(R.id.idSearchView);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        searchView();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Setup Room Database
        locationDatabase = LocationDatabase.getInstance(this);
        locationDao = locationDatabase.locationDao();
        executor = Executors.newSingleThreadExecutor();

        // Initialize location request and callback
        setupLocationRequest();
        initializeLocationCallback();
    }

    private void setupLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000); // Update every 5 seconds
        locationRequest.setFastestInterval(2000); // Fastest update interval
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

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
                        runOnUiThread(() -> {
                            mMap.addMarker(new MarkerOptions().position(currentLatLng).title("Current Location"));
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                        });

                        // Get address from Geocoder
                        Geocoder geocoder = new Geocoder(MapsActivity.this);
                        try {
                            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                            if (addresses != null && !addresses.isEmpty()) {
                                String locationName = addresses.get(0).getAddressLine(0);

                                // Get the last saved location from Room database
                                executor.execute(() -> {
                                    LocationEntity lastSavedLocationEntity = locationDao.getLastLocationEntity();

                                    if (lastSavedLocationEntity != null) {
                                        // Create a Location object for distance calculation
                                        Location lastSavedLocation = new Location("lastLocation");
                                        Log.e("LastLocation", "Location" +  lastSavedLocation);
                                        lastSavedLocation.setLatitude(lastSavedLocationEntity.getLatitude());
                                        lastSavedLocation.setLongitude(lastSavedLocationEntity.getLongitude());

                                        // Calculate distance from the last saved location
                                        float distanceInMeters = location.distanceTo(lastSavedLocation);

                                        // If distance is more than 5 km, save the new location
                                        if (distanceInMeters > 5000) {
                                            insertLocationToDatabase(locationName, location.getLatitude(), location.getLongitude());
                                        }
                                    } else {
                                        // If no previous location exists, save the current one
                                        insertLocationToDatabase(locationName, location.getLatitude(), location.getLongitude());
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
            LocationEntity locationEntity = new LocationEntity(locationName, latitude, longitude, System.currentTimeMillis());
            locationDao.insert(locationEntity);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    @Override
    protected void onPause() {
        super.onPause();
        fusedLocationClient.removeLocationUpdates(locationCallback);
        displayLocations();
    }

    @Override
    protected void onStop() {
        super.onStop();
        displayLocations(); // Optional: Display on activity stop
    }

    public void displayLocations() {
        executor.execute(() -> {
            // Fetch first and last location from the database
            LocationEntity firstLocationEntity = locationDao.getFirstLocationEntity();
            LocationEntity lastLocationEntity = locationDao.getLastLocationEntity();

            // Show the locations on the UI (using Toast for now)
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

}
