package com.example.fencedeviceapp;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Controller {

    @FXML
    private TextField ipField;

    @FXML
    private TextArea responseArea;

    @FXML
    private VBox statusContainer;
    @FXML
    private VBox controlsContainer;
    private Map<String, Boolean> deviceState = new HashMap<>();

    @FXML
    public void handleConnect() {
        String ip = ipField.getText().trim();
        if (ip.isEmpty()) {
            responseArea.setText("Please enter an IP address.");
            return;
        }

        String urlString = "http://" + ip + "/status.xml";
        responseArea.setText("Connecting to " + urlString + "...");

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
                    updateStatusFromResponse(xmlContent.toString());
                    // Create control buttons after status is updated
                    createControlButtons(ip);
                    responseArea.setText(result.toString());
                } catch (Exception e) {
                    responseArea.setText(result + "\n\nError parsing XML: " + e.getMessage());
                }
            } else {
                responseArea.setText(result + "\nFailed to connect. Check IP address and try again.");
            }

            conn.disconnect();

        } catch (Exception e) {
            responseArea.setText("Connection Error: " + e.getMessage());
        }
    }

    public void updateStatusFromResponse(String xmlResponse) throws Exception {
        List<StatusIndicator> indicators = parseXml(xmlResponse);
        statusContainer.getChildren().clear();

        // Set padding for the entire status container
        statusContainer.setStyle("-fx-padding: 10px; -fx-background-color: #f5f5f5; -fx-background-radius: 5;");

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

            statusContainer.getChildren().add(row);
        }
    }

    public List<StatusIndicator> parseXml(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes()));

        // Store device state for controls
        deviceState.put("IntrusionAlarm", getValue(doc, "IntrusionAlarm"));
        deviceState.put("DrainageIntrusionAlarm", getValue(doc, "DrainageIntrusionAlarm"));
        deviceState.put("EncloserAlarm", getValue(doc, "EncloserAlarm"));
        deviceState.put("LidAlarm", getValue(doc, "LidAlarm"));
        deviceState.put("ServiceMode", getValue(doc, "ServiceMode"));
        deviceState.put("FenceStaus", getValue(doc, "FenceStaus"));
        deviceState.put("LightStaus", getValue(doc, "LightStaus"));

        return List.of(
                new StatusIndicator("Intrusion", getValue(doc, "IntrusionAlarm")),
                new StatusIndicator("Drain Intrusion", getValue(doc, "DrainageIntrusionAlarm")),
                new StatusIndicator("Enclosure Open", getValue(doc, "EncloserAlarm")),
                new StatusIndicator("Lid Open", getValue(doc, "LidAlarm")),
                new StatusIndicator("System Check", getValue(doc, "ServiceMode"))
        );
    }

    private boolean getValue(Document doc, String tagName) {
        Node node = doc.getElementsByTagName(tagName).item(0);
        return node != null && node.getTextContent().trim().equals("1");
    }

    public void createControlButtons(String ip) {
        // Clear existing controls
        controlsContainer.getChildren().clear();

        // Add label for the controls section
        Label controlsLabel = new Label("HTTP Status Controls");
        controlsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        controlsContainer.getChildren().add(controlsLabel);

        // Create control rows with toggle buttons
        createControlRow("Fence System", "1", ip, deviceState.getOrDefault("FenceStaus", false));
        createControlRow("Gadget System", "2", ip, deviceState.getOrDefault("LightStaus", false));
        createControlRow("Service Mode", "3", ip, deviceState.getOrDefault("ServiceMode", false));
    }

    private void createControlRow(String label, String key, String ip, boolean initialState) {
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
            sendControlCommand(ip, key, isOn);
        });

        row.getChildren().addAll(nameLabel, toggleButton);
        controlsContainer.getChildren().add(row);
    }

    private String getToggleButtonStyle(boolean isOn) {
        return isOn
                ? "-fx-background-color: #3366cc; -fx-text-fill: white; -fx-background-radius: 15;"
                : "-fx-background-color: #cccccc; -fx-text-fill: black; -fx-background-radius: 15;";
    }

    private void sendControlCommand(String ip, String key, boolean isOn) {
        // Construct the URL with parameters
        String urlString = "http://" + ip + "/leds.cgi?led=" + key;

        // Add to response area
        responseArea.appendText("\n\nSending command: " + urlString);

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

                    // Refresh the status after changing a setting
                    refreshDeviceStatus(ip);
                }

                // Update UI on the JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    responseArea.appendText(responseText.toString());
                });

                conn.disconnect();
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    responseArea.appendText("\nError sending command: " + e.getMessage());
                });
            }
        }).start();
    }

    private void refreshDeviceStatus(String ip) {
        // Wait briefly before refreshing to allow the device to update
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Fetch the updated status
        javafx.application.Platform.runLater(() -> {
            try {
                URL url = new URL("http://" + ip + "/status.xml");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int status = conn.getResponseCode();
                if (status == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder xmlContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        xmlContent.append(line).append("\n");
                    }
                    reader.close();

                    // Update UI with new status
                    updateStatusFromResponse(xmlContent.toString());
                    createControlButtons(ip);
                }

                conn.disconnect();
            } catch (Exception e) {
                responseArea.appendText("\nError refreshing status: " + e.getMessage());
            }
        });
    }
}