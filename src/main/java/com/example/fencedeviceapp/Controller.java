package com.example.fencedeviceapp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.control.TabPane;

import java.io.*;
import java.net.*;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Controller {

    // Common fields
    @FXML
    private TextField ipField;

    // HTTP tab fields
    @FXML
    private TextArea httpresponseArea;
    @FXML
    private VBox httpstatusContainer;
    @FXML
    private VBox httpcontrolsContainer;

    // TCP tab fields
    @FXML
    private TextArea responseArea1;
    @FXML
    private VBox statusContainer1;
    @FXML
    private VBox controlsContainer1;

    // State tracking
    private Map<String, Boolean> httpdeviceState = new HashMap<>();
    private Map<String, Boolean> tcpdeviceState = new HashMap<>();

    // For periodic status updates
    private ScheduledExecutorService httpScheduler;
    private ScheduledExecutorService tcpScheduler;
    private String currentIp = "";
    private boolean isHttpConnected = false;
    private boolean isTcpConnected = false;

    // TCP connection settings
    private static final int TCP_PORT = 1515; // Default Telnet port, adjust as needed
    private static final int TCP_TIMEOUT = 10000; // 5 seconds

    @FXML
    public void initialize() {
        // Initialize UI components if needed
    }

    @FXML
    public void handleConnect() {
        String ip = ipField.getText().trim();
        if (ip.isEmpty()) {
            httpresponseArea.setText("Please enter an IP address.");
            responseArea1.setText("Please enter an IP address.");
            return;
        }

        // Stop any existing refresh timers
        stopHttpRefreshTimer();
        stopTcpRefreshTimer();

        // Connect to both protocols
        connectHttp(ip);
        connectTcp(ip);
    }

    // HTTP CONNECTION HANDLING
    private void connectHttp(String ip) {
        String urlString = "http://" + ip + "/status.xml";
        httpresponseArea.setText("Connecting to " + urlString + "...");

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            StringBuilder result = new StringBuilder("HTTP Status Code: " + status + "\n");

            if (status == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder xmlContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    xmlContent.append(line).append("\n");
                    result.append(line).append("\n");
                }
                reader.close();

                // Update the status indicators based on the XML response
                try {
                    updateHttpStatusFromResponse(xmlContent.toString());
                    // Create control buttons after status is updated
                    createHttpControlButtons(ip);
                    httpresponseArea.setText(result.toString());

                    // Set connection status and start refresh timer
                    currentIp = ip;
                    isHttpConnected = true;
                    startHttpRefreshTimer();

                } catch (Exception e) {
                    httpresponseArea.setText(result + "\n\nError parsing XML: " + e.getMessage());
                }
            } else {
                httpresponseArea.setText(result + "\nFailed to connect. Check IP address and try again.");
            }

            conn.disconnect();

        } catch (Exception e) {
            httpresponseArea.setText("HTTP Connection Error: " + e.getMessage());
        }
    }

    public void updateHttpStatusFromResponse(String xmlResponse) throws Exception {
        List<StatusIndicator> indicators = parseHttpXml(xmlResponse);
        httpstatusContainer.getChildren().clear();

        // Set padding for the entire status container
        httpstatusContainer.setStyle("-fx-padding: 10px; -fx-background-color: #f5f5f5; -fx-background-radius: 5;");

        for (StatusIndicator indicator : indicators) {
            Label label = new Label(indicator.getLabel());
            Label value = new Label(indicator.isStatus() ? "Yes" : "No");

            // Style for the label
            label.setStyle("-fx-font-weight: bold;");
            label.setMinWidth(150);

            // Style for the value label
            value.setStyle("-fx-background-color: " + (indicator.isStatus() ? "#ffcccc" : "#ccffe0") + ";" +
                    "-fx-text-fill: black; -fx-padding: 4px 10px; -fx-background-radius: 12;");

            // Create row with padding and styling
            HBox row = new HBox(10, label, value);
            row.setStyle("-fx-padding: 8px; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
            row.setPrefHeight(40); // Consistent height for each row

            httpstatusContainer.getChildren().add(row);
        }
    }

    public List<StatusIndicator> parseHttpXml(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes()));

        // Store device state for controls
        httpdeviceState.put("IntrusionAlarm", getHttpValue(doc, "IntrusionAlarm"));
        httpdeviceState.put("DrainageIntrusionAlarm", getHttpValue(doc, "DrainageIntrusionAlarm"));
        httpdeviceState.put("EncloserAlarm", getHttpValue(doc, "EncloserAlarm"));
        httpdeviceState.put("LidAlarm", getHttpValue(doc, "LidAlarm"));
        httpdeviceState.put("ServiceMode", getHttpValue(doc, "ServiceMode"));
        httpdeviceState.put("FenceStaus", getHttpValue(doc, "FenceStaus"));
        httpdeviceState.put("LightStaus", getHttpValue(doc, "LightStaus"));

        return List.of(
                new StatusIndicator("Intrusion", getHttpValue(doc, "IntrusionAlarm")),
                new StatusIndicator("Drain Intrusion", getHttpValue(doc, "DrainageIntrusionAlarm")),
                new StatusIndicator("Enclosure Open", getHttpValue(doc, "EncloserAlarm")),
                new StatusIndicator("Lid Open", getHttpValue(doc, "LidAlarm")),
                new StatusIndicator("System Check", getHttpValue(doc, "ServiceMode"))
        );
    }

    private boolean getHttpValue(Document doc, String tagName) {
        Node node = doc.getElementsByTagName(tagName).item(0);
        return node != null && node.getTextContent().trim().equals("1");
    }

    public void createHttpControlButtons(String ip) {
        // Clear existing controls
        httpcontrolsContainer.getChildren().clear();

        // Add label for the controls section
        Label controlsLabel = new Label("HTTP Status Controls");
        controlsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        httpcontrolsContainer.getChildren().add(controlsLabel);

        // Create control rows with toggle buttons
        createHttpControlRow("Fence System", "1", ip, httpdeviceState.getOrDefault("FenceStaus", false));
        createHttpControlRow("Gadget System", "2", ip, httpdeviceState.getOrDefault("LightStaus", false));
        createHttpControlRow("Service Mode", "3", ip, httpdeviceState.getOrDefault("ServiceMode", false));
    }

    private void createHttpControlRow(String label, String key, String ip, boolean initialState) {
        HBox row = new HBox(10);
        row.setStyle("-fx-padding: 10; -fx-background-color: #f0f8ff; -fx-background-radius: 5;");
        row.setPrefHeight(40);

        // Label
        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-font-weight: bold;");
        nameLabel.setMinWidth(150);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        // Toggle button
        ToggleButton toggleButton = new ToggleButton(initialState ? "ON" : "OFF");
        toggleButton.setSelected(initialState);
        toggleButton.setStyle(getToggleButtonStyle(initialState));

        // Set up the action for the toggle button
        toggleButton.setOnAction(event -> {
            boolean isOn = toggleButton.isSelected();
            toggleButton.setText(isOn ? "ON" : "OFF");
            toggleButton.setStyle(getToggleButtonStyle(isOn));

            // Send HTTP request to toggle the state
            sendHttpControlCommand(ip, key, isOn);
        });

        row.getChildren().addAll(nameLabel, toggleButton);
        httpcontrolsContainer.getChildren().add(row);
    }

    private String getToggleButtonStyle(boolean isOn) {
        return isOn
                ? "-fx-background-color: #3366cc; -fx-text-fill: white; -fx-background-radius: 15;"
                : "-fx-background-color: #cccccc; -fx-text-fill: black; -fx-background-radius: 15;";
    }

    private void sendHttpControlCommand(String ip, String key, boolean isOn) {
        // Construct the URL with parameters
        String urlString = "http://" + ip + "/leds.cgi?led=" + key;

        // Add to response area
        httpresponseArea.appendText("\n\nSending command: " + urlString);

        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                StringBuilder responseText = new StringBuilder();
                responseText.append("\nCommand Response: HTTP Status Code ").append(responseCode);

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseText.append("\n").append(line);
                    }
                    reader.close();

                    // Refresh the status after changing a setting - immediate refresh
                    refreshHttpDeviceStatus(ip, true);
                }

                // Update UI on the JavaFX thread
                Platform.runLater(() -> {
                    httpresponseArea.appendText(responseText.toString());
                });

                conn.disconnect();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    httpresponseArea.appendText("\nError sending command: " + e.getMessage());
                });
            }
        }).start();
    }

    // Start periodic refresh of device status
    private void startHttpRefreshTimer() {
        httpScheduler = Executors.newSingleThreadScheduledExecutor();
        httpScheduler.scheduleAtFixedRate(() -> {
            if (isHttpConnected) {
                refreshHttpDeviceStatus(currentIp, false); // false = don't append to response area
            }
        }, 3, 3, TimeUnit.SECONDS); // Update every 3 seconds
    }

    // Stop the refresh timer
    private void stopHttpRefreshTimer() {
        if (httpScheduler != null && !httpScheduler.isShutdown()) {
            httpScheduler.shutdownNow();
            httpScheduler = null;
        }
    }

    private void refreshHttpDeviceStatus(String ip) {
        refreshHttpDeviceStatus(ip, true); // Default to showing in response area
    }

    private void refreshHttpDeviceStatus(String ip, boolean showInResponseArea) {
        // Wait briefly before refreshing to allow the device to update
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Fetch the updated status
        try {
            URL url = new URL("http://" + ip + "/status.xml");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            int status = conn.getResponseCode();
            final StringBuilder responseText = new StringBuilder();

            if (status == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder xmlContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    xmlContent.append(line).append("\n");
                    if (showInResponseArea) {
                        responseText.append(line).append("\n");
                    }
                }
                reader.close();

                final String xmlData = xmlContent.toString();

                // Update UI with new status on JavaFX thread
                Platform.runLater(() -> {
                    try {
                        // Update status display
                        updateHttpStatusFromResponse(xmlData);
                        createHttpControlButtons(ip);

                        // Add to response area if requested
                        if (showInResponseArea) {
                            httpresponseArea.appendText("\n\n--- Status Refresh ---\n" + responseText);
                        }
                    } catch (Exception e) {
                        if (showInResponseArea) {
                            httpresponseArea.appendText("\nError updating status: " + e.getMessage());
                        }
                    }
                });
            } else if (showInResponseArea) {
                final int statusCode = status;
                Platform.runLater(() -> {
                    httpresponseArea.appendText("\nRefresh failed with status code: " + statusCode);
                });
            }

            conn.disconnect();
        } catch (Exception e) {
            if (showInResponseArea) {
                final String errorMsg = e.getMessage();
                Platform.runLater(() -> {
                    httpresponseArea.appendText("\nError refreshing status: " + errorMsg);
                });
            }

            // If we can't connect, stop trying after several failures
            if (e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException) {
                // Connection lost - could implement a retry counter here
            }
        }
    }














        // TCP CONNECTION HANDLING
        public void connectTcp(String ip) {
            responseArea1.setText("Connecting to " + ip + " via TCP...");

            // Match the Node.js port and timeout
            final int TCP_PORT = 1515;
            final int TCP_TIMEOUT = 5000; // Increased to 5000ms (5 seconds) like Node.js

            new Thread(() -> {
                try {
                    // Create socket without connecting first
                    Socket socket = new Socket();

                    // Set timeout before connection
                    socket.setSoTimeout(TCP_TIMEOUT);

                    // Log connection attempt
                    System.out.println("Attempting to connect to " + ip + ":" + TCP_PORT);

                    // Connect with timeout
                    socket.connect(new InetSocketAddress(ip, TCP_PORT), TCP_TIMEOUT);

                    System.out.println("Connected to " + ip + ":" + TCP_PORT);

                    // Convert hex string to byte array - same as Node.js
                    String statusCommandHex = "474554414C4C535441545553009D4353";
                    byte[] statusCommandBytes = hexStringToByteArray(statusCommandHex);

                    // Get streams
                    OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream();

                    // Send request - log this step
                    System.out.println("Sending request to " + ip);
                    out.write(statusCommandBytes);
                    out.flush();
                    System.out.println("Request sent to " + ip);

                    // Read response directly as bytes like Node.js does
                    // Create a buffer to store incoming data
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    // Wait for response with timeout protection
                    long startTime = System.currentTimeMillis();
                    ByteArrayOutputStream responseData = new ByteArrayOutputStream();

                    // Instead of using readLine(), read raw bytes like Node.js
                    while (System.currentTimeMillis() - startTime < TCP_TIMEOUT) {
                        if (in.available() > 0) {
                            bytesRead = in.read(buffer);
                            if (bytesRead == -1) break;
                            responseData.write(buffer, 0, bytesRead);
                            break; // Important - only read once like Node.js
                        }
                        Thread.sleep(50); // Small delay to prevent CPU thrashing
                    }

                    final byte[] response = responseData.toByteArray();
                    System.out.println("Received " + response.length + " bytes from " + ip);

                    // Close the socket
                    socket.close();

                    // Process response on the JavaFX thread
                    if (response.length > 0) {
                        Platform.runLater(() -> {
                            try {
                                // Convert the binary response to string for display
                                String responseStr = new String(response, StandardCharsets.UTF_8);

                                updateTcpStatusFromResponse(responseStr);
                                createTcpControlButtons(ip);
                                responseArea1.setText("TCP Connection Successful\n\n" + responseStr);
                                currentIp = ip;
                                isTcpConnected = true;
                                startTcpRefreshTimer();
                            } catch (Exception e) {
                                System.err.println("Error processing response: " + e.getMessage());
                                responseArea1.setText("TCP Connected but error parsing response: " + e.getMessage());
                            }
                        });
                    } else {
                        Platform.runLater(() ->
                                responseArea1.setText("TCP Connection succeeded but received empty response")
                        );
                    }
                } catch (SocketTimeoutException ste) {
                    System.err.println("TCP connection timeout: " + ste.getMessage());
                    Platform.runLater(() ->
                            responseArea1.setText("TCP Connection Timeout: Device did not respond within " +
                                    (TCP_TIMEOUT/1000) + " seconds")
                    );
                } catch (ConnectException ce) {
                    System.err.println("TCP connection refused: " + ce.getMessage());
                    Platform.runLater(() ->
                            responseArea1.setText("TCP Connection Refused: Device actively refused connection")
                    );
                } catch (Exception e) {
                    System.err.println("TCP error: " + e.getMessage());
                    final String errorMsg = e.getMessage();
                    Platform.runLater(() ->
                            responseArea1.setText("TCP Connection Error: " + errorMsg)
                    );
                }
            }).start();
        }
    // Helper method to convert hex string to byte array
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }


    private void updateTcpStatusFromResponse(String tcpResponse) {
            Map<String, Boolean> statusMap = parseTcpResponse(tcpResponse);

            statusContainer1.getChildren().clear();
            statusContainer1.setStyle("-fx-padding: 10px; -fx-background-color: #f5f5f5; -fx-background-radius: 5;");

            for (Map.Entry<String, Boolean> entry : statusMap.entrySet()) {
                Label label = new Label(entry.getKey());
                Label value = new Label(entry.getValue() ? "Yes" : "No");

                label.setStyle("-fx-font-weight: bold;");
                label.setMinWidth(150);

                value.setStyle("-fx-background-color: " + (entry.getValue() ? "#ffcccc" : "#ccffe0") + ";" +
                        "-fx-text-fill: black; -fx-padding: 4px 10px; -fx-background-radius: 12;");

                HBox row = new HBox(10, label, value);
                row.setStyle("-fx-padding: 8px; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
                row.setPrefHeight(40);

                statusContainer1.getChildren().add(row);
            }
        }

        private Map<String, Boolean> parseTcpResponse(String response) {
            Map<String, Boolean> result = new HashMap<>();
            tcpdeviceState.clear();

            if (response.contains("INTRUSION=")) {
                boolean state = response.contains("INTRUSION=1");
                result.put("Intrusion", state);
                tcpdeviceState.put("Intrusion", state);
            }

            if (response.contains("DRAINAGE=")) {
                boolean state = response.contains("DRAINAGE=1");
                result.put("Drain Intrusion", state);
                tcpdeviceState.put("Drainage", state);
            }

            if (response.contains("ENCLOSURE=")) {
                boolean state = response.contains("ENCLOSURE=1");
                result.put("Enclosure Open", state);
                tcpdeviceState.put("Enclosure", state);
            }

            if (response.contains("LID=")) {
                boolean state = response.contains("LID=1");
                result.put("Lid Open", state);
                tcpdeviceState.put("Lid", state);
            }

            if (response.contains("SERVICE=")) {
                boolean state = response.contains("SERVICE=1");
                result.put("System Check", state);
                tcpdeviceState.put("Service", state);
            }

            if (response.contains("FENCE=")) {
                boolean state = response.contains("FENCE=1");
                tcpdeviceState.put("Fence", state);
            }

            if (response.contains("LIGHT=")) {
                boolean state = response.contains("LIGHT=1");
                tcpdeviceState.put("Light", state);
            }

            // Fallback if response doesn't include expected fields
            if (result.isEmpty()) {
                result.put("Intrusion", false);
                result.put("Drain Intrusion", false);
                result.put("Enclosure Open", false);
                result.put("Lid Open", false);
                result.put("System Check", true);

                tcpdeviceState.put("Fence", true);
                tcpdeviceState.put("Light", false);
                tcpdeviceState.put("Service", true);
            }

            return result;
        }

        private void createTcpControlButtons(String ip) {
            controlsContainer1.getChildren().clear();

            Label controlsLabel = new Label("TCP Status Controls");
            controlsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
            controlsContainer1.getChildren().add(controlsLabel);

            createTcpControlRow("Fence System", "FENCE", ip, tcpdeviceState.getOrDefault("Fence", false));
            createTcpControlRow("Light System", "LIGHT", ip, tcpdeviceState.getOrDefault("Light", false));
            createTcpControlRow("Service Mode", "SERVICE", ip, tcpdeviceState.getOrDefault("Service", false));
        }

        private void createTcpControlRow(String label, String command, String ip, boolean initialState) {
            HBox row = new HBox(10);
            row.setStyle("-fx-padding: 10; -fx-background-color: #f0f8ff; -fx-background-radius: 5;");
            row.setPrefHeight(40);

            Label nameLabel = new Label(label);
            nameLabel.setStyle("-fx-font-weight: bold;");
            nameLabel.setMinWidth(150);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            ToggleButton toggleButton = new ToggleButton(initialState ? "ON" : "OFF");
            toggleButton.setSelected(initialState);
            toggleButton.setStyle(getToggleButtonStyle(initialState));

            toggleButton.setOnAction(event -> {
                boolean isOn = toggleButton.isSelected();
                toggleButton.setText(isOn ? "ON" : "OFF");
                toggleButton.setStyle(getToggleButtonStyle(isOn));
                sendTcpControlCommand(ip, command, isOn);
            });

            row.getChildren().addAll(nameLabel, toggleButton);
            controlsContainer1.getChildren().add(row);
        }

        private void sendTcpControlCommand(String ip, String command, boolean isOn) {
            String fullCommand = "SET_" + command + "=" + (isOn ? "1" : "0") + "\r\n";
            responseArea1.appendText("\n\nSending TCP command: " + fullCommand);

            new Thread(() -> {
                try (Socket socket = new Socket(ip, TCP_PORT)) {
                    socket.setSoTimeout(TCP_TIMEOUT);

                    OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream());
                    writer.write(fullCommand);
                    writer.flush();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        response.append(line).append("\n");
                    }

                    final String tcpResponse = response.toString();

                    Platform.runLater(() -> {
                        responseArea1.appendText("\nCommand Response:\n" + tcpResponse);
                        refreshTcpDeviceStatus(ip, true);
                    });

                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    Platform.runLater(() -> responseArea1.appendText("\nError sending TCP command: " + errorMsg));
                }
            }).start();
        }

        private void startTcpRefreshTimer() {
            tcpScheduler = Executors.newSingleThreadScheduledExecutor();
            tcpScheduler.scheduleAtFixedRate(() -> {
                if (isTcpConnected) {
                    refreshTcpDeviceStatus(currentIp, false);
                }
            }, 3, 3, TimeUnit.SECONDS);
        }

        private void stopTcpRefreshTimer() {
            if (tcpScheduler != null && !tcpScheduler.isShutdown()) {
                tcpScheduler.shutdownNow();
                tcpScheduler = null;
            }
        }

        private void refreshTcpDeviceStatus(String ip, boolean showInResponseArea) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            new Thread(() -> {
                try (Socket socket = new Socket(ip, TCP_PORT)) {
                    socket.setSoTimeout(TCP_TIMEOUT);

                    String statusCommand = "GET_STATUS\r\n";
                    OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream());
                    writer.write(statusCommand);
                    writer.flush();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        response.append(line).append("\n");
                    }

                    final String tcpResponse = response.toString();

                    Platform.runLater(() -> {
                        try {
                            updateTcpStatusFromResponse(tcpResponse);
                            createTcpControlButtons(ip);
                            if (showInResponseArea) {
                                responseArea1.appendText("\n\n--- TCP Status Refresh ---\n" + tcpResponse);
                            }
                        } catch (Exception e) {
                            if (showInResponseArea) {
                                responseArea1.appendText("\nError updating TCP status: " + e.getMessage());
                            }
                        }
                    });

                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    Platform.runLater(() -> {
                        if (showInResponseArea) {
                            responseArea1.appendText("\nError refreshing TCP status: " + errorMsg);
                        }
                    });
                }
            }).start();
        }









    // Clean up resources when application closes
    public void shutdown() {
        stopHttpRefreshTimer();
        stopTcpRefreshTimer();
    }
}