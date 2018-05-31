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
package com.android.tools.idea.util;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Utility class for removing default ignorable characters.
 *
 * These characters can be ignored by default in rendering unless they are
 * explicitly supported.
 */
public class DefaultIgnorable {
  // From http://www.unicode.org/L2/L2002/02368-default-ignorable.pdf Section 5
  private static final String CHARACTER_PATTERN = "[" +
                                                  "\u0000-\u0008" + // Control codes
                                                  "\u000E-\u001F" + // Control codes
                                                  "\u007F-\u0084" + // Control codes
                                                  "\u0086-\u009F" + // Control codes
                                                  "\u06DD" +        // ARABIC END OF AYAH
                                                  "\u070F" +        // SYRIAC ABBREVIATION MARK
                                                  "\u180B-\u180D" + // MONGOLIAN FREE VARIATION SELECTORS
                                                  "\u180E" +        // MONGOLIAN VOWEL SEPARATOR
                                                  "\u200C-\u200F" + // ZERO WIDTH NON-JOINER..RIGHT-TO-LEFT MARK
                                                  "\u202A-\u202E" + // LEFT-TO-RIGHT EMBEDDING..RIGHT-TO-LEFT OVERRIDE
                                                  "\u2060-\u206F" + // WORD JOINER..NOMINAL DIGIT SHAPES
                                                  "\uFEFF" +        // ZERO WIDTH NO-BREAK SPACE
                                                  "\uFFF0-\uFFFB" + // Format Controls
                                                  "]";

  private static Pattern ourCharacterPattern;

  /**
   * Remove all default ignorable characters in the specified string.
   *
   * Use with caution. This should only be called if we are certain that the
   * characters are not supported. We know that at least some of these characters
   * gives trouble for Html rendered in a TextArea on Windows.
   */
  @NotNull
  public static String removeDefaultIgnorable(@NotNull String text) {
    return getCharacterPattern().matcher(text).replaceAll("");
  }

  private static synchronized Pattern getCharacterPattern() {
    if (ourCharacterPattern == null) {
      ourCharacterPattern = Pattern.compile(CHARACTER_PATTERN);
    }
    return ourCharacterPattern;
  }
}
