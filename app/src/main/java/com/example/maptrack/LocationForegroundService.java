package com.example.maptrack;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.security.Provider;

public class LocationForegroundService extends Service {

    private static final String CHANNEL_ID = "LocationChannel";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tracking Location")
                .setContentText("Your location is being tracked")
                .setSmallIcon(R.drawable.btnlogo)
                .build();

        startForeground(1, notification);

        // You can now start location updates here
        // Call fusedLocationClient.requestLocationUpdates() as in the activity

        return START_STICKY;
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Location Tracking Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop location updates when service is destroyed
        // fusedLocationClient.removeLocationUpdates(locationCallback);
    }
}
