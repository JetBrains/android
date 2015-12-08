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
package com.android.tools.idea.ui.properties.adapters;

import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.core.*;
import org.junit.Test;

import static com.android.tools.idea.ui.properties.BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY;
import static org.fest.assertions.Assertions.assertThat;

public class AdapterPropertyTest {
  @Test
  public void bindingStringToDoubleAdapterWorks() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringProperty doubleString = new StringValueProperty("0");
    DoubleProperty doubleValue = new DoubleValueProperty(20.0);

    // Defaults to 1 decimal point of precision
    bindings.bindTwoWay(new StringToDoubleAdapterProperty(doubleString), doubleValue);

    assertThat(doubleString.get()).isEqualTo("20.0");

    doubleString.set("100.5");

    assertThat(doubleValue.get()).isEqualTo(100.5);
  }

  @Test
  public void bindingStringToDoubleAdapterWithPrecisionWorks() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringProperty doubleString = new StringValueProperty("0");
    DoubleProperty doubleValue = new DoubleValueProperty(0.1234);

    bindings.bindTwoWay(new StringToDoubleAdapterProperty(doubleString, 3), doubleValue);

    assertThat(doubleString.get()).isEqualTo("0.123");
  }

  @Test
  public void bindingStringToIntAdapterWorks() throws Exception {
    BindingsManager bindings = new BindingsManager(INVOKE_IMMEDIATELY_STRATEGY);
    StringProperty intString = new StringValueProperty("0");
    IntProperty intValue = new IntValueProperty(1);

    bindings.bindTwoWay(new StringToIntAdapterProperty(intString), intValue);

    assertThat(intString.get()).isEqualTo("1");

    intString.set("-99");

    assertThat(intValue.get()).isEqualTo(-99);
  }
}