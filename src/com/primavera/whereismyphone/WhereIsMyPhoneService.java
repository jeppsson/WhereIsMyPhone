/*
 * Copyright (C) 2012 Mathias Jeppsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.primavera.whereismyphone;

import java.util.Iterator;
import java.util.List;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.telephony.SmsManager;
import android.text.format.DateUtils;
import android.util.Log;

public class WhereIsMyPhoneService extends Service {

    private static final String TAG = "WhereIsMyPhoneService";
    private static final boolean DEBUG = true;

    public static final String COMMAND_EXTRA = "command";
    public static final String COMMAND_START = "start";
    private static final String ALARM_INTENT = "com.primavera.whereismyphone.ALARM";

    public static final String SHARED_PREF_PHONENR = "phonenr";
    public static final String SHARED_PREF_COUNTER = "counter";

    private String mPhoneNumber = "";
    private Location mLocation = null;
    private boolean mNewLocation = false;
    private int mCounter;
    private int mSemePositionCounter = 4;
    private PendingIntent mPendingAlarmIntent;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate()");
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ALARM_INTENT);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            if (DEBUG) Log.i(TAG, "onStartCommand() - intent null");
        } else {
            final String command = intent.getStringExtra(WhereIsMyPhoneService.COMMAND_EXTRA);
            if (DEBUG) Log.i(TAG, "onStartCommand() " + command);
            
            if (WhereIsMyPhoneService.COMMAND_START.equals(command)) {
                SharedPreferences pref = getSharedPreferences("PREF_NAME", Context.MODE_PRIVATE);
                mPhoneNumber = pref.getString(SHARED_PREF_PHONENR, null);
                if (mPhoneNumber == null) {
                    if (DEBUG) Log.i(TAG, "no phone number, stopping.");
                    stopSelf();
                } else {
                    mCounter = pref.getInt(SHARED_PREF_COUNTER, 50);
                    if (DEBUG) Log.i(TAG, "PhoneNumber:" + mPhoneNumber + " Counter:" + mCounter);

                    // Register recurring alarm.
                    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    Intent i = new Intent(WhereIsMyPhoneService.ALARM_INTENT);
                    mPendingAlarmIntent = PendingIntent.getBroadcast(this, 0, i, 0);
                    alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime(),
                            AlarmManager.INTERVAL_HALF_HOUR, mPendingAlarmIntent);

                    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

                    // Register location updates.
                    List<String> list = locationManager.getProviders(true);
                    Iterator<String> it = list.iterator();
                    while(it.hasNext()) {
                        String provider = it.next();
                        Log.i(TAG, "provider: " + provider);
                        if (LocationManager.PASSIVE_PROVIDER.equals(provider)) {
                            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5 * 60 * 1000, 100, locationListener_passive);
                        } else if (LocationManager.GPS_PROVIDER.equals(provider)) {
                            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5 * 60 * 1000, 100, locationListener_gps);
                        } else if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
                            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5 * 60 * 1000, 100, locationListener_network);
                        }
                    }

                    // Try to get current location.
                    Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (DEBUG) Log.i(TAG, "lastKnowLocation(gps):" + gps);
                    Location network = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                    if (DEBUG) Log.i(TAG, "lastKnowLocation(passive):" + network);

                    mLocation = null;

                    // Find location with best accuracy.
                    if (gps != null && gps.hasAccuracy()) {
                        if (network != null && network.hasAccuracy()) {
                            if (gps.getAccuracy() < network.getAccuracy()) {
                                if (DEBUG) Log.i(TAG, "gps < network");
                                mLocation = gps;
                            } else {
                                if (DEBUG) Log.i(TAG, "network < gps");
                                mLocation = network;
                            }
                        } else {
                            // Use GPS as we are missing network.
                            if (DEBUG) Log.i(TAG, "use gps. no network.");
                            mLocation = gps;
                        }
                    } else if (network != null && network.hasAccuracy()) {
                        // Use network as we are missing GPS.
                        if (DEBUG) Log.i(TAG, "use network. no gps.");
                        mLocation = network;
                    }

                    // Send location.
                    if (mLocation != null) {
                        String messageToSend = "http://maps.google.com/maps?q=" +
                                String.valueOf(mLocation.getLatitude()) + "," +
                                String.valueOf(mLocation.getLongitude()) +
                                " Accuracy:" + String.valueOf(mLocation.getAccuracy()) +
                                " Time:" + DateUtils.getRelativeTimeSpanString(mLocation.getTime(), System.currentTimeMillis(), 0);
                        sendMessage(messageToSend);
                        if (DEBUG) Log.i(TAG, "lastKnownLocation: " + messageToSend.length());
                    }
                }
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.i(TAG, "onDestroy()");
        super.onDestroy();

        unregisterReceiver(mReceiver);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(locationListener_gps);
        locationManager.removeUpdates(locationListener_network);
        locationManager.removeUpdates(locationListener_passive);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(mPendingAlarmIntent);

        SharedPreferences pref = getSharedPreferences("PREF_NAME", Context.MODE_PRIVATE);
        pref.edit().clear().commit();
    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(TAG, "onReceive() " + action);

            if (ALARM_INTENT.equals(action)) {
                if (mLocation != null && mNewLocation) {
                    String messageToSend = "http://maps.google.com/maps?q=" +
                            String.valueOf(mLocation.getLatitude()) + "," +
                            String.valueOf(mLocation.getLongitude()) +
                            " Accuracy:" + String.valueOf(mLocation.getAccuracy()) + "m";
                    if (mCounter == 0) {
                        if (DEBUG) Log.i(TAG, "last message");
                        stopSelf();
                        messageToSend += " (last message)";
                    }
                    sendMessage(messageToSend);

                    // Do not send unless we get a new better location.
                    mNewLocation = false;
                } else if (mLocation != null) {
                    mSemePositionCounter--;
                    if (mSemePositionCounter == 0) {
                        final String messageToSend = "same http://maps.google.com/maps?q=" +
                                String.valueOf(mLocation.getLatitude()) + "," +
                                String.valueOf(mLocation.getLongitude()) +
                                " Accuracy:" + String.valueOf(mLocation.getAccuracy()) + "m";
                        sendMessage(messageToSend);
                    } else {
                        if (DEBUG) Log.i(TAG, "no new location");
                    }
                } else {
                    final String messageToSend = "no location";
                    sendMessage(messageToSend);
                }
            }
        }
    };

    private void sendMessage(String messageToSend) {
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(mPhoneNumber, null, messageToSend, null, null);
        if (DEBUG) Log.i(TAG, messageToSend);

        mSemePositionCounter = 4;
        mCounter--;

        if (DEBUG) Log.i(TAG, "counter: " + mCounter);
        if ((mCounter % 10) == 0) {
            if (DEBUG) Log.i(TAG, "store counter");
            SharedPreferences pref = getSharedPreferences("PREF_NAME", Context.MODE_PRIVATE);
            pref.edit().putInt(SHARED_PREF_COUNTER, mCounter).commit();
        }
    }

    private void useNewLocationIfBetter(Location location) {
        if (DEBUG) Log.i(TAG, "old location:" + mLocation);
        if (DEBUG) Log.i(TAG, "new location:" + location);
        if (mLocation == null) {
            mLocation = location;
            mNewLocation = true;
            if (DEBUG) Log.i(TAG, "using new location - no old location");
        } else if (location.hasAccuracy()) {
            if (mLocation.hasAccuracy() &&
                    (location.getAccuracy() < mLocation.getAccuracy())) {
                mLocation = location;
                mNewLocation = true;
                if (DEBUG) Log.i(TAG, "using new location - better accuracy");
            } else if (mLocation.distanceTo(location) > location.getAccuracy()) {
                mLocation = location;
                mNewLocation = true;
                if (DEBUG) Log.i(TAG, "using new location - distance greater than new accuracy");
            }
        }
    }

    LocationListener locationListener_gps = new LocationListener() {
        public void onLocationChanged(Location location) {
            useNewLocationIfBetter(location);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (DEBUG) Log.i(TAG, "onStatusChanged() provider: " + provider + " status: " + status);
        }

        public void onProviderEnabled(String provider) {
            if (DEBUG) Log.i(TAG, "onProviderEnabled() provider: " + provider);
        }

        public void onProviderDisabled(String provider) {
            if (DEBUG) Log.i(TAG, "onProviderDisabled() provider: " + provider);
        }
      };

  	LocationListener locationListener_network = new LocationListener() {
        public void onLocationChanged(Location location) {
            useNewLocationIfBetter(location);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (DEBUG) Log.i(TAG, "onStatusChanged() provider: " + provider + " status: " + status);
        }

        public void onProviderEnabled(String provider) {
            if (DEBUG) Log.i(TAG, "onProviderEnabled() provider: " + provider);
        }

        public void onProviderDisabled(String provider) {
            if (DEBUG) Log.i(TAG, "onProviderDisabled() provider: " + provider);
        }
      };

  	LocationListener locationListener_passive = new LocationListener() {
        public void onLocationChanged(Location location) {
            useNewLocationIfBetter(location);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (DEBUG) Log.i(TAG, "onStatusChanged() provider: " + provider + " status: " + status);
        }

        public void onProviderEnabled(String provider) {
            if (DEBUG) Log.i(TAG, "onProviderEnabled() provider: " + provider);
        }

        public void onProviderDisabled(String provider) {
            if (DEBUG) Log.i(TAG, "onProviderDisabled() provider: " + provider);
        }
      };

      @Override
      public IBinder onBind(Intent arg0) { return null; }
}