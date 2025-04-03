package com.example.dlna;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private DLNAService dlnaService;
    private boolean bound = false;
    private TextView statusText;
    private Button startButton;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DLNAService.LocalBinder binder = (DLNAService.LocalBinder) service;
            dlnaService = binder.getService();
            bound = true;
            updateUI();
//            Toast.makeText(MainActivity.this, "DLNA服务已启动", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            updateUI();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        startButton = findViewById(R.id.start_button);
        
        startButton.setOnClickListener(v -> {
            if (!bound) {
                startDLNAService();
            } else {
                stopDLNAService();
            }
        });
    }

    private void startDLNAService() {
        Intent intent = new Intent(this, DLNAService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void stopDLNAService() {
        if (bound) {
            unbindService(connection);
            bound = false;
            updateUI();
//            Toast.makeText(this, "DLNA服务已停止", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI() {
        if (bound) {
            statusText.setText("DLNA服务状态：运行中");
            startButton.setText("停止服务");
        } else {
            statusText.setText("DLNA服务状态：已停止");
            startButton.setText("启动服务");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }
} 