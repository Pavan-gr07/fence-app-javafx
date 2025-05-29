package com.example.fencedeviceapp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.io.*;
import java.net.*;

import javax.xml.parsers.DocumentBuilder;
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




    // HTTP CONNECTION HANDLING
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

        // Reset connection status
        isHttpConnected = false;
        isTcpConnected = false;

        // Connect to both protocols
        connectHttp(ip);
        connectTcp(ip);
    }

    private void connectHttp(String ip) {
        final String urlString = "http://" + ip + "/status.xml";
        httpresponseArea.setText("Connecting to " + urlString + "...");

        // Define constants
        final int HTTP_TIMEOUT = 5000; // 5 seconds timeout

        new Thread(() -> {
            try {
                System.out.println("Attempting HTTP connection to " + urlString);

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(HTTP_TIMEOUT);
                conn.setReadTimeout(HTTP_TIMEOUT);

                int status = conn.getResponseCode();
                System.out.println("HTTP Status Code: " + status);

                if (status == 200) {
                    // Read the XML response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder xmlContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        xmlContent.append(line).append("\n");
                    }
                    reader.close();

                    String xmlResponse = xmlContent.toString();
                    System.out.println("Received XML response of length: " + xmlResponse.length());

                    // Process response on the JavaFX thread
                    Platform.runLater(() -> {
                        try {
                            // Parse and update the status display
                            Map<String, Boolean> statusMap = parseHttpXmlToMap(xmlResponse);
                            updateHttpStatusDisplay(statusMap);

                            // Create control buttons
                            createHttpControlButtons(ip);

                            // Set connection status and start refresh timer
                            httpresponseArea.setText("HTTP Connection Successful\n\n" + xmlResponse);
                            currentIp = ip;
                            isHttpConnected = true;
                            startHttpRefreshTimer();
                        } catch (Exception e) {
                            System.err.println("Error processing HTTP response: " + e.getMessage());
                            httpresponseArea.setText("HTTP Connected but error parsing XML: " + e.getMessage());
                        }
                    });
                } else {
                    System.err.println("HTTP connection failed with status: " + status);
                    Platform.runLater(() ->
                            httpresponseArea.setText("HTTP Connection Failed: Server returned status code " + status)
                    );
                }

                conn.disconnect();
            } catch (java.net.SocketTimeoutException ste) {
                System.err.println("HTTP connection timeout: " + ste.getMessage());
                Platform.runLater(() ->
                        httpresponseArea.setText("HTTP Connection Timeout: Server did not respond within " +
                                (HTTP_TIMEOUT/1000) + " seconds")
                );
            } catch (java.net.ConnectException ce) {
                System.err.println("HTTP connection refused: " + ce.getMessage());
                Platform.runLater(() ->
                        httpresponseArea.setText("HTTP Connection Refused: Server actively refused connection")
                );
            } catch (Exception e) {
                System.err.println("HTTP error: " + e.getMessage());
                final String errorMsg = e.getMessage();
                Platform.runLater(() ->
                        httpresponseArea.setText("HTTP Connection Error: " + errorMsg)
                );
            }
        }).start();
    }

    // Parse XML response and create a map of status values
    private Map<String, Boolean> parseHttpXmlToMap(String xml) throws Exception {
        Map<String, Boolean> statusMap = new HashMap<>();

        // Parse the XML document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

        // Extract status values and store in both the status map and device state map
        httpdeviceState.clear();

        // Define all the status elements to extract
        String[] statusElements = {
                "IntrusionAlarm", "DrainageIntrusionAlarm", "EncloserAlarm",
                "LidAlarm", "ServiceMode", "FenceStaus", "LightStaus"
        };

        // Process each status element
        for (String element : statusElements) {
            boolean value = getHttpValue(doc, element);
            httpdeviceState.put(element, value);
        }

        // Create a map for display with friendly names
        statusMap.put("Intrusion", httpdeviceState.getOrDefault("IntrusionAlarm", false));
        statusMap.put("Drain Intrusion", httpdeviceState.getOrDefault("DrainageIntrusionAlarm", false));
        statusMap.put("Enclosure Open", httpdeviceState.getOrDefault("EncloserAlarm", false));
        statusMap.put("Lid Open", httpdeviceState.getOrDefault("LidAlarm", false));
        statusMap.put("System Check", httpdeviceState.getOrDefault("ServiceMode", false));

        return statusMap;
    }

    // Helper method to extract boolean value from XML element
    private boolean getHttpValue(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            return node != null && node.getTextContent().trim().equals("1");
        }
        return false;
    }

    // Update the status display with the parsed values
    private void updateHttpStatusDisplay(Map<String, Boolean> statusMap) {
        httpstatusContainer.getChildren().clear();
        httpstatusContainer.setStyle("-fx-padding: 10px; -fx-background-color: #f5f5f5; -fx-background-radius: 5;");

        for (Map.Entry<String, Boolean> entry : statusMap.entrySet()) {
            Label label = new Label(entry.getKey());
            Label value = new Label(entry.getValue() ? "Yes" : "No");

            label.setStyle("-fx-font-weight: bold;");
            label.setMinWidth(150);

            // Use consistent color scheme with TCP handler
            value.setStyle("-fx-background-color: " + (entry.getValue() ? "#ffcccc" : "#ccffe0") + ";" +
                    "-fx-text-fill: black; -fx-padding: 4px 10px; -fx-background-radius: 12;");

            HBox row = new HBox(10, label, value);
            row.setStyle("-fx-padding: 8px; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
            row.setPrefHeight(40);

            httpstatusContainer.getChildren().add(row);
        }
    }

    // Create control buttons for HTTP
    private void createHttpControlButtons(String ip) {
        httpcontrolsContainer.getChildren().clear();

        Label controlsLabel = new Label("HTTP Status Controls");
        controlsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        httpcontrolsContainer.getChildren().add(controlsLabel);

        // Create control rows with the same styling as TCP
        createHttpControlRow("Energizer Status", "1", ip, httpdeviceState.getOrDefault("FenceStaus", false));
        createHttpControlRow("Gadget Status", "2", ip, httpdeviceState.getOrDefault("LightStaus", false));
//        createHttpControlRow("Service Mode", "3", ip, httpdeviceState.getOrDefault("ServiceMode", false));
        // Add alarm acknowledgment button if alarm is active
        if (httpdeviceState.getOrDefault("IntrusionAlarm", false)) {
            Button acknowledgeButton = new Button("Acknowledge Alarm");
            acknowledgeButton.setStyle("-fx-background-color: #ff6666; -fx-text-fill: white; -fx-font-weight: bold;");
            acknowledgeButton.setOnAction(event -> sendHttpControlCommand(ip, "3", false)); // Assuming "4" is alarm reset

            HBox alarmRow = new HBox(10, acknowledgeButton);
            alarmRow.setStyle("-fx-padding: 10; -fx-background-color: #fff0f0; -fx-background-radius: 5;");
            alarmRow.setPrefHeight(40);
            httpcontrolsContainer.getChildren().add(alarmRow);
        }
    }

    private void createHttpControlRow(String label, String key, String ip, boolean initialState) {
        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-font-weight: bold;");
        nameLabel.setMinWidth(150);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        // Create ON button
        Button onButton = new Button("ON");
        onButton.setStyle(getButtonStyle(true, initialState));
        onButton.setDisable(initialState); // Disable if already ON
        onButton.setMinWidth(60);

        // Create OFF button - FIXED THE LOGIC HERE
        Button offButton = new Button("OFF");
        offButton.setStyle(getButtonStyle(false, initialState)); // Pass initialState directly
        offButton.setDisable(!initialState); // Disable if device is OFF (initialState is false)

        // ON button action
        onButton.setOnAction(event -> {
            onButton.setDisable(true);
            offButton.setDisable(false);
            onButton.setStyle(getButtonStyle(true, true));
            offButton.setStyle(getButtonStyle(false, true));

            // Update row background color
            HBox row = (HBox) onButton.getParent().getParent();
            row.setStyle("-fx-padding: 10; -fx-background-color: #e8f5e8; -fx-background-radius: 5;");

            sendHttpControlCommand(ip, key, true);
        });

        // OFF button action
        offButton.setOnAction(event -> {
            offButton.setDisable(true);
            onButton.setDisable(false);
            onButton.setStyle(getButtonStyle(true, false));
            offButton.setStyle(getButtonStyle(false, false));

            // Update row background color
            HBox row = (HBox) offButton.getParent().getParent();
            row.setStyle("-fx-padding: 10; -fx-background-color: #ffe8e8; -fx-background-radius: 5;");

            sendHttpControlCommand(ip, key, false);
        });

        HBox buttonContainer = new HBox(5, onButton, offButton);
        HBox row = new HBox(10, nameLabel, buttonContainer);

        String backgroundColor = initialState ? "#e8f5e8" : "#ffe8e8";
        row.setStyle("-fx-padding: 10; -fx-background-color: " + backgroundColor + "; -fx-background-radius: 5;");
        row.setPrefHeight(40);

        httpcontrolsContainer.getChildren().add(row);
    }

    private String getButtonStyle(boolean isOnButton, boolean deviceIsOn) {
        if (isOnButton) {
            // ON button styling
            if (deviceIsOn) {
                // Device is ON, so ON button should be disabled/faded
                return "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-opacity: 0.6;";
            } else {
                // Device is OFF, so ON button should be active
                return "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;";
            }
        } else {
            // OFF button styling
            if (deviceIsOn) {
                // Device is ON, so OFF button should be active
                return "-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;";
            } else {
                // Device is OFF, so OFF button should be disabled/faded
                return "-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-opacity: 0.6;";
            }
        }
    }


    private void sendHttpControlCommand(String ip, String key, boolean isOn) {
        // Construct command URL - add state parameter to URL if needed
        String urlString = "http://" + ip + "/leds.cgi?led=" + key;

        // Add value parameter if device requires it
        // urlString += "&value=" + (isOn ? "1" : "0");  // Uncomment if needed for your device

        httpresponseArea.appendText("\n\nSending HTTP command: " + urlString);

        final String finalUrl = urlString;
        final int HTTP_TIMEOUT = 5000;

        new Thread(() -> {
            try {
                System.out.println("Sending HTTP command: " + finalUrl);

                URL url = new URL(finalUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(HTTP_TIMEOUT);
                conn.setReadTimeout(HTTP_TIMEOUT);

                int responseCode = conn.getResponseCode();

                // Read response if available
                StringBuilder responseContent = new StringBuilder();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseContent.append(line).append("\n");
                    }
                    reader.close();
                }

                final String responseText = responseContent.toString();
                final int finalResponseCode = responseCode;

                System.out.println("HTTP command response code: " + responseCode);

                Platform.runLater(() -> {
                    StringBuilder message = new StringBuilder("\nCommand Response: HTTP Status Code ");
                    message.append(finalResponseCode);

                    if (!responseText.isEmpty()) {
                        message.append("\n").append(responseText);
                    }

                    httpresponseArea.appendText(message.toString());

                    // Refresh device status after command
                    if (finalResponseCode == 200) {
                        // Wait briefly before refreshing to allow the device to update its state
                        refreshHttpDeviceStatus(ip, true);
                    }
                });

                conn.disconnect();
            } catch (Exception e) {
                System.err.println("Error sending HTTP command: " + e.getMessage());
                final String errorMsg = e.getMessage();
                Platform.runLater(() ->
                        httpresponseArea.appendText("\nError sending HTTP command: " + errorMsg)
                );
            }
        }).start();
    }

    // Start periodic refresh of HTTP device status
    private void startHttpRefreshTimer() {
        stopHttpRefreshTimer(); // Ensure we don't have multiple timers

        httpScheduler = Executors.newSingleThreadScheduledExecutor();
        httpScheduler.scheduleAtFixedRate(() -> {
            if (isHttpConnected) {
                refreshHttpDeviceStatus(currentIp, false);
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    // Stop the HTTP refresh timer
    private void stopHttpRefreshTimer() {
        if (httpScheduler != null && !httpScheduler.isShutdown()) {
            httpScheduler.shutdownNow();
            httpScheduler = null;
        }
    }

    // Refresh HTTP device status
    private void refreshHttpDeviceStatus(String ip, boolean showInResponseArea) {
        try {
            Thread.sleep(500); // Short delay before refresh
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        new Thread(() -> {
            try {
                String urlString = "http://" + ip + "/status.xml";
                System.out.println("Refreshing HTTP status from: " + urlString);

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int status = conn.getResponseCode();

                if (status == 200) {
                    // Read XML response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder xmlContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        xmlContent.append(line).append("\n");
                    }
                    reader.close();

                    final String xmlResponse = xmlContent.toString();

                    Platform.runLater(() -> {
                        try {
                            // Parse and update status
                            Map<String, Boolean> statusMap = parseHttpXmlToMap(xmlResponse);
                            updateHttpStatusDisplay(statusMap);
                            createHttpControlButtons(ip);

                            if (showInResponseArea) {
                                httpresponseArea.appendText("\n\n--- HTTP Status Refresh ---\n" + xmlResponse);
                            }
                        } catch (Exception e) {
                            System.err.println("Error updating HTTP status: " + e.getMessage());
                            if (showInResponseArea) {
                                httpresponseArea.appendText("\nError updating HTTP status: " + e.getMessage());
                            }
                        }
                    });
                } else if (showInResponseArea) {
                    final int finalStatus = status;
                    Platform.runLater(() ->
                            httpresponseArea.appendText("\nHTTP refresh failed with status code: " + finalStatus)
                    );
                }

                conn.disconnect();
            } catch (Exception e) {
                System.err.println("Error refreshing HTTP status: " + e.getMessage());
                if (showInResponseArea) {
                    final String errorMsg = e.getMessage();
                    Platform.runLater(() ->
                            httpresponseArea.appendText("\nError refreshing HTTP status: " + errorMsg)
                    );
                }
            }
        }).start();
    }



















    // TCP CONNECTION HANDLING
    public void connectTcp(String ip) {
        responseArea1.setText("Connecting to " + ip + " via TCP...");

        // Match the Node.js port and timeout
        final int TCP_PORT = 1515;
        final int TCP_TIMEOUT = 5000; // 5 seconds timeout

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

                // Read response directly as bytes
                byte[] buffer = new byte[4096];
                int bytesRead;

                // Wait for response with timeout protection
                long startTime = System.currentTimeMillis();
                ByteArrayOutputStream responseData = new ByteArrayOutputStream();

                while (System.currentTimeMillis() - startTime < TCP_TIMEOUT) {
                    if (in.available() > 0) {
                        bytesRead = in.read(buffer);
                        if (bytesRead == -1) break;
                        responseData.write(buffer, 0, bytesRead);
                        break; // Only read once like Node.js
                    }
                    Thread.sleep(50); // Small delay to prevent CPU thrashing
                }

                final byte[] response = responseData.toByteArray();
                System.out.println("Received " + response.length + " bytes from " + ip);

                // Log the hex representation of the response
                StringBuilder hexResponseBuilder = new StringBuilder();
                for (byte b : response) {
                    hexResponseBuilder.append(String.format("%02x", b));
                }
                String hexResponse = hexResponseBuilder.toString();
                System.out.println("Response in hex: " + hexResponse);

                // Close the socket
                socket.close();

                // Process response on the JavaFX thread
                if (response.length > 0) {
                    Platform.runLater(() -> {
                        try {
                            // Process the binary response similar to Node.js
                            Map<String, Object> deviceData = processTcpResponse(response, ip);
                            updateTcpStatusFromBinaryResponse(deviceData);
                            createTcpControlButtons(ip);

                            // Display a more readable response in the UI
                            StringBuilder formattedResponse = new StringBuilder("TCP Connection Successful\n\n");
                            for (Map.Entry<String, Object> entry : deviceData.entrySet()) {
                                formattedResponse.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                            }

                            responseArea1.setText(formattedResponse.toString());
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

    // Process TCP response in binary format as in the Node.js code
    private Map<String, Object> processTcpResponse(byte[] response, String ip) {
        System.out.println("Processing TCP response for IP " + ip + ". Raw response length: " + response.length);

        // Print hex representation for debugging
        StringBuilder hexString = new StringBuilder();
        for (byte b : response) {
            hexString.append(String.format("%02x", b));
        }
        System.out.println("Response hex: " + hexString);

        Map<String, Object> deviceData = new HashMap<>();

        // Check if it's not the expected 16-byte response
        if (response.length != 16) {
            System.err.println("Invalid response length: expected 16, got " + response.length);
            throw new RuntimeException("Invalid response length: expected 16, got " + response.length);
        }

        // Calculate checksum (sum of first 13 elements & 0xFF)
        int checkSum = 0;
        for (int i = 0; i < 13; i++) {
            checkSum += (response[i] & 0xFF);
        }
        checkSum = checkSum & 0xFF;

        // Verify signature bytes and checksum
        if (!((response[14] & 0xFF) == 67 && (response[15] & 0xFF) == 83 &&
                (response[13] & 0xFF) == checkSum)) {
            System.err.println("Invalid checksum or signature. Expected checksum: " + checkSum +
                    ", got: " + (response[13] & 0xFF));
            throw new RuntimeException("Invalid checksum or signature");
        }

        // Extract device properties (bytes 2-8)
        String[] deviceProps = {"FVout", "FVReturn", "BV", "Check", "GadgetStatus", "FenceStatus", "AlarmStatus"};
        for (int i = 0; i < deviceProps.length; i++) {
            deviceData.put(deviceProps[i], response[i + 2] & 0xFF);
        }

        // Process bit flags from the 12th byte
        int twelfthByte = response[12] & 0xFF;
        String[] devicePropsBool = {"Inp4", "Inp3", "Inp2", "ServiceMode", "LidStatus", "DrinageIntrusion", "EncloserStatus", "MainsStatus"};
        for (int i = 0; i < devicePropsBool.length; i++) {
            deviceData.put(devicePropsBool[i], ((twelfthByte & (1 << i)) != 0) ? 1 : 0);
        }

        System.out.println("Processed device data: " + deviceData);
        return deviceData;
    }

    // Update the UI based on binary response data
    private void updateTcpStatusFromBinaryResponse(Map<String, Object> deviceData) {
        Map<String, Boolean> statusMap = new HashMap<>();
        tcpdeviceState.clear();

        // Map the binary response fields to the status display fields
        statusMap.put("Intrusion", getIntValue(deviceData, "AlarmStatus") == 1);
        statusMap.put("Drain Intrusion", getIntValue(deviceData, "DrinageIntrusion") == 1);
        statusMap.put("Enclosure Open", getIntValue(deviceData, "EncloserStatus") == 1);
        statusMap.put("Lid Open", getIntValue(deviceData, "LidStatus") == 1);
        statusMap.put("System Check", getIntValue(deviceData, "Check") == 1);

        // Update the device state map
        tcpdeviceState.put("Fence", getIntValue(deviceData, "FenceStatus") == 1);
        tcpdeviceState.put("Light", getIntValue(deviceData, "GadgetStatus") == 1);
        tcpdeviceState.put("Service", getIntValue(deviceData, "ServiceMode") == 1);
        tcpdeviceState.put("Intrusion", getIntValue(deviceData, "AlarmStatus") == 1);
        tcpdeviceState.put("Drainage", getIntValue(deviceData, "DrinageIntrusion") == 1);
        tcpdeviceState.put("Enclosure", getIntValue(deviceData, "EncloserStatus") == 1);
        tcpdeviceState.put("Lid", getIntValue(deviceData, "LidStatus") == 1);

        // Update the UI
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

    // Safe method to get integer values from the device data map
    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    // Updated command sending method to use binary commands
    private void sendTcpControlCommand(String ip, String command, boolean isOn) {
        // Map of command strings to hex values
        Map<String, String> commandHexMap = new HashMap<>();
        commandHexMap.put("FENCE_ON", "5455524E46454E43454F4E0000474353");    // ARM
        commandHexMap.put("FENCE_OFF", "5455524E46454E43454F464600854353");    // DISARM
        commandHexMap.put("POSTPONE", "504F5354504F4E450000000000784353");     // POSTPONE
        commandHexMap.put("ALARM_OFF", "5455524E414C41524D4F464600914353");    // ACKNOWLEDGE
        commandHexMap.put("LIGHT_ON", "5455524E4C494748544F4E00005E4353");     // GADGET_ON
        commandHexMap.put("LIGHT_OFF", "5455524E4C494748544F4646009C4353");    // GADGET_OFF
        commandHexMap.put("SERVICE_ON", "5352564943454D4F44454F4E008E4353");   // SERVICE_MODE_ON
        commandHexMap.put("SERVICE_OFF", "5352564943454D4F44454F4646CC4353");  // SERVICE_MODE_OFF

        // Select the appropriate command
        String commandKey = null;
        if (command.equals("FENCE")) {
            commandKey = isOn ? "FENCE_ON" : "FENCE_OFF";
        } else if (command.equals("LIGHT")) {
            commandKey = isOn ? "LIGHT_ON" : "LIGHT_OFF";
        } else if (command.equals("SERVICE")) {
            commandKey = isOn ? "SERVICE_ON" : "SERVICE_OFF";
        } else if (command.equals("ALARM")) {
            commandKey = "ALARM_OFF";  // This is always off
        } else if (command.equals("POSTPONE")) {
            commandKey = "POSTPONE";
        }

        if (commandKey == null) {
            responseArea1.appendText("\n\nInvalid command: " + command);
            return;
        }

        String hexCommand = commandHexMap.get(commandKey);
        byte[] commandBytes = hexStringToByteArray(hexCommand);

        responseArea1.appendText("\n\nSending TCP command: " + commandKey + " (HEX: " + hexCommand + ")");

        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.setSoTimeout(TCP_TIMEOUT);
                socket.connect(new InetSocketAddress(ip, TCP_PORT), TCP_TIMEOUT);

                // Send binary command
                OutputStream out = socket.getOutputStream();
                out.write(commandBytes);
                out.flush();

                // Read binary response
                InputStream in = socket.getInputStream();
                ByteArrayOutputStream responseData = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;

                // Wait for response with timeout
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < TCP_TIMEOUT) {
                    if (in.available() > 0) {
                        bytesRead = in.read(buffer);
                        if (bytesRead == -1) break;
                        responseData.write(buffer, 0, bytesRead);
                        break;
                    }
                    Thread.sleep(50);
                }

                final byte[] response = responseData.toByteArray();

                // Close the socket
                socket.close();

                StringBuilder hexResponse = new StringBuilder();
                for (byte b : response) {
                    hexResponse.append(String.format("%02x", b));
                }

                Platform.runLater(() -> {
                    try {
                        responseArea1.appendText("\nCommand Response (HEX): " + hexResponse);

                        if (response.length > 0) {
                            // Process response
                            Map<String, Object> deviceData = processTcpResponse(response, ip);
                            updateTcpStatusFromBinaryResponse(deviceData);
                            createTcpControlButtons(ip);
                        } else {
                            responseArea1.appendText("\nEmpty response received");
                        }
                    } catch (Exception e) {
                        responseArea1.appendText("\nError processing response: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                final String errorMsg = e.getMessage();
                Platform.runLater(() -> responseArea1.appendText("\nError sending TCP command: " + errorMsg));
            }
        }).start();
    }

    // Updated refresh method to use binary commands
    private void refreshTcpDeviceStatus(String ip, boolean showInResponseArea) {
        try {
            Thread.sleep(500); // Shorter delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.setSoTimeout(TCP_TIMEOUT);
                socket.connect(new InetSocketAddress(ip, TCP_PORT), TCP_TIMEOUT);

                // Send binary GET_ALL_STATUS command
                String statusCommandHex = "474554414C4C535441545553009D4353";
                byte[] statusCommandBytes = hexStringToByteArray(statusCommandHex);

                OutputStream out = socket.getOutputStream();
                out.write(statusCommandBytes);
                out.flush();

                // Read binary response
                InputStream in = socket.getInputStream();
                ByteArrayOutputStream responseData = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;

                // Wait for response with timeout
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < TCP_TIMEOUT) {
                    if (in.available() > 0) {
                        bytesRead = in.read(buffer);
                        if (bytesRead == -1) break;
                        responseData.write(buffer, 0, bytesRead);
                        break;
                    }
                    Thread.sleep(50);
                }

                final byte[] response = responseData.toByteArray();

                // Close the socket
                socket.close();

                if (response.length > 0) {
                    Platform.runLater(() -> {
                        try {
                            // Process binary response
                            Map<String, Object> deviceData = processTcpResponse(response, ip);
                            updateTcpStatusFromBinaryResponse(deviceData);
                            createTcpControlButtons(ip);

                            if (showInResponseArea) {
                                StringBuilder hexResponse = new StringBuilder();
                                for (byte b : response) {
                                    hexResponse.append(String.format("%02x", b));
                                }

                                StringBuilder formattedResponse = new StringBuilder("\n\n--- TCP Status Refresh ---\n");
                                formattedResponse.append("Raw response (HEX): ").append(hexResponse).append("\n\n");

                                for (Map.Entry<String, Object> entry : deviceData.entrySet()) {
                                    formattedResponse.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                                }

                                responseArea1.appendText(formattedResponse.toString());
                            }
                        } catch (Exception e) {
                            if (showInResponseArea) {
                                responseArea1.appendText("\nError updating TCP status: " + e.getMessage());
                            }
                        }
                    });
                } else if (showInResponseArea) {
                    Platform.runLater(() -> responseArea1.appendText("\nEmpty response received during refresh"));
                }

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

    private void createTcpControlButtons(String ip) {
        controlsContainer1.getChildren().clear();

        Label controlsLabel = new Label("TCP Status Controls");
        controlsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        controlsContainer1.getChildren().add(controlsLabel);

        createTcpControlRow("Energizers Status", "FENCE", ip, tcpdeviceState.getOrDefault("Fence", false));
        createTcpControlRow("Gadget Status", "LIGHT", ip, tcpdeviceState.getOrDefault("Light", false));
        createTcpControlRow("Service Mode", "SERVICE", ip, tcpdeviceState.getOrDefault("Service", false));

        // Add alarm acknowledgment button if alarm is active
        if (tcpdeviceState.getOrDefault("Intrusion", false)) {
            Button acknowledgeButton = new Button("Acknowledge Alarm");
            acknowledgeButton.setStyle("-fx-background-color: #ff6666; -fx-text-fill: white; -fx-font-weight: bold;");
            acknowledgeButton.setOnAction(event -> sendTcpControlCommand(ip, "ALARM", false));

            HBox alarmRow = new HBox(10, acknowledgeButton);
            alarmRow.setStyle("-fx-padding: 10; -fx-background-color: #fff0f0; -fx-background-radius: 5;");
            alarmRow.setPrefHeight(40);
            controlsContainer1.getChildren().add(alarmRow);
        }
    }

    private void createTcpControlRow(String label, String command, String ip, boolean initialState) {
        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-font-weight: bold;");
        nameLabel.setMinWidth(150);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        // Create ON button
        Button onButton = new Button("ON");
        onButton.setStyle(getTcpButtonStyle(true, initialState));
        onButton.setDisable(initialState); // Disable if already ON
        onButton.setMinWidth(60);

        // Create OFF button
        Button offButton = new Button("OFF");
        offButton.setStyle(getTcpButtonStyle(false, initialState));
        offButton.setDisable(!initialState); // Disable if device is OFF (initialState is false)

        // ON button action
        onButton.setOnAction(event -> {
            onButton.setDisable(true);
            offButton.setDisable(false);
            onButton.setStyle(getTcpButtonStyle(true, true));
            offButton.setStyle(getTcpButtonStyle(false, true));

            // Update row background color
            HBox row = (HBox) onButton.getParent().getParent();
            row.setStyle("-fx-padding: 10; -fx-background-color: #e8f5e8; -fx-background-radius: 5;");

            sendTcpControlCommand(ip, command, true);
        });

        // OFF button action
        offButton.setOnAction(event -> {
            offButton.setDisable(true);
            onButton.setDisable(false);
            onButton.setStyle(getTcpButtonStyle(true, false));
            offButton.setStyle(getTcpButtonStyle(false, false));

            // Update row background color
            HBox row = (HBox) offButton.getParent().getParent();
            row.setStyle("-fx-padding: 10; -fx-background-color: #ffe8e8; -fx-background-radius: 5;");

            sendTcpControlCommand(ip, command, false);
        });

        HBox buttonContainer = new HBox(5, onButton, offButton);
        HBox row = new HBox(10, nameLabel, buttonContainer);

        // Set initial background color based on device state
        String backgroundColor = initialState ? "#e8f5e8" : "#ffe8e8";
        row.setStyle("-fx-padding: 10; -fx-background-color: " + backgroundColor + "; -fx-background-radius: 5;");
        row.setPrefHeight(40);

        controlsContainer1.getChildren().add(row);
    }

    private String getTcpButtonStyle(boolean isOnButton, boolean deviceIsOn) {
        if (isOnButton) {
            // ON button styling
            if (deviceIsOn) {
                // Device is ON, so ON button should be disabled/faded
                return "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-opacity: 0.6;";
            } else {
                // Device is OFF, so ON button should be active
                return "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;";
            }
        } else {
            // OFF button styling
            if (deviceIsOn) {
                // Device is ON, so OFF button should be active
                return "-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;";
            } else {
                // Device is OFF, so OFF button should be disabled/faded
                return "-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold; -fx-opacity: 0.6;";
            }
        }
    }
    private void startTcpRefreshTimer() {
        stopTcpRefreshTimer(); // Ensure we don't have multiple timers

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





    // Clean up resources when application closes
    public void shutdown() {
        stopHttpRefreshTimer();
        stopTcpRefreshTimer();
    }
}