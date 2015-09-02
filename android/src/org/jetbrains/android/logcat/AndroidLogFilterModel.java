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

package org.jetbrains.android.logcat;

import com.android.ddmlib.Log;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFilter;
import com.intellij.diagnostic.logging.LogFilterListener;
import com.intellij.diagnostic.logging.LogFilterModel;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.logcat.AndroidLogcatReceiver.LogMessageHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A filter which plugs into {@link LogConsoleBase} for custom logcat filtering.
 */
public abstract class AndroidLogFilterModel extends LogFilterModel {
  private final List<LogFilterListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private Log.LogLevel myPrevMessageLogLevel;
  private String myPrevTag;
  private String myPrevPkg;
  private String myPrevPid;
  private boolean myFullMessageApplicable = false;
  private boolean myFullMessageApplicableByCustomFilter = false;
  private StringBuilder myMessageBuilder = new StringBuilder();
  private boolean myIsInvalidRegexp = false;
  private @Nullable Pattern myCustomPattern;

  protected List<AndroidLogFilter> myLogFilters = new ArrayList<AndroidLogFilter>();

  public AndroidLogFilterModel() {
    for (Log.LogLevel logLevel : Log.LogLevel.values()) {
      myLogFilters.add(new AndroidLogFilter(logLevel));
    }
  }

  private void updateCustomPattern(@Nullable String filter) {
    myIsInvalidRegexp = false;
    myCustomPattern = null;
    if (filter != null && filter.length() > 0) {
      if (isRegexp()) {
        try {
          myCustomPattern = Pattern.compile(filter, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }
        catch (PatternSyntaxException e) {
          myIsInvalidRegexp = true;
        }
      }
      if (myCustomPattern == null) {
        myCustomPattern = Pattern.compile(Pattern.quote(filter), Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
      }
    }
  }

  @Override
  public final void updateCustomFilter(String filter) {
    super.updateCustomFilter(filter);
    updateCustomPattern(filter);
    setCustomFilter(filter);
    fireTextFilterChange();
  }

  public final void updateConfiguredFilter(@Nullable ConfiguredFilter filter) {
    setConfiguredFilter(filter);
    fireTextFilterChange();
  }

  public void updateIsRegexp(boolean isRegexp) {
    setIsRegexp(isRegexp);
    updateCustomPattern(getCustomFilter());
    fireTextFilterChange();
  }

  protected abstract void setCustomFilter(String filter);

  protected void setConfiguredFilter(@Nullable ConfiguredFilter filter) {
  }

  protected abstract void setIsRegexp(boolean isRegexp);

  @Nullable
  protected ConfiguredFilter getConfiguredFilter() {
    return null;
  }

  protected abstract boolean isRegexp();

  public boolean isInvalidRegexp() {
    return myIsInvalidRegexp;
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

    Log.LogLevel logLevel = null;
    String tag = null;
    String pkg = null;
    String pid = null;
    String message = text;

    AndroidLogcatFormatter.Message result = AndroidLogcatFormatter.parseMessage(text);
    if (result.getHeader() != null) {
      LogMessageHeader header = result.getHeader();
      logLevel = header.myLogLevel;
      tag = header.myTag;
      pkg = header.myAppPackage;
      pid = Integer.toString(header.myPid);
      message = result.getMessage();
    }

    if (tag == null) {
      tag = myPrevTag;
    }
    if (pkg == null) {
      pkg = myPrevPkg;
    }
    if (pid == null) {
      pid = myPrevPid;
    }
    if (logLevel == null) {
      logLevel = myPrevMessageLogLevel;
    }

    return configuredFilterName.isApplicable(message, tag, pkg, pid, logLevel);
  }

  @Override
  public final List<? extends LogFilter> getLogFilters() {
    return myLogFilters;
  }

  private final class AndroidLogFilter extends LogFilter {
    final Log.LogLevel myLogLevel;

    private AndroidLogFilter(Log.LogLevel logLevel) {
      super(StringUtil.capitalize(logLevel.name().toLowerCase()));
      myLogLevel = logLevel;
    }

    @Override
    public boolean isAcceptable(String line) {
      Log.LogLevel logLevel = null;

      AndroidLogcatFormatter.Message result = AndroidLogcatFormatter.parseMessage(line);
      if (result.getHeader() != null) {
        logLevel = result.getHeader().myLogLevel;
      }
      if (logLevel == null) {
        logLevel = myPrevMessageLogLevel;
      }
      return logLevel != null && logLevel.getPriority() >= myLogLevel.getPriority();
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
    myPrevMessageLogLevel = null;
    myPrevTag = null;
    myPrevPkg = null;
    myPrevPid = null;
    myFullMessageApplicable = false;
    myFullMessageApplicableByCustomFilter = false;
    myMessageBuilder = new StringBuilder();
  }

  @Override
  @NotNull
  public final MyProcessingResult processLine(String line) {
    AndroidLogcatFormatter.Message result = AndroidLogcatFormatter.parseMessage(line);
    final boolean hasHeader = result.getHeader() != null;

    if (hasHeader) {
      LogMessageHeader header = result.getHeader();
      if (header.myLogLevel != null) {
        myPrevMessageLogLevel = header.myLogLevel;
      }

      if (!header.myTag.isEmpty()) {
        myPrevTag = header.myTag;
      }

      if (!header.myAppPackage.isEmpty()) {
        myPrevPkg = header.myAppPackage;
      }

      if (header.myPid != 0) {
        myPrevPid = Integer.toString(header.myPid);
      }
    }
    final boolean applicable = isApplicable(line); 
    final boolean applicableByCustomFilter = isApplicableByCustomFilter(line);

    String messagePrefix;
    
    if (hasHeader) {
      messagePrefix = null;
      myMessageBuilder = new StringBuilder(line);
      myMessageBuilder.append('\n');
      myFullMessageApplicable = applicable;
      myFullMessageApplicableByCustomFilter = applicableByCustomFilter;
    }
    else {
      messagePrefix = (myFullMessageApplicable || applicable) &&
                      (myFullMessageApplicableByCustomFilter || applicableByCustomFilter) &&
                      !(myFullMessageApplicable && myFullMessageApplicableByCustomFilter)
                      ? myMessageBuilder.toString()
                      : null;
      myMessageBuilder.append(line).append('\n');
      myFullMessageApplicable = myFullMessageApplicable || applicable;
      myFullMessageApplicableByCustomFilter = myFullMessageApplicableByCustomFilter || applicableByCustomFilter;
    }
    final Key key = myPrevMessageLogLevel != null ? getProcessOutputType(myPrevMessageLogLevel) : ProcessOutputTypes.STDOUT;
    
    return new MyProcessingResult(key,
                                  myFullMessageApplicable && myFullMessageApplicableByCustomFilter,
                                  messagePrefix);
  }
}
