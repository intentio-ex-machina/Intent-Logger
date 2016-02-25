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
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.carteryagemann.AICS.AICSFile;
import com.carteryagemann.AICS.ActivityIntentHeader;
import com.carteryagemann.AICS.BroadcastIntentHeader;
import com.carteryagemann.AICS.IntentData;
import com.carteryagemann.AICS.ServiceIntentHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;

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
    private static PackageManager PM;

    private static boolean LOGGING = false;
    private static AICSFile LOG = null;
    private static int LOG_COUNT = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        UID = getApplicationInfo().uid;
        PM = getPackageManager();
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
                    saveLog();
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
                Intent intent = data.getParcelable("intent");
                if (intent == null) {
                    Log.w(TAG, "Failed to log data, no intent!");
                    return;
                }
                switch (data.getInt("intentType", -1)) {
                    case TYPE_ACTIVITY:
                        try {
                            String receiver = null;
                            int receiverUid = 0;
                            if (intent.getComponent() != null) {
                                receiver = intent.getComponent().toShortString();
                                if (PM != null) {
                                    receiverUid = PM.getApplicationInfo(intent.getComponent()
                                            .getPackageName(), 0).uid;
                                }
                            }
                            byte[] clipData = {};
                            if (intent.getClipData() != null) {
                                try {
                                    Parcel clipDataParcel = Parcel.obtain();
                                    intent.getClipData().writeToParcel(clipDataParcel, 0);
                                    clipData = clipDataParcel.marshall();
                                } catch (Exception e) {
                                    Log.w(TAG, "Skipping activity clip data.");
                                }
                            }
                            byte[] extras = {};
                            if (intent.getExtras() != null) {
                                try {
                                    Parcel extrasParcel = Parcel.obtain();
                                    intent.getExtras().writeToParcel(extrasParcel, 0);
                                    extras = extrasParcel.marshall();
                                } catch (Exception e) {
                                    Log.w(TAG, "Skipping activity extras.");
                                }
                            }
                            String categories = "";
                            if (intent.getCategories() != null) {
                                Iterator<String> iterator = intent.getCategories().iterator();
                                while (iterator.hasNext())
                                    categories += iterator.next() + ";";
                            }
                            ActivityIntentHeader head = (ActivityIntentHeader)
                                    new ActivityIntentHeader()
                                    .setCallerComponent(data.getString("callingPackage"))
                                    .setReceiverComponent(receiver)
                                    .setRequestCode(data.getInt("requestCode"))
                                    .setStartFlags(data.getInt("startFlags"))
                                    .setUserID(data.getInt("userId"))
                                    .setTimestamp((int) System.currentTimeMillis() / 1000)
                                    .setOffset((short) (System.currentTimeMillis() % 1000))
                                    .setCallerUID(data.getInt("callerUid", 0))
                                    .setCallerPID(data.getInt("callerPid", 0))
                                    .setReceiverUID(receiverUid);
                            IntentData intentData = new IntentData()
                                    .setAction(intent.getAction())
                                    .setData(intent.getDataString())
                                    .setFlags(intent.getFlags())
                                    .setType(intent.getType())
                                    .setCategory(categories)
                                    .setClipData(clipData)
                                    .setExtras(extras);
                            head.setIntentData(intentData);
                            LOG.appendIntent(head);
                            LOG_COUNT++;
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to log activity intent: " + e.toString());
                        }
                        break;
                    case TYPE_BROADCAST:
                        try {
                            String receiver = null;
                            int receiverUid = 0;
                            if (intent.getComponent() != null) {
                                receiver = intent.getComponent().toShortString();
                                if (PM != null) {
                                    receiverUid = PM.getApplicationInfo(intent.getComponent()
                                            .getPackageName(), 0).uid;
                                }
                            }
                            byte[] clipData = {};
                            if (intent.getClipData() != null) {
                                try {
                                    Parcel clipDataParcel = Parcel.obtain();
                                    intent.getClipData().writeToParcel(clipDataParcel, 0);
                                    clipData = clipDataParcel.marshall();
                                } catch (Exception e) {
                                    Log.w(TAG, "Skipping broadcast clip data.");
                                }
                            }
                            byte[] extras = {};
                            if (intent.getExtras() != null) {
                                try {
                                    Parcel extrasParcel = Parcel.obtain();
                                    intent.getExtras().writeToParcel(extrasParcel, 0);
                                    extras = extrasParcel.marshall();
                                } catch (Exception e) {
                                    Log.w(TAG, "Skipping broadcast extras.");
                                }
                            }
                            String categories = "";
                            if (intent.getCategories() != null) {
                                Iterator<String> iterator = intent.getCategories().iterator();
                                while (iterator.hasNext())
                                    categories += iterator.next() + ";";
                            }
                            BroadcastIntentHeader head = (BroadcastIntentHeader)
                                    new BroadcastIntentHeader()
                                            .setReceiverComponent(receiver)
                                            .setRequestCode(data.getInt("requestCode"))
                                            .setRequiredPermission(data.getString("requiredPermission"))
                                            .setUserID(data.getInt("userId"))
                                            .setTimestamp((int) System.currentTimeMillis() / 1000)
                                            .setOffset((short) (System.currentTimeMillis() % 1000))
                                            .setReceiverUID(receiverUid);
                            IntentData intentData = new IntentData()
                                    .setAction(intent.getAction())
                                    .setData(intent.getDataString())
                                    .setFlags(intent.getFlags())
                                    .setType(intent.getType())
                                    .setCategory(categories)
                                    .setClipData(clipData)
                                    .setExtras(extras);
                            head.setIntentData(intentData);
                            LOG.appendIntent(head);
                            LOG_COUNT++;
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to log broadcast intent: " + e.toString());
                        }
                        break;
                    case TYPE_SERVICE:
                        try {
                            String receiver = null;
                            int receiverUid = 0;
                            if (intent.getComponent() != null) {
                                receiver = intent.getComponent().toShortString();
                                if (PM != null) {
                                    receiverUid = PM.getApplicationInfo(intent.getComponent()
                                            .getPackageName(), 0).uid;
                                }
                            }
                            byte[] clipData = {};
                            if (intent.getClipData() != null) {
                                try {
                                    Parcel clipDataParcel = Parcel.obtain();
                                    intent.getClipData().writeToParcel(clipDataParcel, 0);
                                    clipData = clipDataParcel.marshall();
                                } catch (Exception e) {
                                    Log.w(TAG, "Skipping service clip data.");
                                }
                            }
                            byte[] extras = {};
                            if (intent.getExtras() != null) {
                                try {
                                    Parcel extrasParcel = Parcel.obtain();
                                    intent.getExtras().writeToParcel(extrasParcel, 0);
                                    extras = extrasParcel.marshall();
                                } catch (Exception e) {
                                    Log.w(TAG, "Skipping service extras.");
                                }
                            }
                            String categories = "";
                            if (intent.getCategories() != null) {
                                Iterator<String> iterator = intent.getCategories().iterator();
                                while (iterator.hasNext())
                                    categories += iterator.next() + ";";
                            }
                            ServiceIntentHeader head = (ServiceIntentHeader)
                                    new ServiceIntentHeader()
                                            .setAction(data.getString("IFW_SERVICE_ACTION"))
                                            .setCallerComponent(data.getString("callingPackage"))
                                            .setFlags(data.getInt("flags"))
                                            .setReceiverComponent(receiver)
                                            .setUserID(data.getInt("userId"))
                                            .setTimestamp((int) System.currentTimeMillis() / 1000)
                                            .setOffset((short) (System.currentTimeMillis() % 1000))
                                            .setCallerUID(data.getInt("callerUid", 0))
                                            .setCallerPID(data.getInt("callerPid", 0))
                                            .setReceiverUID(receiverUid);
                            IntentData intentData = new IntentData()
                                    .setAction(intent.getAction())
                                    .setData(intent.getDataString())
                                    .setFlags(intent.getFlags())
                                    .setType(intent.getType())
                                    .setCategory(categories)
                                    .setClipData(clipData)
                                    .setExtras(extras);
                            head.setIntentData(intentData);
                            LOG.appendIntent(head);
                            LOG_COUNT++;
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to log service intent: " + e.toString());
                        }
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

        private void saveLog() {
            Log.v(TAG, "Saving log.");
            if (LOG != null) {
                try {
                    ByteBuffer output = LOG.toByteBuffer();
                    File newFolder = new File(Environment.getExternalStorageDirectory(), "AICS");
                    if (!newFolder.exists()) newFolder.mkdir();
                    File file = new File(newFolder, System.currentTimeMillis() + ".aics");
                    file.createNewFile();
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(output.array());
                    fos.flush();
                    fos.close();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save log! " + e.toString());
                }
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
