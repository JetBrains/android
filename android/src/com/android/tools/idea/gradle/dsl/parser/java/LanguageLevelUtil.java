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
package com.android.tools.idea.gradle.dsl.parser.java;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LanguageLevelUtil {
  /**
   * Parses {@link LanguageLevel} from Gradle JavaVersion representation, e.g. JavaVersion.VERSION_1_X, VERSION_1_X, 1.7, '1.7'
   */
  @Nullable
  public static LanguageLevel parseFromGradleString(@NotNull String gradleString) {
    String digitalVersion; // in the format of 1.3, 1.4 etc so that LanguageLevel could parse
    if (gradleString.startsWith("JavaVersion.VERSION_")) {
      digitalVersion = gradleString.substring("JavaVersion.VERSION_".length()).replace('_', '.');
    } else if (gradleString.startsWith("VERSION_")) {
      digitalVersion = gradleString.substring("VERSION_".length()).replace('_', '.');
    } else if (gradleString.startsWith("'") || gradleString.startsWith("\"")) {
      digitalVersion = gradleString.substring(1, gradleString.length() - 1);
    } else {
      digitalVersion = gradleString;
    }
    return LanguageLevel.parse(digitalVersion);
  }

  /**
   * Converts {@code languageLevel} to Gradle JavaVersion representation use the same format as {@code sampleGradleString}.
   */
  @NotNull
  public static String convertToGradleString(@NotNull LanguageLevel languageLevel, @Nullable String sampleGradleString) {
    String underscoreVersion = languageLevel.name().substring("JDK_".length()); // in the format of 1_5, 1_6, etc
    String dotVersion = underscoreVersion.replace('_', '.');
    if (sampleGradleString != null) {
      if (sampleGradleString.startsWith("JavaVersion.VERSION_")) {
        return "JavaVersion.VERSION_" + underscoreVersion;
      }
      else if (sampleGradleString.startsWith("VERSION_")) {
        return "VERSION_" + underscoreVersion;
      }
      else if (sampleGradleString.startsWith("'")) {
        return "'" + dotVersion + "'";
      }
      else if (sampleGradleString.startsWith("\"")) {
        return "\"" + dotVersion + "\"";
      }
    }
    return dotVersion;
  }
}
