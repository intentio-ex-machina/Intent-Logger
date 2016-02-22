/*
 * Copyright 2016 Carter Yagemann <carter.yagemann@gmail.com>.
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

package com.carteryagemann.intentlogger;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.carteryagemann.AICS.AICSFile;

import java.nio.ByteBuffer;

public class LoggingService extends Service {

    public final static String TAG = "IntentLogger";

    private final Messenger mMessenger = new Messenger(new ServiceHandler());

    private final static int CHECK_INTENT = 1;
    public final static int START_LOGGING = 2;
    public final static int STOP_LOGGING  = 3;
    public final static int GET_COUNT     = 4;
    public final static int SAVE_LOG      = 5;

    private final static int TYPE_ACTIVITY  = 0;
    private final static int TYPE_BROADCAST = 1;
    private final static int TYPE_SERVICE   = 2;

    private int UID;

    private static boolean LOGGING = false;
    private static AICSFile LOG = null;
    private static int LOG_COUNT = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        UID = getApplicationInfo().uid;
    }

    /**
     * The main handler for the logging service. IEM will deliver messages to here.
     */
    private final static class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CHECK_INTENT:
                    logIntent(msg);
                    break;
                case START_LOGGING:
                    enableLogging();
                    break;
                case STOP_LOGGING:
                    Log.v(TAG, "Disabling logging.");
                    LOGGING = false;
                    break;
                case GET_COUNT:
                    sendCount(msg);
                    break;
                case SAVE_LOG:
                    saveLog(msg);
                    break;
            }
        }

        private void enableLogging() {
            Log.v(TAG, "Enabling logging.");
            LOGGING = true;
            LOG_COUNT = 0;
            try {
                String[] version = android.os.Build.VERSION.RELEASE.split(".");
                switch (version.length) {
                    case 1:
                        LOG = new AICSFile(Short.parseShort(version[0]), (byte) 0, (byte) 0);
                        break;
                    case 2:
                        LOG = new AICSFile(Short.parseShort(version[0]),
                                Byte.parseByte(version[1]), (byte) 1);
                        break;
                    case 3:
                        LOG = new AICSFile(Short.parseShort(version[0]),
                                Byte.parseByte(version[1]),
                                Byte.parseByte(version[2]));
                        break;
                    default:
                        LOG = new AICSFile((short) 0, (byte) 0, (byte) 0);
                        break;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get OS version.");
                LOG = new AICSFile((short) 0, (byte) 0, (byte) 0);
            }
        }

        private void logIntent(Message msg) {

            // Log intent
            Bundle data = msg.getData();
            if (data != null && LOGGING && LOG != null) {
                switch (data.getInt("intentType", -1)) {
                    case TYPE_ACTIVITY:
                        //TODO Handle activity intents here!
                        LOG_COUNT++;
                        break;
                    case TYPE_BROADCAST:
                        //TODO Handle broadcast intents here!
                        LOG_COUNT++;
                        break;
                    case TYPE_SERVICE:
                        //TODO Handle service intents here!
                        LOG_COUNT++;
                        break;
                }
            }

            // Allow intent
            if (msg.replyTo != null) {
                try {
                    msg.replyTo.send(msg);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to send response to IEM.");
                }
            }
        }

        private void saveLog(Message msg) {
            Log.v(TAG, "Saving log.");
            if (LOG != null) {
                ByteBuffer output = LOG.toByteBuffer();
                //TODO Write buffer to file.
            }
        }

        private void sendCount(Message msg) {
            if (msg.replyTo != null) {
                try {
                    Message response = Message.obtain(null, GET_COUNT);
                    response.arg1 = LOG_COUNT;
                    msg.replyTo.send(response);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to send response to IEM.");
                }
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Received bind request.");
        int caller = Binder.getCallingUid();
        if (caller < 10000 || caller == UID) return mMessenger.getBinder();
        return null;
    }
}
