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
package com.android.tools.adtui.model.formatter;

import static com.intellij.icons.AllIcons.Javaee.Local;

import java.text.DecimalFormat;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Class that formats a {@link SingleUnitAxisFormatter} as a percentage of the incoming value. The value will be normalized by the global
 * range producing an output between -100% and 100%.
 * Example output:
 *  Value: 0, GlobalRange: 2000 = "0%"
 *  Value: 3333, GlobalRange: 100000 = "33.33%"
 *  Value: 100, GlobalRange: 100 = "100%"
 */
public class PercentAxisFormatter extends SingleUnitAxisFormatter {
  public PercentAxisFormatter(int maxMinorTicks, int maxMajorTicks) {
    super(maxMinorTicks, maxMajorTicks, 1, "%");
  }

  @NotNull
  @Override
  public String getFormattedString(double globalRange, double value, boolean includeUnit) {
    DecimalFormat decimalFormat = new DecimalFormat("#.##");
    // Normalize value, and include unit if required.
    return decimalFormat.format(value / globalRange * 100) + (includeUnit ? "%" : "");
  }
}
