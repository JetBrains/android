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

  /**
   * Uses an integer {@link NumberFormat} for the current locale to format a number.
   */
  @NotNull
  public static String formatInteger(@NotNull Number number) {
    return INTEGER_FORMAT.format(number);
  }
}
