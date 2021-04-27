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
import com.google.common.collect.ImmutableList;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFilter;
import com.intellij.diagnostic.logging.LogFilterListener;
import com.intellij.diagnostic.logging.LogFilterModel;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A filter which plugs into {@link LogConsoleBase} for custom logcat filtering.
 * This deliberately drops the custom pattern behaviour of LogFilterModel, replacing it with a new version that allows regex support.
 */
final class AndroidLogFilterModel extends LogFilterModel {

  private final List<LogFilterListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Nullable private LogCatHeader myPrevHeader;
  @Nullable private LogCatHeader myRejectBeforeHeader;

  /**
   * A regex which is tested against unprocessed log input. Contrast with
   * {@link #myConfiguredFilter} which, if non-null, does additional filtering on input after
   * it has been parsed and broken up into component parts.
   * This is normally set by the Android Monitor search bar.
   */
  @Nullable private Pattern myCustomPattern;

  @Nullable private AndroidLogcatFilter myConfiguredFilter;

  @NotNull private final ImmutableList<AndroidLogLevelFilter> myLogLevelFilters;
  @NotNull private final AndroidLogcatPreferences myPreferences;
  @NotNull private final AndroidLogcatFormatter myFormatter;

  AndroidLogFilterModel(@NotNull AndroidLogcatFormatter formatter, @NotNull AndroidLogcatPreferences preferences) {
    ImmutableList.Builder<AndroidLogLevelFilter> builder = ImmutableList.builder();
    for (Log.LogLevel logLevel : Log.LogLevel.values()) {
      builder.add(new AndroidLogLevelFilter(logLevel));
    }
    myLogLevelFilters = builder.build();
    myPreferences = preferences;
    myFormatter = formatter;
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

  public final void updateLogcatFilter(@Nullable AndroidLogcatFilter filter) {
    myPreferences.TOOL_WINDOW_CONFIGURED_FILTER = filter != null ? filter.getName() : "";
    myConfiguredFilter = filter;
    fireTextFilterChange();
  }

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
   * <p>
   * This is useful as a way to mark a time where you don't care about older messages. For example,
   * if you change your active filter and replay all logcat messages from the beginning, we will
   * skip over any that were originally reported before we called this method.
   * <p>
   * This can also act as a lightweight clear, in case clearing the logcat buffer on the device
   * fails for some reason (which does happen). If you call this method, clear the console text
   * even without clearing the device's logcat buffer, and reprocess all messages, old messages
   * will be skipped.
   */
  public void beginRejectingOldMessages() {
    if (myPrevHeader == null) {
      return; // Haven't received any messages yet, so nothing to filter
    }

    myRejectBeforeHeader = myPrevHeader;
  }


  private void fireTextFilterChange() {
    for (LogFilterListener listener : myListeners) {
      listener.onTextFilterChange();
    }
  }

  private void fireFilterChange(@NotNull LogFilter filter) {
    for (LogFilterListener listener : myListeners) {
      listener.onFilterStateChange(filter);
    }
  }

  @Override
  public final boolean isApplicable(@Nullable String line) {
    // Probably not used. We use isApplicable(LogCatMessage) ourselves and it looks like LogConsoleBase doesn't use this.
    // Just in case, parse the json and evaluate.
    if (line == null) {
      return false;
    }
    return isApplicable(LogcatJson.fromJson(line));
  }

  private boolean isApplicable(@NotNull LogCatMessage logCatMessage) {
    if (myCustomPattern != null && !myCustomPattern.matcher(myFormatter.formatMessage(logCatMessage)).find()) {
      return false;
    }
    final AndroidLogLevelFilter selectedLogLevelFilter = getSelectedLogLevelFilter();
    return selectedLogLevelFilter == null || selectedLogLevelFilter.isAcceptable(logCatMessage);
  }


  // Checks if the log message (with header stripped) matches the active filter, if set. Note that
  // this should ONLY be called if myPrevHeader was already set (which is how the filter will test
  // against header information).
  private boolean isApplicableByConfiguredFilter(@NotNull LogCatMessage logCatMessage) {
    if (myConfiguredFilter == null) {
      return true;
    }
    LogCatHeader header = logCatMessage.getHeader();
    return myConfiguredFilter
      .isApplicable(logCatMessage.getMessage(), header.getTag(), header.getAppName(), header.getPid(), header.getLogLevel());
  }

  @Override
  public final List<? extends LogFilter> getLogFilters() {
    return myLogLevelFilters;
  }

  private static final class AndroidLogLevelFilter extends LogFilter {
    final Log.LogLevel myLogLevel;

    private AndroidLogLevelFilter(Log.LogLevel logLevel) {
      super(StringUtil.capitalize(logLevel.getStringValue()));
      myLogLevel = logLevel;
    }

    @Override
    public boolean isAcceptable(@Nullable String line) {
      // Probably not used. We expose these Log Filters to LogConsoleBase through getLogFilters but it looks like it uses them only for
      // presenting the combo box. Just in case, we'll parse the json and evaluate.
      if (line == null) {
        return false;
      }
      return isAcceptable(LogcatJson.fromJson(line));
    }

    public boolean isAcceptable(@NotNull LogCatMessage logCatMessage) {
      return logCatMessage.getHeader().getLogLevel().getPriority() >= myLogLevel.getPriority();
    }
  }

  @Nullable
  private AndroidLogLevelFilter getSelectedLogLevelFilter() {
    final String filterName = myPreferences.TOOL_WINDOW_LOG_LEVEL;
    if (filterName != null) {
      for (AndroidLogLevelFilter logFilter : myLogLevelFilters) {
        if (filterName.equals(logFilter.myLogLevel.getStringValue())) {
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
    if (!(filter instanceof AndroidLogLevelFilter)) {
      return;
    }
    String newFilterName = ((AndroidLogLevelFilter)filter).myLogLevel.getStringValue();
    if (!Objects.equals(newFilterName, myPreferences.TOOL_WINDOW_LOG_LEVEL)) {
      myPreferences.TOOL_WINDOW_LOG_LEVEL = newFilterName;
      fireFilterChange(filter);
    }
  }

  @Override
  public void processingStarted() {
    myRejectBeforeHeader = null;
  }

  @Override
  @NotNull
  public final MyProcessingResult processLine(String line) {
    LogCatMessage logCatMessage = LogcatJson.fromJson(line);
    if (logCatMessage == null) {
      // This line did not come from a logcat
      return new MyProcessingResult(ProcessOutputTypes.STDOUT, false, null);
    }
    LogCatHeader header = logCatMessage.getHeader();
    myPrevHeader = header;

    boolean isApplicable = isApplicable(logCatMessage) && isApplicableByConfiguredFilter(logCatMessage);
    if (isApplicable) {
      LogCatHeader rejectBeforeHeader = myRejectBeforeHeader;
      if (rejectBeforeHeader != null) {
        isApplicable = !header.getTimestamp().isBefore(myRejectBeforeHeader.getTimestamp());
      }
    }

    return new MyProcessingResult(AndroidLogcatUtils.getProcessOutputType(header.getLogLevel()), isApplicable, null);
  }
}
