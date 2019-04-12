/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.diagnostics;

import com.android.annotations.concurrency.Slow;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.WindowsDefenderStatus;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WindowsPerformanceHintsChecker {
  private static final Logger LOG = Logger.getInstance(WindowsPerformanceHintsChecker.class);

  private static final Pattern WINDOWS_ENV_VAR_PATTERN = Pattern.compile("%([^%]+?)%");
  private static final Pattern WINDOWS_DEFENDER_WILDCARD_PATTERN = Pattern.compile("[?*]");
  private static final int POWERSHELL_COMMAND_TIMEOUT_MS = 5000;
  private static final int MAX_POWERSHELL_STDERR_LENGTH = 500;

  @Slow
  static void checkWindowsDefender(@NotNull AndroidStudioSystemHealthMonitor systemHealthMonitor, @NotNull Project project) {
    switch (getRealtimeScanningEnabled()) {
      case SCANNING_ENABLED:
        List<Pattern> excludedPatterns = getExcludedPatterns();
        if (excludedPatterns == null) {
          // there was an error getting the excluded paths
          logWindowsDefenderStatus(WindowsDefenderStatus.Status.UNKNOWN_STATUS, false, project);
        } else {
          Map<Path, Boolean> pathStatuses = checkPathsExcluded(getImportantPaths(project), excludedPatterns);
          WindowsDefenderStatus.Status overallStatus;
          if (pathStatuses.containsValue(Boolean.FALSE)) {
            if (StudioFlags.WINDOWS_DEFENDER_NOTIFICATION_ENABLED.get()) {
              systemHealthMonitor.showNotification("virus.scanning.warn.message", PropertiesComponent.getInstance(project),
                   AndroidStudioSystemHealthMonitor.detailsAction("https://d.android.com/r/studio-ui/antivirus-check"), false,
                   getNotificationTextForNonExcludedPaths(pathStatuses));
            }
            if (pathStatuses.containsValue(Boolean.TRUE)) {
              overallStatus = WindowsDefenderStatus.Status.SOME_EXCLUDED;
            } else {
              overallStatus = WindowsDefenderStatus.Status.NONE_EXCLUDED;
            }
          } else {
            overallStatus = WindowsDefenderStatus.Status.ALL_EXCLUDED;
          }
          String projectDir = project.getBasePath();
          boolean projectPathExcluded = false;
          if (projectDir != null) {
            projectPathExcluded = pathStatuses.getOrDefault(Paths.get(projectDir), false);
          }
          logWindowsDefenderStatus(overallStatus, projectPathExcluded, project);
        }
        break;
      case SCANNING_DISABLED:
        logWindowsDefenderStatus(WindowsDefenderStatus.Status.SCANNING_DISABLED, true, project);
        break;
      case ERROR:
        logWindowsDefenderStatus(WindowsDefenderStatus.Status.UNKNOWN_STATUS, false, project);
        break;
    }
  }

  private static void logWindowsDefenderStatus(WindowsDefenderStatus.Status status, boolean projectDirExcluded, @NotNull Project project) {
    LOG.info("Windows Defender status: " + status + "; projectDirExcluded? " + projectDirExcluded);
    if (StudioFlags.WINDOWS_DEFENDER_METRICS_ENABLED.get()) {
      if (!ApplicationManager.getApplication().isInternal() && StatisticsUploadAssistant.isSendAllowed()) {
        UsageTracker.log(UsageTrackerUtils.withProjectId(AndroidStudioEvent.newBuilder()
                                                           .setKind(AndroidStudioEvent.EventKind.WINDOWS_DEFENDER_STATUS)
                                                           .setWindowsDefenderStatus(WindowsDefenderStatus.newBuilder()
                                                                                       .setProjectDirExcluded(projectDirExcluded)
                                                                                       .setStatus(status)), project));
      }
    }
  }

  /** Runs a powershell command to list the paths that are excluded from realtime scanning by Windows Defender. These
   * paths can contain environment variable references, as well as wildcards ('?', which matches a single character, and
   * '*', which matches any sequence of characters (but cannot match multiple nested directories; i.e., "foo\*\bar" would
   * match foo\baz\bar but not foo\baz\quux\bar)). The behavior of wildcards with respect to case-sensitivity is undocumented.
   * Returns a list of patterns, one for each exclusion path, that emulate how Windows Defender would interpret that path.
   */
  @Slow
  @Nullable
  private static List<Pattern> getExcludedPatterns() {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "powershell", "-inputformat", "none", "-outputformat", "text", "-NonInteractive", "-Command", "Get-MpPreference | select -ExpandProperty \"ExclusionPath\""), POWERSHELL_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        return output.getStdoutLines(true).stream().map(path -> wildcardsToRegex(expandEnvVars(path))).collect(Collectors.toList());
      } else {
        LOG.warn("Windows Defender exclusion path check exited with status " + output.getExitCode() + ": " +
                 StringUtil.first(output.getStderr(), MAX_POWERSHELL_STDERR_LENGTH, false));
      }
    } catch (ExecutionException e) {
      LOG.warn("Windows Defender exclusion path check failed", e);
    }
    return null;
  }

  /** Runs a powershell command to determine whether realtime scanning is enabled or not. */
  @Slow
  @NotNull
  private static RealtimeScanningStatus getRealtimeScanningEnabled() {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "powershell", "-inputformat", "none", "-outputformat", "text", "-NonInteractive", "-Command", "Get-MpPreference | select -ExpandProperty \"DisableRealtimeMonitoring\""), POWERSHELL_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        if (output.getStdout().startsWith("False")) return RealtimeScanningStatus.SCANNING_ENABLED;
        return RealtimeScanningStatus.SCANNING_DISABLED;
      } else {
        LOG.warn("Windows Defender realtime scanning status check exited with status " + output.getExitCode() + ": " +
                 StringUtil.first(output.getStderr(), MAX_POWERSHELL_STDERR_LENGTH, false));
      }
    } catch (ExecutionException e) {
      LOG.warn("Windows Defender realtime scanning status check failed", e);
    }
    return RealtimeScanningStatus.ERROR;
  }

  private enum RealtimeScanningStatus {
    SCANNING_DISABLED,
    SCANNING_ENABLED,
    ERROR
  }

  /** Returns a list of paths that might impact build performance if Windows Defender were configured to scan them. */
  @NotNull
  private static List<Path> getImportantPaths(@NotNull Project project) {
    String homeDir = System.getProperty("user.home");
    String gradleUserHome = System.getenv("GRADLE_USER_HOME");
    String projectDir = project.getBasePath();

    List<Path> paths = new ArrayList<>();
    if (projectDir != null) {
      paths.add(Paths.get(projectDir));
    }
    paths.add(Paths.get(PathManager.getSystemPath()));
    if (gradleUserHome != null) {
      paths.add(Paths.get(gradleUserHome));
    } else {
      paths.add(Paths.get(homeDir, ".gradle"));
    }
    paths.add(Paths.get(homeDir, ".android"));
    AndroidSdkData sdkData = AndroidSdkUtils.getProjectSdkData(project);
    if (sdkData == null) {
      sdkData = AndroidSdkUtils.getFirstAndroidModuleSdkData(project);
    }
    if (sdkData != null) {
      paths.add(Paths.get(sdkData.getLocation().getAbsolutePath()));
    }
    return paths;
  }

  /** Expands references to environment variables (strings delimited by '%') in 'path' */
  @NotNull
  private static String expandEnvVars(@NotNull String path) {
    Matcher m = WINDOWS_ENV_VAR_PATTERN.matcher(path);
    StringBuffer result = new StringBuffer();
    while (m.find()) {
      String value = System.getenv(m.group(1));
      if (value != null) {
        m.appendReplacement(result, Matcher.quoteReplacement(value));
      }
    }
    m.appendTail(result);
    return result.toString();
  }

  /**
   * Produces a {@link Pattern} that approximates how Windows Defender interprets the exclusion path {@link path}.
   * The path is split around wildcards; the non-wildcard portions are quoted, and regex equivalents of
   * the wildcards are inserted between them. See
   * https://docs.microsoft.com/en-us/windows/security/threat-protection/windows-defender-antivirus/configure-extension-file-exclusions-windows-defender-antivirus
   * for more details.
   */
  @NotNull
  private static Pattern wildcardsToRegex(@NotNull String path) {
    Matcher m = WINDOWS_DEFENDER_WILDCARD_PATTERN.matcher(path);
    StringBuilder sb = new StringBuilder();
    int previousWildcardEnd = 0;
    while (m.find()) {
      sb.append(Pattern.quote(path.substring(previousWildcardEnd, m.start())));
      if (m.group().equals("?")) {
        sb.append("[^\\\\]");
      } else {
        sb.append("[^\\\\]*");
      }
      previousWildcardEnd = m.end();
    }
    sb.append(Pattern.quote(path.substring(previousWildcardEnd)));
    sb.append(".*"); // technically this should only be appended if the path refers to a directory, not a file. This is difficult to determine.
    return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE); // CASE_INSENSITIVE is overly permissive. Being precise with this is more work than it's worth.
  }

  /**
   * Checks whether each of the given paths in {@link paths} is matched by some pattern in {@link excludedPatterns},
   * returning a map of the results.
   */
  @NotNull
  private static Map<Path, Boolean> checkPathsExcluded(@NotNull List<Path> paths, @NotNull List<Pattern> excludedPatterns) {
    Map<Path, Boolean> result = new HashMap<>();
    for (Path path : paths) {
      try {
        String canonical = path.toRealPath().toString();
        boolean found = false;
        for (Pattern pattern : excludedPatterns) {
          if (pattern.matcher(canonical).matches()) {
            found = true;
            result.put(path, true);
            break;
          }
        }
        if (!found) {
          result.put(path, false);
        }
      } catch (IOException e) {
        LOG.warn("Windows Defender exclusion check couldn't get real path for " + path, e);
      }
    }
    return result;
  }

  @NotNull
  private static String getNotificationTextForNonExcludedPaths(@NotNull Map<Path, Boolean> pathStatuses) {
    StringBuilder sb = new StringBuilder();
    pathStatuses.entrySet().stream().filter(entry -> !entry.getValue()).forEach(entry -> sb.append("<br/>" + entry.getKey()));
    return sb.toString();
  }

}
