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
package com.android.tools.idea.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class Icons {
  private Icons() {
  }

  /**
   * @return a new LayeredIcon with icon2 at 0.8 scale and shifted to the south east corner of icon1
   */
  @NotNull
  public static Icon newLayeredIcon(@NotNull Icon icon1, @NotNull ScalableIcon icon2) {
    LayeredIcon layeredIcon = new LayeredIcon(2);

    layeredIcon.setIcon(icon1, 0);
    layeredIcon.setIcon(icon2.scale(0.8F), 1, SwingConstants.SOUTH_EAST);

    return layeredIcon;
  }
}
