/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.adapters.AdapterProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.ui.SliderValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import javax.swing.JSlider;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

public class SliderUtils {

  /**
   * Provides 2 way binding between the slider value and the text value from a text field.
   * @param manager A BindingsManager for a dialog.
   * @param slider The slider control.
   * @param sliderTextValue The JTextField with a text value.
   * @return An IntProperty for the value in the slider.
   */
  public static IntProperty bindTwoWay(@NotNull BindingsManager manager, @NotNull JSlider slider, @NotNull JTextField sliderTextValue) {
    IntProperty sliderPercent = new SliderValueProperty(slider);
    manager.bindTwoWay(
      sliderPercent,
      new AdapterProperty<>(new TextProperty(sliderTextValue), sliderPercent.get()) {
        @Override
        protected @NotNull Integer convertFromSourceType(@NotNull String value) {
          try {
            return Integer.parseInt(value);
          }
          catch (NumberFormatException ex) {
            return 100;
          }
        }

        @Override
        protected @NotNull String convertFromDestType(@NotNull Integer value) {
          return value.toString();
        }
      }
    );
    return sliderPercent;
  }

  /**
   * Create a Validator for String values representing a slider value.
   * @param slider The slider control.
   * @param name A name associated with the property the slider controls.
   * @return A Validator for a String value that should be an integer in the valid range of the slider control.
   */
  public static Validator<String> inRange(@NotNull JSlider slider, @NotNull String name) {
    return new Validator<>() {
      @Override
      public @NotNull Result validate(String value) {
        Integer intValue = null;
        try {
          intValue = Integer.parseInt(value);
        }
        catch (NumberFormatException ignored) {
        }
        if (intValue != null) {
          if (intValue < slider.getMinimum() || intValue > slider.getMaximum()) {
            intValue = null;
          }
        }
        if (intValue != null) {
          return Result.OK;
        }
        else {
          String message = name + " should be an integer between " + slider.getMinimum() + " and " + slider.getMaximum();
          return Result.fromNullableMessage(message);
        }
      }
    };
  }
}
