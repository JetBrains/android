package com.example.dagger2app.intro;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = VehiclesModule.class)
public interface VehicleComponent {

    Car buildCar();
}
