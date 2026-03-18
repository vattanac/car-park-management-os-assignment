/**
 * Module descriptor for the Car Park Management Simulation.
 */
module com.carpark {
    requires javafx.controls;
    requires javafx.graphics;

    opens com.carpark.ui to javafx.graphics;
    exports com.carpark.ui;
    exports com.carpark.model;
    exports com.carpark.sync;
    exports com.carpark.thread;
}
