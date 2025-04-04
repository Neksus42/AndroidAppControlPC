package com.example.androidappcontrolpc;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private Button ButtonToTurnOFF_PC;
    private Button ButtonToSwitchDisplay_PC;
    private Button ButtonToSwitchDisplay_TV;

    private Spinner spinnerAudioDevices;
    private static final String SERVER_IP = "192.168.0.200";
    private static final int SERVER_PORT = 8888;
    private boolean spinnerUpdated = true;
    private TcpClient client;
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        client = new TcpClient(SERVER_IP, SERVER_PORT);
        client.connect(this);

        ButtonToTurnOFF_PC = findViewById(R.id.button);
        ButtonToSwitchDisplay_PC = findViewById(R.id.button4);
        ButtonToSwitchDisplay_TV = findViewById(R.id.button5);

        spinnerAudioDevices = findViewById(R.id.spinnerAudioDevices);



        ButtonToSwitchDisplay_TV.setOnClickListener(v -> {
            new Thread(() -> {

                try {
                    client.SendMessage("SwapDisplayTV");
                    Thread.sleep(1000);
                    client.SendMessage("SwitchAudioDevice:2");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        });
        ButtonToSwitchDisplay_PC.setOnClickListener(v -> {
            client.SendMessage("SwapDisplayPC");
        });
        ButtonToTurnOFF_PC.setOnClickListener(v -> {
            client.SendMessage("ShutdownPC");
        });

        spinnerAudioDevices.setOnTouchListener((View v, MotionEvent event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                UpdateSpinner();
            }
            return false;
        });

        spinnerAudioDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if (spinnerUpdated) {
                    spinnerUpdated = false;
                } else {

                    client.SendMessage("SwitchAudioDevice:" + (position-1));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }
    public void UpdateSpinner()
    {
        new Thread(() -> {

            client.SendMessage("GetAudioDevices");

            String jsonResponse = client.GetMessage();

            if (jsonResponse != null && !jsonResponse.isEmpty()) {

                Gson gson = new Gson();
                Type listType = new TypeToken<List<String>>() {}.getType();
                List<String> devicesList = gson.fromJson(jsonResponse, listType);
                devicesList.add(0, "Выберите устройство");

                new Handler(Looper.getMainLooper()).post(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            MainActivity.this,
                            android.R.layout.simple_spinner_item,
                            devicesList
                    );
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerAudioDevices.setAdapter(adapter);

                    spinnerUpdated = true;
                });
            }
        }).start();
    }
    @Override
    protected void onResume() {
        super.onResume();

        client.IsSleep = true;
        client.connect(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        client.IsSleep = false;

    }
}
