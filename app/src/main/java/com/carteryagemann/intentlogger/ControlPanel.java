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
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class ControlPanel extends AppCompatActivity {

    private static final int SERVICE_POLLING_RATE = 2000;

    Handler mInternalHandler;
    Messenger mMessenger = new Messenger(new StatusHandler());
    Messenger mLoggerService;
    LoggerConnection mLoggerConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_panel);
        // Bind to logging service
        mLoggerConnection = new LoggerConnection();
        bindService(new Intent(this, LoggingService.class),
                mLoggerConnection,
                Service.BIND_AUTO_CREATE);
        mInternalHandler = new InternalHandler();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mInternalHandler != null) mInternalHandler.removeMessages(LoggingService.GET_COUNT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mInternalHandler != null) mInternalHandler.sendEmptyMessage(LoggingService.GET_COUNT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mLoggerConnection);
    }

    public void startLogging(View view) {
        if (mLoggerService == null) return;
        try {
            Message request = Message.obtain(null, LoggingService.START_LOGGING);
            mLoggerService.send(request);
        } catch (RemoteException e) {
            Log.w(LoggingService.TAG, "Failed to send message to logging service.");
        }
    }

    public void stopLogging(View view) {
        if (mLoggerService == null) return;
        try {
            Message request = Message.obtain(null, LoggingService.STOP_LOGGING);
            mLoggerService.send(request);
        } catch (RemoteException e) {
            Log.w(LoggingService.TAG, "Failed to send message to logging service.");
        }
    }

    public void saveLog(View view) {
        //TODO
    }

    private class StatusHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LoggingService.GET_COUNT:
                    TextView count = (TextView) findViewById(R.id.text_count);
                    if (count != null) count.setText(Integer.toString(msg.arg1));
                    break;
            }
        }
    }

    private class InternalHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LoggingService.GET_COUNT:
                    if (mLoggerService == null) break;
                    try {
                        Message request = Message.obtain(null, LoggingService.GET_COUNT);
                        request.replyTo = mMessenger;
                        mLoggerService.send(request);
                        mInternalHandler.sendEmptyMessageDelayed(LoggingService.GET_COUNT,
                                SERVICE_POLLING_RATE);
                    } catch (RemoteException e) {
                        Log.w(LoggingService.TAG, "Failed to send message to logging service.");
                    }
                    break;
            }
        }
    }

    private class LoggerConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mLoggerService = new Messenger(iBinder);
            // Periodically get stats from logging service
            mInternalHandler.sendEmptyMessageDelayed(LoggingService.GET_COUNT, SERVICE_POLLING_RATE);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mLoggerService = null;
        }
    }
}
