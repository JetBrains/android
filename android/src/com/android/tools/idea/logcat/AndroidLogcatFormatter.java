/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.logcat;

import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.intellij.diagnostic.logging.DefaultLogFormatter;
import java.time.ZoneId;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Class which handles parsing the output from logcat and reformatting it to its final form before
 * displaying it to the user.
 */
public final class AndroidLogcatFormatter extends DefaultLogFormatter {
  public static final String TAG_PATTERN_GROUP_NAME = "tag";
  public static final Pattern TAG_PATTERN = Pattern.compile(" [VDIWEA]/(?<tag>.*?): ");


  @NotNull private final ZoneId myTimeZone;
  private final AndroidLogcatPreferences myPreferences;

  public AndroidLogcatFormatter(@NotNull ZoneId timeZone, @NotNull AndroidLogcatPreferences preferences) {
    myTimeZone = timeZone;
    myPreferences = preferences;
  }

  /**
   * Parse a JSON encoded {@link LogCatMessage} and format it based on {@link AndroidLogcatPreferences#LOGCAT_HEADER_FORMAT}
   */
  @NotNull
  @Override
  public String formatMessage(@NotNull String message) {
    return formatMessage(LogcatJson.fromJson(message));
  }

  /**
   * Parse a {@link LogCatMessage} and format it based on {@link AndroidLogcatPreferences#LOGCAT_HEADER_FORMAT}
   */
  @NotNull
  String formatMessage(@NotNull LogCatMessage message) {
    return myPreferences.LOGCAT_HEADER_FORMAT.formatMessage(message, myTimeZone);
  }

  /**
   * Parse a {@link LogCatMessage} and format it
   */
  @NotNull
  public String formatMessage(@NotNull LogcatHeaderFormat format, @NotNull LogCatHeader header, @NotNull String message) {
    return format.formatMessage(new LogCatMessage(header, message), myTimeZone);
  }
}
