/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.util;

import org.fest.swing.util.TextMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

import static org.fest.swing.util.Strings.match;

public class NotMatchingPatternMatcher implements TextMatcher {
  @NotNull private final Pattern myPattern;

  public NotMatchingPatternMatcher(@NotNull Pattern pattern) {
    myPattern = pattern;
  }

  @Override
  public boolean isMatching(String text) {
    return !match(myPattern, text);
  }

  @Override
  @NotNull
  public String description() {
    return "not matching pattern";
  }

  @Override
  @NotNull
  public String formattedValues() {
    return myPattern.pattern();
  }
}
