package org.jetbrains.android.logcat;

import com.android.ddmlib.Log;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Eugene.Kudelevsky
 */
class ConfiguredFilter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.logcat.ConfiguredFilter");

  private final String myName;
  private final Pattern myMessagePattern;
  private final Pattern myTagPattern;
  private final Pattern myPkgNamePattern;
  private final String myPid;
  private final Log.LogLevel myLogLevel;

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
  
  public boolean isApplicable(String message, String tag, String pkg,
                              String pid, Log.LogLevel logLevel) {
    if (myMessagePattern != null && (message == null || !myMessagePattern.matcher(message).find())) {
      return false;
    }

    if (myTagPattern != null && (tag == null || !myTagPattern.matcher(tag).find())) {
      return false;
    }

    if (myPkgNamePattern != null && (pkg == null || !myPkgNamePattern.matcher(pkg).matches())) {
      return false;
    }

    if (myPid != null && myPid.length() > 0 && !myPid.equals(pid)) {
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

  @Nullable
  @Contract ("!null,_ -> !null")
  public static ConfiguredFilter compile(@Nullable AndroidConfiguredLogFilters.MyFilterEntry entry,
                                         @NotNull String name) {
    if (entry == null) {
      return null;
    }

    Pattern logMessagePattern = compilePattern(entry.getLogMessagePattern());
    Pattern logTagPattern = compilePattern(entry.getLogTagPattern());
    Pattern pkgNamePattern = compilePattern(entry.getPackageNamePattern());

    final String pid = entry.getPid();

    Log.LogLevel logLevel = null;
    final String logLevelStr = entry.getLogLevel();
    if (logLevelStr != null && logLevelStr.length() > 0) {
      logLevel = Log.LogLevel.getByString(logLevelStr);
    }

    return new ConfiguredFilter(name, logMessagePattern, logTagPattern, pkgNamePattern,
                                pid, logLevel);

  }

  private static Pattern compilePattern(String pattern) {
    Pattern p = null;
    if (StringUtil.isNotEmpty(pattern)) {
      try {
        p = Pattern.compile(pattern, AndroidConfiguredLogFilters.getPatternCompileFlags(pattern));
      }
      catch (PatternSyntaxException e) {
        /** This shouldn't happen if the pattern was entered through the UI in which case
         the {@link org.jetbrains.android.logcat.EditLogFilterDialog#doValidate()} captures
         and reports the issue to the user. */
        LOG.info(e);
      }
    }

    return p;
  }
}
