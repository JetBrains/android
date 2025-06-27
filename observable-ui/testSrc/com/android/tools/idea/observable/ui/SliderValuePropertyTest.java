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
package com.android.tools.idea.observable.ui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.observable.CountListener;
import javax.swing.JSlider;
import org.junit.Test;

public class SliderValuePropertyTest {
  @Test
  public void testSliderValueProperty() {
    JSlider slider = new JSlider(0, 100, 50);
    SliderValueProperty sliderValue = new SliderValueProperty(slider);
    CountListener listener = new CountListener();
    sliderValue.addListener(listener);

    assertThat(sliderValue.get()).isEqualTo(50);
    assertThat(listener.getCount()).isEqualTo(0);

    slider.setValue(90);
    assertThat(sliderValue.get()).isEqualTo(90);
    assertThat(listener.getCount()).isEqualTo(1);

    sliderValue.set(10);
    assertThat(slider.getValue()).isEqualTo(10);
    assertThat(listener.getCount()).isEqualTo(2);
  }
}