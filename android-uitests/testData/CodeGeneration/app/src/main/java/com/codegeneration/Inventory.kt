package com.codegeneration

class Inventory {

    init {
        addNewCar()
    }

    public fun addNewCar() {
        val car = Car.getInstance()
        car.make = "Mercedes"
        car.model = "C-Class"
        car.year = 2019

        val make = car.make
        val model = car.model
        val year = car.year
    }
}