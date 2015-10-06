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
package com.android.tools.idea.logcat;

import com.android.ddmlib.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * A filter which can reject lines of logcat output based on user configured patterns.
 */
final class ConfiguredFilter {
  @NotNull private final String myName;
  @Nullable private final Pattern myMessagePattern;
  @Nullable private final Pattern myTagPattern;
  @Nullable private final Pattern myPkgNamePattern;
  @Nullable private final String myPid;
  @Nullable private final Log.LogLevel myLogLevel;

  private ConfiguredFilter(@NotNull String name,
                           @Nullable Pattern messagePattern,
                           @Nullable Pattern tagPattern,
                           @Nullable Pattern pkgNamePattern,
                           @Nullable String pid,
                           @Nullable Log.LogLevel logLevel) {
    myName = name;
    myMessagePattern = messagePattern;
    myTagPattern = tagPattern;
    myPkgNamePattern = pkgNamePattern;
    myPid = pid;
    myLogLevel = logLevel;
  }

  public boolean isApplicable(String message, String tag, String pkg, int pid, Log.LogLevel logLevel) {

    if (myMessagePattern != null && (message == null || !myMessagePattern.matcher(message).find())) {
      return false;
    }

    if (myTagPattern != null && (tag == null || !myTagPattern.matcher(tag).find())) {
      return false;
    }

    if (myPkgNamePattern != null && (pkg == null || !myPkgNamePattern.matcher(pkg).find())) {
      return false;
    }

    if (myPid != null && myPid.length() > 0 && !myPid.equals(Integer.toString(pid))) {
      return false;
    }

    if (myLogLevel != null && (logLevel == null || logLevel.getPriority() < myLogLevel.getPriority())) {
      return false;
    }
    
    return true;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public static ConfiguredFilter compile(@NotNull AndroidConfiguredLogFilters.FilterEntry entry, @NotNull String name) {

    Pattern logMessagePattern = RegexFilterComponent.pattern(entry.getLogMessagePattern(), entry.getLogMessageIsRegex());
    Pattern logTagPattern = RegexFilterComponent.pattern(entry.getLogTagPattern(), entry.getLogTagIsRegex());
    Pattern pkgNamePattern = RegexFilterComponent.pattern(entry.getPackageNamePattern(), entry.getPackageNameIsRegex());

    final String pid = entry.getPid();

    Log.LogLevel logLevel = null;
    final String logLevelStr = entry.getLogLevel();
    if (logLevelStr != null && logLevelStr.length() > 0) {
      logLevel = Log.LogLevel.getByString(logLevelStr);
    }

    return new ConfiguredFilter(name, logMessagePattern, logTagPattern, pkgNamePattern,
                                pid, logLevel);

  }

}
