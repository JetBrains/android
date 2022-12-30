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

import java.text.Format;
import java.text.NumberFormat;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public final class NumberFormatter {

  private static final Format INTEGER_FORMAT = NumberFormat.getIntegerInstance();
  private static final String[] FILE_SIZE_UNITS = {"B", "KB", "MB", "GB", "TB", "PB", "EB"};
  private static final int FILE_SIZE_MULTIPLIER = 1024;
  private static final String[] FREQUENCY_UNITS = {"kHz", "MHz", "GHz"};
  private static final int FREQUENCY_MULTIPLIER = 1000;

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
    return formatNumberWithUnit(sizeInBytes, FILE_SIZE_MULTIPLIER, FILE_SIZE_UNITS);
  }

  /**
   * Formats the given frequency to be 1 decimal place at most, for example, "5.0 kHz" or 123 MHz".
   */
  @NotNull
  public static String formatFrequency(long frequencyInKhz) {
    return formatNumberWithUnit(frequencyInKhz, FREQUENCY_MULTIPLIER, FREQUENCY_UNITS);
  }

  private static String formatNumberWithUnit(long number, int multiplier, String[] units) {
    double result = number;
    String unit = units[0];
    for (int i = 1; i < units.length; i++) {
      if (result < multiplier) {
        break;
      }
      result /= multiplier;
      unit = units[i];
    }
    String decimalFormat = result < 100 ? "%.1f" : "%.0f";
    return String.format(Locale.US, decimalFormat + " %s", result, unit);
  }
}
