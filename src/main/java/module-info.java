module com.example.fencedeviceapp {
    requires javafx.controls;
    requires javafx.fxml;

    requires java.xml;
    opens com.example.fencedeviceapp to javafx.fxml;
    exports com.example.fencedeviceapp;
}