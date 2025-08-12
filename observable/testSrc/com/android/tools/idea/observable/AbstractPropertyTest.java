/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.observable;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.DoubleProperty;
import com.android.tools.idea.observable.core.DoubleValueProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.IntValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.Expression;
import java.util.List;
import org.junit.Test;

public class AbstractPropertyTest {

  @Test
  public void testGetAll() {
    TestInvokeStrategy testStrategy = new TestInvokeStrategy();
    ListenerManager listeners = new ListenerManager(testStrategy);

    ObjectWithObservableProperties sample = new ObjectWithObservableProperties();
    CountingRunnable counter = new CountingRunnable();

    List<AbstractProperty<?>> sampleProperties = AbstractProperty.getAll(sample);
    assert !sampleProperties.isEmpty();

    listeners.listenAll(sampleProperties).with(counter);

    assertThat(counter.myRunCount).isEqualTo(0);
    sample.bool().set(true);
    testStrategy.updateOneStep();
    assertThat(counter.myRunCount).isEqualTo(1);
    sample.integer().set(1);
    testStrategy.updateOneStep();
    assertThat(counter.myRunCount).isEqualTo(2);
    sample._double().set(1.0);
    testStrategy.updateOneStep();
    assertThat(counter.myRunCount).isEqualTo(3);
    sample.string.set("test");
    testStrategy.updateOneStep();
    assertThat(counter.myRunCount).isEqualTo(4);
  }

  @Test
  public void testGetAllWithNoObservableProperties() {
    ObjectWithNoObservableProperties object = new ObjectWithNoObservableProperties(false, 42, 42.0, "test");
    List<AbstractProperty<?>> properties = AbstractProperty.getAll(object);
    assert properties.isEmpty();
  }

  @Test
  public void testTransformMethod() {
    IntProperty source = new IntValueProperty(10);
    Expression<String> transformed = source.transform(Object::toString);

    assertThat(transformed.get()).isEqualTo("10");
    source.set(20);
    assertThat(transformed.get()).isEqualTo("20");
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
