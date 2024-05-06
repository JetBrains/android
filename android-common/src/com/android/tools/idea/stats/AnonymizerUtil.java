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
package com.android.tools.idea.stats;

import com.android.tools.analytics.Anonymizer;
import com.android.tools.idea.log.LogWrapper;
import com.android.utils.ILogger;
import org.jetbrains.annotations.NotNull;

public class AnonymizerUtil {
  private static final String ANONYMIZATION_ERROR = "*ANONYMIZATION_ERROR*";
  public static final ILogger LOGGER = new LogWrapper(AnonymizerUtil.class);

  /**
   * Like {@link Anonymizer#anonymize(String)} but maintains its own IntelliJ logger and upon error
   * reports to logger and returns ANONYMIZATION_ERROR instead of returning null.
   */
  @NotNull
  public static String anonymizeUtf8(@NotNull String value) {
    String result = Anonymizer.anonymize(value);
    if (result == null) {
      LOGGER.warning("Unable to read anonymization settings, not reporting any values");
      return ANONYMIZATION_ERROR;
    }
    return result;
  }
}