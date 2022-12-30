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

package com.android.tools.idea.avdmanager;

import org.jetbrains.annotations.NotNull;

public final class AvdNameVerifier {

  private static final String ALLOWED_CHARS          = "0-9a-zA-Z-_. ()";
  private static final String ALLOWED_CHARS_READABLE = "a-z A-Z 0-9 . _ - ( )";


  public static boolean isValid(@NotNull String candidateName) {
    // The name is valid if it has one or more allowed characters
    // and only allowed characters
    return candidateName.matches("^[" + ALLOWED_CHARS + "]+$");
  }

  @NotNull
  public static String stripBadCharacters(@NotNull String candidateName) {
    // Remove any invalid characters.
    return candidateName.replaceAll("[^" + ALLOWED_CHARS + "]", " ");
  }

  @NotNull
  public static String stripBadCharactersAndCollapse(@NotNull String candidateName) {
    // Remove any invalid characters. Remove leading and trailing spaces. Replace consecutive
    // spaces, parentheses, and underscores by a single underscore.
    return candidateName.replaceAll("[^" + ALLOWED_CHARS + "]", " ").trim().replaceAll("[ ()_]+", "_");
  }

  @NotNull
  public static String humanReadableAllowedCharacters() {
    return ALLOWED_CHARS_READABLE;
  }

}
