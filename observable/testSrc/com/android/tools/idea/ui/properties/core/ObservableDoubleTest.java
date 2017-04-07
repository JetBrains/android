/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui.properties.core;

import com.android.tools.idea.ui.properties.CountListener;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class ObservableDoubleTest {
  @Test
  public void testInitialization() {
    {
      DoubleValueProperty doubleValue = new DoubleValueProperty(42.0);
      assertThat(doubleValue.get()).isWithin(0.01).of(42.0);
    }

    {
      DoubleValueProperty doubleValue = new DoubleValueProperty();
      assertThat(doubleValue.get()).isWithin(0.01).of(0.0);
    }
  }

  @Test
  public void testSetValue() {
    DoubleValueProperty doubleValue = new DoubleValueProperty(0.0);
    doubleValue.set(-20.0);
    assertThat(doubleValue.get()).isWithin(0.01).of(-20.0);
  }

  @Test
  public void testIncrementValue() {
    DoubleValueProperty doubleValue = new DoubleValueProperty(3.0);
    doubleValue.increment();
    assertThat(doubleValue.get()).isWithin(0.01).of(4.0);
  }

  @Test
  public void testInvalidationListenerFiredOnValueChange() {
    DoubleValueProperty doubleValue = new DoubleValueProperty(0.0);
    CountListener listener = new CountListener();
    doubleValue.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);
    doubleValue.set(10.0);
    assertThat(listener.getCount()).isEqualTo(1);
    doubleValue.set(10.0);
    assertThat(listener.getCount()).isEqualTo(1);
  }

}