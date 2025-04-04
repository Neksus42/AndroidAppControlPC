package com.example.androidappcontrolpc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ServerChecker {
    /**
     *
     * @param ip      IP-адрес сервера
     * @param port    порт сервера
     * @param timeout таймаут в миллисекундах
     * @return true, если соединение установлено, false – если не удалось
     */
    public static boolean isServerReachable(String ip, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
