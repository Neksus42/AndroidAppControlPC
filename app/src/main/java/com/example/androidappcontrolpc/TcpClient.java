package com.example.androidappcontrolpc;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class TcpClient {
    private String serverAddress;
    private int serverPort;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    // Флаг для того, чтобы не запускать несколько мониторинговых потоков одновременно
    public volatile boolean monitoring = false;
    public volatile boolean IsSleep = false;
    public TcpClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    /**
     * Попытка установить соединение.
     * При успешном подключении вызывается activity.UpdateSpinner().
     * При неудаче ждёт 5 секунд и повторяет попытку.
     */
    public void connect(MainActivity activity) {
        final String TAG = TcpClient.class.getSimpleName();

        new Thread(() -> {

            Log.i(TAG, "Пытаемся подключиться к " + serverAddress + ":" + serverPort);
            try {
                socket = new Socket(serverAddress, serverPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Log.i(TAG, "Подключение успешно установлено");
                activity.UpdateSpinner();
                activity.UpdateVolume();

                if (!monitoring) {
                    Log.d(TAG, "Запускаем мониторинг соединения…");
                    startConnectionMonitor(activity);
                }
            } catch (IOException e) {
                Log.e(TAG, "Не удалось подключиться: " + e.getMessage(), e);

                e.printStackTrace();
                try {
                    Log.i(TAG, "Ждём 5с и пробуем ещё раз");
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Log.e(TAG, "Пауза прервана", ie);
                    ie.printStackTrace();
                }
                connect(activity);
            }
        }).start();
    }


    public void startConnectionMonitor(MainActivity activity) {
        monitoring = true;
        new Thread(() -> {
while (true)
            {
                final String TAG = "ReconnectWorker";
                while (IsSleep) {
                    try {
                        Thread.sleep(5000);             // проверяем каждые 5 с
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Thread interrupted", e);
                    }

                    if (!ServerChecker.isServerReachable(serverAddress, serverPort, 5000)) {
                        Log.w(TAG, "Соединение потеряно. Пробуем переподключиться…");
                        connect(activity);
                        break;
                    }

                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }).start();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }


    public void SendMessage(String Message) {
        new Thread(() -> {
            if (!isConnected()) {
                System.out.println("Нет соединения. Сообщение не отправлено: " + Message);
                return;
            }
            if (out != null) {
                out.println(Message);
            }
        }).start();
    }


    public String GetMessage() {
        Callable<String> readTask = () -> {
            try {
                return in.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        };

        FutureTask<String> futureTask = new FutureTask<>(readTask);
        new Thread(futureTask).start();

        try {
            return futureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }
}
