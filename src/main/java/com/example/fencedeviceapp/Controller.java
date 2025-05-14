package com.example.fencedeviceapp;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
import java.util.List;


public class Controller {

    @FXML
    private TextField ipField;

    @FXML
    private TextArea responseArea;

    @FXML
    private VBox statusContainer;

    @FXML
    public void handleConnect() {
        String ip = ipField.getText().trim();
        if (ip.isEmpty()) {
            responseArea.setText("Please enter an IP address.");
            return;
        }

        String urlString = "http://" + ip + "/status.xml";

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();
            StringBuilder result = new StringBuilder("HTTP Status Code: " + status + "\n");

            if (status == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
                reader.close();
            }

            conn.disconnect();
            responseArea.setText(result.toString());

        } catch (Exception e) {
            responseArea.setText("Error: " + e.getMessage());
        }
    }



    public void updateStatusFromResponse(String xmlResponse) throws Exception {
        List<StatusIndicator> indicators = parseXml(xmlResponse);
        statusContainer.getChildren().clear();

        for (StatusIndicator indicator : indicators) {
            Label label = new Label(indicator.getLabel());
            Label value = new Label(indicator.isStatus() ? "Yes" : "No");

            value.setStyle("-fx-background-color: " + (indicator.isStatus() ? "#ffcccc" : "#ccffe0") + ";" +
                    "-fx-text-fill: black; -fx-padding: 4px 10px; -fx-background-radius: 12;");

            label.setMinWidth(150);
            HBox row = new HBox(10, label, value);
            statusContainer.getChildren().add(row);
        }
    }

    public List<StatusIndicator> parseXml(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes()));
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
}
