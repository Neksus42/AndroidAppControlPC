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
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button ButtonToTurnOFF_PC;
    private Button ButtonToSwitchDisplay_PC;
    private Button ButtonToSwitchDisplay_TV;

    private Spinner spinnerAudioDevices;

    // --- Новое: элементы для громкости ---
    private SeekBar volumeSeek;
    private TextView volumeLabel;
    private boolean updatingFromServer = false; // чтобы не зациклить обратную отправку
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable throttleSend;

    private static final String SERVER_IP = "192.168.0.200";
    private static final int SERVER_PORT = 8888;
    private boolean spinnerUpdated = true;

    private TcpClient client;

    // Поток единственного чтения входящих строк
    private Thread receiverThread;
    private volatile boolean receiverRunning = false;

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

        // --- Сеть ---
        client = new TcpClient(SERVER_IP, SERVER_PORT);
        client.connect(this);           // твой метод коннекта (оставляем как есть)
        startReceiverThread();          // стартуем ЕДИНСТВЕННЫЙ поток чтения

        // --- Кнопки/спиннер ---
        ButtonToTurnOFF_PC = findViewById(R.id.button);
        ButtonToSwitchDisplay_PC = findViewById(R.id.button4);
        ButtonToSwitchDisplay_TV = findViewById(R.id.button5);
        spinnerAudioDevices = findViewById(R.id.spinnerAudioDevices);

        ButtonToSwitchDisplay_TV.setOnClickListener(v -> new Thread(() -> {
            try {
                client.SendMessage("SwapDisplayTV");
                // при необходимости: client.SendMessage("SwitchAudioDevice:3");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start());

        ButtonToSwitchDisplay_PC.setOnClickListener(v -> client.SendMessage("SwapDisplayPC"));
        ButtonToTurnOFF_PC.setOnClickListener(v -> client.SendMessage("ShutdownPC"));

        spinnerAudioDevices.setOnTouchListener((View v, MotionEvent event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                UpdateSpinner(); // ТЕПЕРЬ только шлёт запрос; ответ ловится в receiverThread
            }
            return false;
        });

        spinnerAudioDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnerUpdated) {
                    spinnerUpdated = false;
                } else {
                    client.SendMessage("SwitchAudioDevice:" + (position - 1));
                    UpdateVolume();

                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        // --- ГРОМКОСТЬ: UI и логика ---
        volumeSeek = findViewById(R.id.volumeSeek);
        volumeLabel = findViewById(R.id.volumeLabel);
        volumeSeek.setMax(100);

        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeLabel.setText("Volume: " + progress + "%");
                if (fromUser && !updatingFromServer) {
                    if (throttleSend != null) ui.removeCallbacks(throttleSend);
                    throttleSend = () -> {
                        float v01 = progress / 100f;
                        client.SendMessage("SetVolume:" + String.format(Locale.US, "%.3f", v01));
                    };
                    ui.postDelayed(throttleSend, 10); // троттлинг ~20 сообщений/сек
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });


    }

    // ==== ЕДИНСТВЕННЫЙ поток чтения входящих линий ====
    private void startReceiverThread() {
        stopReceiverThread(); // на всякий случай
        receiverRunning = true;
        receiverThread = new Thread(() -> {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<String>>() {}.getType();

            while (receiverRunning) {
                try {
                    String line = client.GetMessage();  // ТВОЙ метод чтения одной строки
                    if (line == null || line.isEmpty()) {
                        // маленькая пауза, чтобы не крутить CPU в случае null
                        Thread.sleep(10);
                        continue;
                    }
                    line = line.trim();

                    // 1) Синхронизация громкости: "VolumeSync:x"
                    if (line.startsWith("VolumeSync:")) {
                        try {
                            float v01 = Float.parseFloat(line.substring("VolumeSync:".length()).trim());
                            int percent = Math.max(0, Math.min(100, Math.round(v01 * 100f)));
                            runOnUiThread(() -> {
                                updatingFromServer = true;
                                volumeSeek.setProgress(percent);
                                volumeLabel.setText("Volume: " + percent + "%");
                                updatingFromServer = false;
                            });
                        } catch (Exception ignored) {}
                        continue;
                    }

                    // 2) Ответ на GetAudioDevices — JSON-массив (примерно: ["0: ...","1: ..."])
                    if (line.startsWith("[") && line.endsWith("]")) {
                        try {
                            List<String> devicesList = gson.fromJson(line, listType);
                            if (devicesList != null) {
                                // добавим в начало "Выберите устройство"
                                devicesList.add(0, "Выберите устройство");
                                runOnUiThread(() -> {
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
                        } catch (Exception ignored) {}
                        continue;
                    }

                    // 3) Прочие ответы — если есть другие команды
                    // if (line.startsWith("...")) { ... }

                } catch (Throwable t) {
                    // Не даём треду умереть из-за исключения
                    t.printStackTrace();
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
            }
        }, "tcp-receiver");
        receiverThread.start();
    }

    private void stopReceiverThread() {
        receiverRunning = false;
        if (receiverThread != null) {
            try { receiverThread.interrupt(); } catch (Exception ignored) {}
            receiverThread = null;
        }
    }

    // ==== ТЕПЕРЬ UpdateSpinner НЕ читает сокет! ====
    public void UpdateSpinner() {
        new Thread(() -> client.SendMessage("GetAudioDevices")).start();
    }
    public void UpdateVolume()
    {
        new Thread(() -> client.SendMessage("GetVolume")).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        client.IsSleep = true;
        client.connect(this);
        // поток чтения уже запущен в onCreate(); если хочешь — можно здесь проверять/перезапускать
    }

    @Override
    protected void onPause() {
        super.onPause();
        client.IsSleep = false;
        // по желанию можно останавливать приёмник:
        // stopReceiverThread();
    }
}
