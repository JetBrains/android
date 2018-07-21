/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.swingp;

import org.jetbrains.annotations.NotNull;
import com.google.common.truth.Truth;
import com.google.gson.JsonArray;
import org.junit.Test;

public class MethodStatTest {
  @Test(expected = RuntimeException.class)
  public void catchesDoubleEnd() {
    RenderStatsManager.setIsEnabled(true);
    BadTestClass bad = new BadTestClass();
    bad.doubleBad();
    RenderStatsManager.setIsEnabled(false);
  }

  @Test
  public void methodStatEndsCorrect() {
    RenderStatsManager.setIsEnabled(true);
    TestClass good = new TestClass();
    good.foo();
    RenderStatsManager.setIsEnabled(false);
  }

  @Test
  public void arraySerializesCorrectly() {
    int[] intArray = {5, 7};
    JsonArray jsonIntArray = SerializationHelpers.arrayToJsonArray(intArray);
    Truth.assertThat(jsonIntArray.size()).isEqualTo(2);
    Truth.assertThat(jsonIntArray.get(0).getAsInt()).isEqualTo(intArray[0]);
    Truth.assertThat(jsonIntArray.get(1).getAsInt()).isEqualTo(intArray[1]);

    double[] doubleArray = {3.5, 9.5, 11};
    JsonArray jsonDoubleArray = SerializationHelpers.arrayToJsonArray(doubleArray);
    Truth.assertThat(jsonDoubleArray.size()).isEqualTo(3);
    Truth.assertThat(jsonDoubleArray.get(0).getAsDouble()).isEqualTo(doubleArray[0]);
    Truth.assertThat(jsonDoubleArray.get(1).getAsDouble()).isEqualTo(doubleArray[1]);
    Truth.assertThat(jsonDoubleArray.get(2).getAsDouble()).isEqualTo(doubleArray[2]);
  }

  /**
   * Trivial extension of {@link MethodStat} (since it's abstract) to test its implementation.
   */
  private static final class TestMethodStat extends MethodStat {
    public TestMethodStat(@NotNull Object owner) {
      super(owner);
    }
  }

  private static final class TestClass {
    public void foo() {
      MethodStat stat = new TestMethodStat(this);
      stat.endMethod();
    }
  }

  private static final class BadTestClass {
    private final TestClass myTestClassAObject = new TestClass();

    public void doubleBad() {
      MethodStat stat = new TestMethodStat(this);
      myTestClassAObject.foo();
      stat.endMethod();
      stat.endMethod();
    }
  }
}
