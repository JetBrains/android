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
package com.android.tools.idea.logcat;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.ExpressionFilterManager.ExpressionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A basic implementation of {@link AndroidLogcatFilter} which does exclusive matching against
 * multiple predicate patterns (all non-null predicates must match).
 */
public final class DefaultAndroidLogcatFilter implements AndroidLogcatFilter {
  @NotNull private static final ExpressionFilterManager EXPRESSION_FILTER_MANAGER = new ExpressionFilterManager();

  @NotNull private final String myName;
  @Nullable private final Pattern myMessagePattern;
  @Nullable private final Pattern myTagPattern;
  @Nullable private final Pattern myPkgNamePattern;
  @Nullable private final String myPid;
  @Nullable private final LogLevel myLogLevel;
  @Nullable private String myExpression;


  /**
   * Use {@link #compile} to create an instance.
   */
  private DefaultAndroidLogcatFilter(@NotNull String name,
                                     @Nullable Pattern messagePattern,
                                     @Nullable Pattern tagPattern,
                                     @Nullable Pattern pkgNamePattern,
                                     @Nullable String pid,
                                     @Nullable LogLevel logLevel,
                                     @Nullable String expression) {
    myName = name;
    myMessagePattern = messagePattern;
    myTagPattern = tagPattern;
    myPkgNamePattern = pkgNamePattern;
    myPid = pid;
    myLogLevel = logLevel;
    myExpression = expression;
  }

  @Override
  public boolean isApplicable(@NotNull LogCatMessage logCatMessage) {
    LogCatHeader header = logCatMessage.getHeader();
    String message = logCatMessage.getMessage();
    LogLevel logLevel = header.getLogLevel();
    String tag = header.getTag();
    String appName = header.getAppName();
    int pid = header.getPid();

    if (StringUtil.isNotEmpty(myExpression)) {
      try {
        if (!EXPRESSION_FILTER_MANAGER.eval(myExpression, logCatMessage)) {
          return false;
        }
      }
      catch (ExpressionException e) {
        // Don't fail on invalid expression or other exceptions.
        Logger.getInstance(getClass()).warn("Error evaluating expression: " + myExpression);
        myExpression = null;
      }
    }

    if (myLogLevel != null) {
      if (logLevel.getPriority() < myLogLevel.getPriority()) {
        return false;
      }
    }

    if (myMessagePattern != null) {
      if (!myMessagePattern.matcher(message).find()) {
        return false;
      }
    }

    if (myTagPattern != null) {
      if (!myTagPattern.matcher(tag).find()) {
        return false;
      }
    }

    if (myPkgNamePattern != null) {
      if (!myPkgNamePattern.matcher(appName).find()) {
        return false;
      }
    }

    // TODO: If we're always checking against an int pid anyway, why let myPid be a string?
    //noinspection RedundantIfStatement
    if ((myPid != null && !myPid.isEmpty()) && !myPid.equals(Integer.toString(pid))) {
      return false;
    }

    return true;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public static DefaultAndroidLogcatFilter compile(@NotNull PersistentAndroidLogFilters.FilterData filterData, @NotNull String name) {

    Pattern logMessagePattern = RegexFilterComponent.pattern(filterData.getLogMessagePattern(), filterData.getLogMessageIsRegex());
    Pattern logTagPattern = RegexFilterComponent.pattern(filterData.getLogTagPattern(), filterData.getLogTagIsRegex());
    Pattern pkgNamePattern = RegexFilterComponent.pattern(filterData.getPackageNamePattern(), filterData.getPackageNameIsRegex());

    final String expression = filterData.getExpression();
    final String pid = filterData.getPid();

    LogLevel logLevel = null;
    final String logLevelStr = filterData.getLogLevel();
    if (logLevelStr != null && !logLevelStr.isEmpty()) {
      logLevel = LogLevel.getByString(logLevelStr);
    }

    return new DefaultAndroidLogcatFilter(name, logMessagePattern, logTagPattern, pkgNamePattern, pid, logLevel, expression);
  }
}
