package com.codegeneration;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String hello = "hello";
        addNewCar();
        new Inventory().addNewCar();

    }

    private void addNewCar() {
        Car car = Car.getInstance();
        car.setMake("BMW");
        car.setModel("M5");
        car.setYear(2019);

        String make = car.getMake();
        String model = car.getModel();
        int year = car.getYear();

    }
}