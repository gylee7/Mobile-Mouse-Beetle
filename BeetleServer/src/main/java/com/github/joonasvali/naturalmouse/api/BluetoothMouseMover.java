package com.github.joonasvali.naturalmouse.api;

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import javax.bluetooth.LocalDevice;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

public class BluetoothMouseMover extends JFrame {
    private static final int FPS = 30;
    private static final int DELAY = 1000 / FPS;

    private static double currentX;
    private static double currentY;
    private static double previousTX = 0;
    private static double previousTY = 0;

    private static MouseMotionFactory mouseMotionFactory;
    private static Dimension screenSize;

    public BluetoothMouseMover() {
        setTitle("Bluetooth Mouse Mover");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) { }

            @Override
            public void keyTyped(KeyEvent e) { }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BluetoothMouseMover mover = new BluetoothMouseMover();

            Point mousePosition = MouseInfo.getPointerInfo().getLocation();
            currentX = mousePosition.getX();
            currentY = mousePosition.getY();

            screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            mouseMotionFactory = MouseMotionFactory.getDefault();

            Runnable r = new BluetoothServerRunnable();
            Thread thread = new Thread(r);
            thread.start();
        });
    }

    private static void log(String msg) {
        System.out.println("[" + (new Date()) + "] " + msg);
    }

    static class BluetoothServerRunnable implements Runnable {
        final UUID uuid = new UUID("0000110100001000800000805F9B34FB", false);
        final String CONNECTION_URL_FOR_SPP = "btspp://localhost:" + uuid + ";name=MouseMover Server";

        private StreamConnectionNotifier mStreamConnectionNotifier = null;
        private StreamConnection mStreamConnection = null;

        @Override
        public void run() {
            try {
                LocalDevice.getLocalDevice().setDiscoverable(javax.bluetooth.DiscoveryAgent.GIAC);
                mStreamConnectionNotifier = (StreamConnectionNotifier) Connector.open(CONNECTION_URL_FOR_SPP);
                log("Bluetooth Server Started. Waiting for clients...");
            } catch (Exception e) {
                log("Error starting server: " + e.getMessage());
                return;
            }

            while (true) {
                try {
                    mStreamConnection = mStreamConnectionNotifier.acceptAndOpen();
                    log("Client Connected");
                    new ClientHandler(mStreamConnection).start();
                } catch (IOException e) {
                    log("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    static class ClientHandler extends Thread {
        private final StreamConnection mStreamConnection;
        private InputStream mInputStream;

        ClientHandler(StreamConnection connection) {
            this.mStreamConnection = connection;
        }

        @Override
        public void run() {
            try {
                mInputStream = mStreamConnection.openInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(mInputStream));
                String line;
                log("Ready to receive data..."); // 추가된 로그
                while ((line = reader.readLine()) != null) {
                    log("Received line: " + line); // 추가된 로그
                    
                    // 앞뒤 공백 제거 후, 공백을 기준으로 나누기
                    String[] parts = line.trim().split("\\s+"); // 정규 표현식 사용
                    
                    if (parts.length == 2) {
                        try {
                            double t_x = Double.parseDouble(parts[0]);
                            double t_y = Double.parseDouble(parts[1]);
                            updateMousePosition(t_x, t_y);
                        } catch (NumberFormatException e) {
                            log("Invalid number format: " + e.getMessage());
                        }
                    } else {
                        log("Invalid data format: " + line); // 데이터 포맷이 잘못되었을 경우 로그
                    }
                }
            } catch (IOException e) {
                log("Error handling client: " + e.getMessage());
            } finally {
                try {
                    if (mInputStream != null) mInputStream.close();
                    if (mStreamConnection != null) mStreamConnection.close();
                } catch (IOException e) {
                    log("Error closing connection: " + e.getMessage());
                }
            }
        }


        private void updateMousePosition(double t_x, double t_y) {
            SwingUtilities.invokeLater(() -> {
                if (previousTX != 0 && previousTY != 0) {
                    currentX += (t_x - previousTX) * 65000;
                    currentY += (t_y - previousTY) * 65000;

                    currentX = Math.min(Math.max(currentX, 0), screenSize.getWidth() - 1);
                    currentY = Math.min(Math.max(currentY, 0), screenSize.getHeight() - 1);

                    MouseMotion motion = mouseMotionFactory.build((int) currentX, (int) currentY);
                    try {
                        motion.move();
                    } catch (InterruptedException ex) {
                        log("Mouse movement interrupted: " + ex.getMessage());
                    }
                }

                previousTX = t_x;
                previousTY = t_y;
                log("Mouse moved to: " + (int) currentX + ", " + (int) currentY);
            });
        }

    }
}





