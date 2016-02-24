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
public final class AndroidLogcatFilter {
  @NotNull private final String myName;
  @Nullable private final Pattern myMessagePattern;
  @Nullable private final Pattern myTagPattern;
  @Nullable private final Pattern myPkgNamePattern;
  @Nullable private final String myPid;
  @Nullable private final Log.LogLevel myLogLevel;

  public static final class Builder {
    @NotNull private final String myName;
    @Nullable private Pattern myMessagePattern;
    @Nullable private Pattern myTagPattern;
    @Nullable private Pattern myPkgNamePattern;
    @Nullable private String myPid;
    @Nullable private Log.LogLevel myLogLevel;

    public Builder(@NotNull String name) {
      myName = name;
    }

    public Builder setMessagePattern(@Nullable Pattern messagePattern) {
      myMessagePattern = messagePattern;
      return this;
    }

    public Builder setTagPattern(@Nullable Pattern tagPattern) {
      myTagPattern = tagPattern;
      return this;
    }

    public Builder setPackagePattern(@Nullable Pattern pkgNamePattern) {
      myPkgNamePattern = pkgNamePattern;
      return this;
    }

    public Builder setPid(@Nullable String pid) {
      myPid = pid;
      return this;
    }

    public Builder setPid(@Nullable Integer pid) {
      return setPid(pid != null ? pid.toString() : null);
    }

    public Builder setLogLevel(@Nullable Log.LogLevel logLevel) {
      myLogLevel = logLevel;
      return this;
    }

    @NotNull
    public AndroidLogcatFilter build() {
      return new AndroidLogcatFilter(myName, myMessagePattern, myTagPattern, myPkgNamePattern, myPid, myLogLevel);
    }
  }

  /**
   * Use a {@link Builder} to construct a filter.
   */
  private AndroidLogcatFilter(@NotNull String name,
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
  public static AndroidLogcatFilter compile(@NotNull PersistentAndroidLogFilters.FilterData filterData, @NotNull String name) {

    Pattern logMessagePattern = RegexFilterComponent.pattern(filterData.getLogMessagePattern(), filterData.getLogMessageIsRegex());
    Pattern logTagPattern = RegexFilterComponent.pattern(filterData.getLogTagPattern(), filterData.getLogTagIsRegex());
    Pattern pkgNamePattern = RegexFilterComponent.pattern(filterData.getPackageNamePattern(), filterData.getPackageNameIsRegex());

    final String pid = filterData.getPid();

    Log.LogLevel logLevel = null;
    final String logLevelStr = filterData.getLogLevel();
    if (logLevelStr != null && logLevelStr.length() > 0) {
      logLevel = Log.LogLevel.getByString(logLevelStr);
    }

    return new AndroidLogcatFilter(name, logMessagePattern, logTagPattern, pkgNamePattern, pid, logLevel);
  }

}
