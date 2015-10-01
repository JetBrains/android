/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.logcat;

import com.android.ddmlib.Log;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatTimestamp;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFilter;
import com.intellij.diagnostic.logging.LogFilterListener;
import com.intellij.diagnostic.logging.LogFilterModel;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A filter which plugs into {@link LogConsoleBase} for custom logcat filtering.
 * This deliberately drops the custom pattern behaviour of LogFilterModel, replacing it with a new version that allows regex support.
 */
public abstract class AndroidLogFilterModel extends LogFilterModel {

  private final List<LogFilterListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private LogCatHeader myPrevHeader;
  private LogCatTimestamp myRejectBeforeTime;

  private boolean myFullMessageApplicable = false;
  private boolean myFullMessageApplicableByCustomFilter = false;
  private @Nullable Pattern myCustomPattern;

  protected List<AndroidLogFilter> myLogFilters = new ArrayList<AndroidLogFilter>();

  public AndroidLogFilterModel() {
    for (Log.LogLevel logLevel : Log.LogLevel.values()) {
      myLogFilters.add(new AndroidLogFilter(logLevel));
    }
  }

  // Implemented because it is abstract in the parent, but the functionality is no longer used.
  @Override
  public String getCustomFilter() {
    return "";
  }

  /**
   * This is called to enable regular expression filtering of log messages.
   * Replaces the customFilter mechanism.
   */
  public void updateCustomPattern(@Nullable Pattern pattern) {
    myCustomPattern = pattern;
    fireTextFilterChange();
  }

  public final void updateConfiguredFilter(@Nullable ConfiguredFilter filter) {
    setConfiguredFilter(filter);
    fireTextFilterChange();
  }

  protected void setConfiguredFilter(@Nullable ConfiguredFilter filter) {
  }

  @Nullable
  protected ConfiguredFilter getConfiguredFilter() {
    return null;
  }

  protected abstract void saveLogLevel(String logLevelName);

  @Override
  public final void addFilterListener(LogFilterListener listener) {
    myListeners.add(listener);
  }

  @Override
  public final void removeFilterListener(LogFilterListener listener) {
    myListeners.remove(listener);
  }

  /**
   * Once called, any logcat messages processed with a timestamp older than our most recent one
   * will be filtered out from now on.
   *
   * This is useful as a way to mark a time where you don't care about older messages. For example,
   * if you change your active filter and replay all logcat messages from the beginning, we will
   * skip over any that were originally reported before we called this method.
   *
   * This can also act as a lightweight clear, in case clearing the logcat buffer on the device
   * fails for some reason (which does happen). If you call this method, clear the console text
   * even without clearing the device's logcat buffer, and reprocess all messages, old messages
   * will be skipped.
   */
  public void beginRejectingOldMessages() {
    if (myPrevHeader == null) {
      return; // Haven't received any messages yet, so nothing to filter
    }

    myRejectBeforeTime = myPrevHeader.getTimestamp();
  }


  private void fireTextFilterChange() {
    for (LogFilterListener listener : myListeners) {
      listener.onTextFilterChange();
    }
  }

  private void fireFilterChange(LogFilter filter) {
    for (LogFilterListener listener : myListeners) {
      listener.onFilterStateChange(filter);
    }
  }

  private static Key getProcessOutputType(@NotNull Log.LogLevel level) {
    switch (level) {
      case VERBOSE:
        return AndroidLogcatConstants.VERBOSE;
      case INFO:
        return AndroidLogcatConstants.INFO;
      case DEBUG:
        return AndroidLogcatConstants.DEBUG;
      case WARN:
        return AndroidLogcatConstants.WARNING;
      case ERROR:
        return AndroidLogcatConstants.ERROR;
      case ASSERT:
        return AndroidLogcatConstants.ASSERT;
    }
    return ProcessOutputTypes.STDOUT;
  }

  @Override
  public final boolean isApplicable(String text) {
    // Not calling the super class version, it does not do what we want with regular expression matching
    if (myCustomPattern != null && !myCustomPattern.matcher(text).find()) return false;
    final LogFilter selectedLogLevelFilter = getSelectedLogLevelFilter();
    return selectedLogLevelFilter == null || selectedLogLevelFilter.isAcceptable(text);
  }

  public final boolean isApplicableByCustomFilter(String text) {
    final ConfiguredFilter configuredFilterName = getConfiguredFilter();
    if (configuredFilterName == null) {
      return true;
    }

    LogCatMessage message = AndroidLogcatFormatter.parseMessage(text);
    return configuredFilterName
      .isApplicable(message.getMessage(), message.getTag(), message.getAppName(), message.getPid(), message.getLogLevel());
  }

  @Override
  public final List<? extends LogFilter> getLogFilters() {
    return myLogFilters;
  }

  private static final class AndroidLogFilter extends LogFilter {
    final Log.LogLevel myLogLevel;

    private AndroidLogFilter(Log.LogLevel logLevel) {
      super(StringUtil.capitalize(logLevel.name().toLowerCase()));
      myLogLevel = logLevel;
    }

    @Override
    public boolean isAcceptable(String line) {
      LogCatMessage message = AndroidLogcatFormatter.tryParseMessage(line);
      return message != null && message.getLogLevel().getPriority() >= myLogLevel.getPriority();
    }
  }

  public abstract String getSelectedLogLevelName();

  @Nullable
  private LogFilter getSelectedLogLevelFilter() {
    final String filterName = getSelectedLogLevelName();
    if (filterName != null) {
      for (AndroidLogFilter logFilter : myLogFilters) {
        if (filterName.equals(logFilter.myLogLevel.name())) {
          return logFilter;
        }
      }
    }
    return null;
  }

  @Override
  public boolean isFilterSelected(LogFilter filter) {
    return filter == getSelectedLogLevelFilter();
  }

  @Override
  public void selectFilter(LogFilter filter) {
    if (!(filter instanceof AndroidLogFilter)) {
      return;
    }
    String newFilterName = ((AndroidLogFilter)filter).myLogLevel.name();
    if (!Comparing.equal(newFilterName, getSelectedLogLevelName())) {
      saveLogLevel(newFilterName);
      fireFilterChange(filter);
    }
  }

  @Override
  public void processingStarted() {
    myPrevHeader = null;
    myFullMessageApplicable = false;
    myFullMessageApplicableByCustomFilter = false;
  }

  @Override
  @NotNull
  public final MyProcessingResult processLine(String line) {

    Key key = ProcessOutputTypes.STDOUT;
    boolean isApplicable = false;

    LogCatMessage message = AndroidLogcatFormatter.tryParseMessage(line);
    if (message != null) {
      myPrevHeader = message.getHeader();
      myFullMessageApplicable = isApplicable(line);
      myFullMessageApplicableByCustomFilter = isApplicableByCustomFilter(line);

      key = getProcessOutputType(myPrevHeader.getLogLevel());

      isApplicable = myFullMessageApplicable && myFullMessageApplicableByCustomFilter;
      if (isApplicable && myRejectBeforeTime != null && myPrevHeader != null) {
        isApplicable = !myPrevHeader.getTimestamp().isBefore(myRejectBeforeTime);
      }
    }

    return new MyProcessingResult(key, isApplicable, null);
  }
}
