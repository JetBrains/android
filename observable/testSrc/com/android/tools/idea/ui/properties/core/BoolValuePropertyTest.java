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

public final class BoolValuePropertyTest {
  @Test
  public void testInitialization() {
    {
      BoolValueProperty boolValue = new BoolValueProperty(true);
      assertThat(boolValue.get()).isEqualTo(true);
    }

    {
      BoolValueProperty boolValue = new BoolValueProperty();
      assertThat(boolValue.get()).isEqualTo(false);
    }
  }

  @Test
  public void testSetValue() {
    BoolValueProperty boolValue = new BoolValueProperty(false);
    boolValue.set(true);
    assertThat(boolValue.get()).isEqualTo(true);
  }

  @Test
  public void testInvertValue() {
    BoolValueProperty boolValue = new BoolValueProperty(false);
    boolValue.invert();
    assertThat(boolValue.get()).isEqualTo(true);
  }

  @Test
  public void testInvalidationListenerFiredOnValueChange() {
    BoolValueProperty boolValue = new BoolValueProperty();
    CountListener listener = new CountListener();
    boolValue.addListener(listener);

    assertThat(listener.getCount()).isEqualTo(0);
    boolValue.set(true);
    assertThat(listener.getCount()).isEqualTo(1);
    boolValue.set(true);
    assertThat(listener.getCount()).isEqualTo(1);
  }
}