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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class WhereIsMyPhoneReceiver extends BroadcastReceiver {

    private static final String SMS_RECEIVED_ACTION =
            "android.provider.Telephony.SMS_RECEIVED";

    private static final String TRIGGER_WORD = "whereismyphone";
    private static final String STOP_WORD = "stop";
    
    private static final int NUMBER_OF_SMS = 50;

    private static final String TAG = "WhereIsMyPhoneReceiver";
    private static final boolean DEBUG = true;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "onReceive() " + action);

        if (SMS_RECEIVED_ACTION.equals(action)) {
            Bundle bundle = intent.getExtras();
            if (bundle == null) {
                if (DEBUG) Log.d(TAG, "bundle null");
                return;
            }

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null) {
                if (DEBUG) Log.d(TAG, "pdus null");
                return;
            }

            SmsMessage message = SmsMessage.createFromPdu((byte[])pdus[0]);
            if (message == null) {
                if (DEBUG) Log.d(TAG, "message null");
                return;
            }

            String messageBody = message.getMessageBody().toLowerCase();
            String originatingAddress = message.getOriginatingAddress();

            if (DEBUG) Log.i(TAG, "\"" + messageBody + "\"");
            if (DEBUG) Log.i(TAG, "\"" + originatingAddress + "\"");

            if (messageBody.contains(TRIGGER_WORD)) {
                abortBroadcast();
                if (messageBody.contains(STOP_WORD)) {
                    if (DEBUG) Log.i(TAG, "stopping");
                    context.stopService(new Intent(context, WhereIsMyPhoneService.class));
                } else {
                    if (DEBUG) Log.i(TAG, "starting");
                    SharedPreferences pref = context.getSharedPreferences("PREF_NAME", Context.MODE_PRIVATE);
                    Editor editor = pref.edit();
                    editor.putString(WhereIsMyPhoneService.SHARED_PREF_PHONENR, originatingAddress);
                    editor.putInt(WhereIsMyPhoneService.SHARED_PREF_COUNTER, NUMBER_OF_SMS);
                    editor.commit();

                    Intent startIntent = new Intent(context, WhereIsMyPhoneService.class);
                    startIntent.putExtra(WhereIsMyPhoneService.COMMAND_EXTRA, WhereIsMyPhoneService.COMMAND_START);
                    context.startService(startIntent);
                }
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Intent startIntent = new Intent(context, WhereIsMyPhoneService.class);
            startIntent.putExtra(WhereIsMyPhoneService.COMMAND_EXTRA, WhereIsMyPhoneService.COMMAND_START);
            context.startService(startIntent);
        }
    }
}