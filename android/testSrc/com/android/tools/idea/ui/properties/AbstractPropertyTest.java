/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.ui.properties;

import com.android.tools.idea.ui.properties.core.*;
import org.junit.Test;

import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

public class AbstractPropertyTest {

  @Test
  public void testGetAll() {
    TestInvokeStrategy testStrategy = new TestInvokeStrategy();
    ListenerManager listeners = new ListenerManager(testStrategy);

    ObjectWithObservableProperties dummy = new ObjectWithObservableProperties();
    CountingRunnable counter = new CountingRunnable();

    List<AbstractProperty<?>> dummyProperties = AbstractProperty.getAll(dummy);
    assert !dummyProperties.isEmpty();

    listeners.listenAll(dummyProperties).with(counter);

    assertThat(counter.myRunCount).isEqualTo(0);
    dummy.bool().set(true);
    testStrategy.updateOneStep();
    assertThat(counter.myRunCount).isEqualTo(1);
    dummy.integer().set(1);
    testStrategy.updateOneStep();
    assertThat(counter.myRunCount).isEqualTo(2);
    dummy._double().set(1.0);
    testStrategy.updateOneStep();
    assertThat(counter.myRunCount).isEqualTo(3);
    dummy.string.set("test");
    testStrategy.updateOneStep();
    assertThat(counter.myRunCount).isEqualTo(4);
  }

  @Test
  public void testGetAllWithNoObservableProperties() {
    ObjectWithNoObservableProperties object = new ObjectWithNoObservableProperties(false, 42, 42.0, "test");
    List<AbstractProperty<?>> properties = AbstractProperty.getAll(object);
    assert properties.isEmpty();
  }

  private static class ObjectWithObservableProperties {
    // To make sure everything is working we do public private, package private and protected variables
    private BoolProperty myBool = new BoolValueProperty();
    protected IntProperty myInteger = new IntValueProperty();
    DoubleProperty myDouble = new DoubleValueProperty();
    public StringProperty string = new StringValueProperty();

    public BoolProperty bool() {
      return myBool;
    }

    public IntProperty integer() {
      return myInteger;
    }

    public DoubleProperty _double() {
      return myDouble;
    }
  }

  private static class ObjectWithNoObservableProperties {
    private boolean myBool;
    protected int myInteger;
    double myDouble;

    public String string = new String();

    public boolean isBool() {
      return myBool;
    }

    public int getInteger() {
      return myInteger;
    }

    public double getDouble() {
      return myDouble;
    }

    public ObjectWithNoObservableProperties(boolean bool, int integer, double aDouble, String string) {
      myBool = bool;
      myInteger = integer;
      myDouble = aDouble;
      this.string = string;
    }
  }

  private static class CountingRunnable implements Runnable {
    int myRunCount;

    @Override
    public void run() {
      myRunCount++;
    }
  }
}
