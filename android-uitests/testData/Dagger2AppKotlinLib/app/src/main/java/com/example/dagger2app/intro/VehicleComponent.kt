package com.example.dagger2app.intro

import javax.inject.Singleton

import dagger.Component

@Singleton
@Component(modules = [VehiclesModule::class])
interface VehicleComponent {

    companion object {
        fun init(): VehicleComponent = DaggerVehicleComponent.builder().build()
    }

    fun buildCar(): Car
}
