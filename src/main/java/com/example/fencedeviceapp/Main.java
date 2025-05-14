package com.example.fencedeviceapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("layout.fxml"));
        primaryStage.setScene(new Scene(loader.load()));
        primaryStage.setTitle("Device Status Monitor");
        primaryStage.show();

        // Inject mock XML after UI loads
        Controller controller = loader.getController();
        String mockXml = """
            <Response>
              <FVout>87</FVout>
              <FVReturn>87</FVReturn>
              <BV>0</BV>
              <SPV>0</SPV>
              <ACStatus>0</ACStatus>
              <SyncStatus>0</SyncStatus>
              <LidAlarm>0</LidAlarm>
              <EncloserAlarm>0</EncloserAlarm>
              <DrainageIntrusionAlarm>1</DrainageIntrusionAlarm>
              <TemparatureAlarm>0</TemparatureAlarm>
              <IntrusionAlarm>0</IntrusionAlarm>
              <FenceStaus>1</FenceStaus>
              <LightStaus>0</LightStaus>
              <ServiceMode>0</ServiceMode>
              <Inp1>0</Inp1>
              <Inp2>1</Inp2>
              <Inp3>0</Inp3>
            </Response>
        """;

        // ✅ Call the correct method
        controller.updateStatusFromResponse(mockXml);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
