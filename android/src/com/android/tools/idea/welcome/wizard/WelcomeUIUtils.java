/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard;

import com.android.sdklib.devices.Storage;
import com.google.common.collect.ImmutableMap;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.TextAttribute;
import java.math.RoundingMode;
import java.text.NumberFormat;

/**
 * Functions to style controls for a consistent user UI
 */
public final class WelcomeUIUtils {

  private WelcomeUIUtils() {
    // No instantiation
  }

  /**
   * @return string describing the size
   */
  public static String getSizeLabel(long size) {
    Storage.Unit[] values = Storage.Unit.values();
    Storage.Unit unit = values[values.length - 1];
    for (int i = values.length - 2; unit.getNumberOfBytes() > size && i >= 0; i--) {
      unit = values[i];
    }
    final double space = size * 1.0 / unit.getNumberOfBytes();
    String formatted = roundToNumberOfDigits(space, 3);
    return String.format("%s %s", formatted, unit.getDisplayValue());
  }

  /**
   * <p>Returns a string that rounds the number so number of
   * integer places + decimal places is less or equal to maxDigits.</p>
   * <p>Number will not be truncated if it has more integer digits
   * then macDigits</p>
   */
  private static String roundToNumberOfDigits(double number, int maxDigits) {
    int multiplier = 1, digits;
    for (digits = maxDigits; digits > 0 && number > multiplier; digits--) {
      multiplier *= 10;
    }
    NumberFormat numberInstance = NumberFormat.getNumberInstance();
    numberInstance.setGroupingUsed(false);
    numberInstance.setRoundingMode(RoundingMode.HALF_UP);
    numberInstance.setMaximumFractionDigits(digits);
    return numberInstance.format(number);
  }

  /**
   * Appends details to the message if they are not empty.
   */
  public static String getMessageWithDetails(@NotNull String message, @Nullable String details) {
    if (StringUtil.isEmptyOrSpaces(details)) {
      return message + ".";
    }
    else {
      String dotIfNeeded = details.trim().endsWith(".") ? "" : ".";
      return message + ": " + details + dotIfNeeded;
    }
  }
}
