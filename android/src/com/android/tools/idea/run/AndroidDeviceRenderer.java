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
package com.android.tools.idea.run;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AndroidDeviceRenderer extends ColoredListCellRenderer<AndroidDevice> {
  private final LaunchCompatibiltyChecker myCompatibilityChecker;

  public AndroidDeviceRenderer(@NotNull LaunchCompatibiltyChecker checker) {
    myCompatibilityChecker = checker;
  }

  @Override
  protected void customizeCellRenderer(JList list, AndroidDevice device, int index, boolean selected, boolean hasFocus) {
    if (myCompatibilityChecker.validate(device).isCompatible() == ThreeState.NO) {
      append("[not compatible] ", SimpleTextAttributes.ERROR_ATTRIBUTES);
    }

    device.renderName(this);
  }
}
