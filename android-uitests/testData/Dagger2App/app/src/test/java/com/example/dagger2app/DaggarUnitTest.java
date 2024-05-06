package com.example.dagger2app;


import com.example.dagger2app.intro.Car;
import com.example.dagger2app.intro.DaggerVehicleComponent;
import com.example.dagger2app.intro.VehicleComponent;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class DaggarUnitTest {

    @Test
    public void buildCarWithInjection(){
        VehicleComponent component = DaggerVehicleComponent.create();

        Car carOne = component.buildCar();
        Car carTwo = component.buildCar();

        Assert.assertNotNull(carOne);
        Assert.assertNotNull(carOne.getEngine());
        Assert.assertNotNull(carOne.getBrand());

        Assert.assertNotNull(carTwo);
        Assert.assertNotNull(carTwo.getEngine());
        Assert.assertNotNull(carTwo.getBrand());

        Assert.assertNotEquals(carOne.getEngine(), carTwo.getEngine());
        Assert.assertEquals(carOne.getBrand(), carTwo.getBrand());



    }

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }
}