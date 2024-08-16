package com.example.dagger2app


import com.example.dagger2app.intro.DaggerVehicleComponent

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
class DaggarTestUnit {

    @Test
    fun buildCarWithInjection() {
        val component = DaggerVehicleComponent.create()

        val carOne = component.buildCar()
        val carTwo = component.buildCar()

        assertNotNull(carOne)
        assertNotNull(carOne.engine)
        assertNotNull(carOne.brand)

        assertNotNull(carTwo)
        assertNotNull(carTwo.engine)
        assertNotNull(carTwo.brand)

        assertNotEquals(carOne.engine, carTwo.engine)
        assertEquals(carOne.brand, carTwo.brand)


    }

    @Test
    fun addition_isCorrect() {
        assertEquals(4, (2 + 2).toLong())
    }
}