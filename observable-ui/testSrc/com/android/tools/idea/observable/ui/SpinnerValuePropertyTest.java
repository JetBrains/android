/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.observable.ui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.observable.CountListener;
import com.intellij.ui.JBIntSpinner;
import org.junit.Test;

public class SpinnerValuePropertyTest {

  @Test
  public void testSliderValueProperty() throws Exception {
    JBIntSpinner spinner = new JBIntSpinner(111, Integer.MIN_VALUE, Integer.MAX_VALUE);
    SpinnerValueProperty spinnerValue = new SpinnerValueProperty(spinner);
    CountListener listener = new CountListener();
    spinnerValue.addListener(listener);

    assertThat(spinnerValue.get()).isEqualTo(111);
    assertThat(listener.getCount()).isEqualTo(0);

    spinner.setNumber(222);
    assertThat(spinnerValue.get()).isEqualTo(222);
    assertThat(listener.getCount()).isEqualTo(1);

    spinner.setNumber(333);
    assertThat(spinnerValue.get()).isEqualTo(333);
    assertThat(listener.getCount()).isEqualTo(2);

    spinnerValue.set(444);
    assertThat(spinner.getValue()).isEqualTo(444);
    assertThat(spinner.getNumber()).isEqualTo(444);
    assertThat(listener.getCount()).isEqualTo(3);
  }
}