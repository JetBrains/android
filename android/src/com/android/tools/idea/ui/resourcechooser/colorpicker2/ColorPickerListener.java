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
package com.android.tools.idea.ui.resourcechooser.colorpicker2;

import com.intellij.ui.picker.ColorListener;
import java.awt.Color;

/**
 * Interface for receive callback from ColorPicker
 *
 * @see ColorPickerBuilder
 */
public interface ColorPickerListener extends ColorListener {

  /**
   * Callback when color picker is picking color but not commit yet.<br>
   * For example, dragging the mouse on color slider means the color is been picking, but the actual picked color is decided when the
   * releasing the mouse.
   */
  default void pickingColorChanged(Color color, Object source) {
  }
}
