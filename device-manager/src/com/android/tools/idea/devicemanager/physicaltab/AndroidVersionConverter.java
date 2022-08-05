/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersion.AndroidVersionException;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.Converter;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AndroidVersionConverter extends Converter<AndroidVersion> {
  private static final @NotNull Pattern PATTERN = Pattern.compile("androidversion:(.+):(.+):(.+):(.+)");

  @VisibleForTesting
  AndroidVersionConverter() {
  }

  @Override
  public @NotNull AndroidVersion fromString(@NotNull String string) {
    Matcher matcher = PATTERN.matcher(string);

    if (matcher.matches()) {
      return fromMatchResult(matcher);
    }

    try {
      return new AndroidVersion(string);
    }
    catch (AndroidVersionException exception) {
      Logger.getInstance(AndroidVersionConverter.class).warn(exception);
      return AndroidVersion.DEFAULT;
    }
  }

  private static @NotNull AndroidVersion fromMatchResult(@NotNull MatchResult result) {
    try {
      return new AndroidVersion(Integer.parseInt(result.group(1)),
                                parseString(result.group(2)),
                                parseInteger(result.group(3)),
                                parseBoolean(result.group(4)));
    }
    catch (IllegalArgumentException exception) {
      Logger.getInstance(AndroidVersionConverter.class).warn(exception);
      return AndroidVersion.DEFAULT;
    }
  }

  private static @Nullable String parseString(@NotNull String string) {
    if (string.equals("null")) {
      return null;
    }

    return string;
  }

  private static @Nullable Integer parseInteger(@NotNull String string) {
    if (string.equals("null")) {
      return null;
    }

    return Integer.parseInt(string);
  }

  private static boolean parseBoolean(@NotNull String string) {
    switch (string) {
      case "false":
        return false;
      case "true":
        return true;
      default:
        throw new IllegalArgumentException(string);
    }
  }

  @Override
  public @NotNull String toString(@NotNull AndroidVersion version) {
    return "androidversion:" +
           version.getApiLevel() +
           ':' +
           version.getCodename() +
           ':' +
           version.getExtensionLevel() +
           ':' +
           version.isBaseExtension();
  }
}
