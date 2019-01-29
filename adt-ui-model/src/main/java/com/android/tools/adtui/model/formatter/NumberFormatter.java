/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.model.formatter;

import org.jetbrains.annotations.NotNull;

import java.text.Format;
import java.text.NumberFormat;

public class NumberFormatter {

  private static final Format INTEGER_FORMAT = NumberFormat.getIntegerInstance();
  private static final String[] FILE_SIZE_UNITS = new String[]{"B", "KB", "MB", "GB", "TB", "PB", "EB"};
  private static final int FILE_SIZE_MULTIPLIER = 1024;

  /**
   * Uses an integer {@link NumberFormat} for the current locale to format a number.
   */
  @NotNull
  public static String formatInteger(@NotNull Number number) {
    return INTEGER_FORMAT.format(number);
  }

  /**
   * Formats the given file size to be 1 decimal place at most, for example, "5.0 B" or "123 B".
   */
  @NotNull
  public static String formatFileSize(long sizeInBytes) {
    double result = sizeInBytes;
    String unit = FILE_SIZE_UNITS[0];
    for (int i = 1; i < FILE_SIZE_UNITS.length; i++) {
      if (result < FILE_SIZE_MULTIPLIER) {
        break;
      }
      result /= FILE_SIZE_MULTIPLIER;
      unit = FILE_SIZE_UNITS[i];
    }
    String decimalFormat = result < 100 ? "%.1f" : "%.0f";
    return String.format(decimalFormat + " %s", result, unit);
  }
}
