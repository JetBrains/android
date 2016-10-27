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

public final class IntValuePropertyTest {
  @Test
  public void testInitialization() {
    {
      IntValueProperty intValue = new IntValueProperty(42);
      assertThat(intValue.get()).isEqualTo(42);
    }

    {
      IntValueProperty intValue = new IntValueProperty();
      assertThat(intValue.get()).isEqualTo(0);
    }
  }

  @Test
  public void testSetValue() {
    IntValueProperty intValue = new IntValueProperty(0);
    intValue.set(-20);
    assertThat(intValue.get()).isEqualTo(-20);
  }

  @Test
  public void testIncrementValue() {
    IntValueProperty intValue = new IntValueProperty(3);
    intValue.increment();
    assertThat(intValue.get()).isEqualTo(4);
  }

  @Test
  public void testInvalidationListenerFiredOnValueChange() {
    IntValueProperty intValue = new IntValueProperty(0);
    CountListener listener = new CountListener();
    intValue.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);
    intValue.set(10);
    assertThat(listener.getCount()).isEqualTo(1);
    intValue.set(10);
    assertThat(listener.getCount()).isEqualTo(1);
  }
}