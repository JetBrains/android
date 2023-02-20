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

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;

import com.android.annotations.concurrency.Slow;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.diagnostics.windows.VirusCheckerStatusProvider;
import com.android.tools.idea.diagnostics.windows.WindowsDefenderPowerShellStatusProvider;
import com.android.tools.idea.diagnostics.windows.WindowsDefenderRegistryStatusProvider;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.WindowsDefenderStatus;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.android.tools.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WindowsPerformanceHintsChecker {
  private static final Logger LOG = Logger.getInstance(WindowsPerformanceHintsChecker.class);

  private static final String ANTIVIRUS_NOTIFICATION_LAST_SHOWN_TIME_KEY = "antivirus.scan.notification.last.shown.time";
  private static final Duration ANTIVIRUS_MIN_INTERVAL_BETWEEN_NOTIFICATIONS = Duration.ofDays(1);
  private static final Pattern WINDOWS_ENV_VAR_PATTERN = Pattern.compile("%([^%]+?)%");
  private static final Pattern WINDOWS_DEFENDER_WILDCARD_PATTERN = Pattern.compile("[?*]");

  private final AndroidStudioSystemHealthMonitor systemHealthMonitor;
  private final VirusCheckerStatusProvider myVirusCheckerStatusProvider;

  private WindowsPerformanceHintsChecker() {
    this.systemHealthMonitor = AndroidStudioSystemHealthMonitor.getInstance();
    this.myVirusCheckerStatusProvider =
      StudioFlags.ANTIVIRUS_CHECK_USE_REGISTRY.get() ? new WindowsDefenderRegistryStatusProvider()
                                                     : new WindowsDefenderPowerShellStatusProvider();
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean isEnabled() {
    return SystemInfo.isWindows && (StudioFlags.ANTIVIRUS_METRICS_ENABLED.get() || StudioFlags.ANTIVIRUS_NOTIFICATION_ENABLED.get());
  }

  /** Runs checks a few seconds after a project is opened. */
  public static class MyProjectStartupActivity implements StartupActivity.Background {

    public MyProjectStartupActivity() {
      if (!isEnabled()) throw ExtensionNotApplicableException.create();
    }

    @Override
    public void runActivity(@NotNull Project project) {
      // Check antivirus status for metrics only; do not show any notifications.
      var app = ApplicationManager.getApplication();
      var checker = new WindowsPerformanceHintsChecker();
      app.executeOnPooledThread(() -> checker.checkWindowsDefender(project, false));
      if (isEnabled()) {
        getProjectSystem(project).getBuildManager().addBuildListener(project, new MyBuildListener(project));
      }
    }
  }

  /** Runs checks after a Gradle build. */
  public static class MyBuildListener implements ProjectSystemBuildManager.BuildListener {

    private final Project myProject;

    public MyBuildListener(@NotNull Project project) {
      myProject = project;
      if (!isEnabled()) throw ExtensionNotApplicableException.create();
    }

    @Override
    public void buildCompleted(@NotNull ProjectSystemBuildManager.BuildResult result) {
      // Check antivirus status after a successful build, and possibly show a notification.
      if (result.getStatus() == ProjectSystemBuildManager.BuildStatus.SUCCESS ) {
        final var mode = result.getMode();
        if (mode == ProjectSystemBuildManager.BuildMode.ASSEMBLE) {
          var app = ApplicationManager.getApplication();
          var checker = new WindowsPerformanceHintsChecker();
          app.executeOnPooledThread(() -> checker.checkWindowsDefender(myProject, true));
        }
      }
    }
  }

  @Slow
  private void checkWindowsDefender(@NotNull Project project, boolean showNotification) {
    VirusCheckerStatusProvider.RealtimeScanningStatus status;
    try {
      status = myVirusCheckerStatusProvider.getRealtimeScanningStatus();
    }
    catch (IOException exception) {
      LOG.warn("Error retrieving status of virus checker", exception);
      logWindowsDefenderStatus(WindowsDefenderStatus.Status.UNKNOWN_STATUS, false, project);
      return;
    }

    switch (status) {
      case SCANNING_ENABLED:
        List<Pattern> excludedPatterns = getExcludedPatterns();
        if (excludedPatterns == null) {
          // there was an error getting the excluded paths
          logWindowsDefenderStatus(WindowsDefenderStatus.Status.UNKNOWN_STATUS, false, project);
        } else {
          Map<Path, Boolean> pathStatuses = checkPathsExcluded(getImportantPaths(project), excludedPatterns);
          WindowsDefenderStatus.Status overallStatus;
          if (pathStatuses.containsValue(Boolean.FALSE)) {
            if (showNotification && StudioFlags.ANTIVIRUS_NOTIFICATION_ENABLED.get() && !shownRecently()) {
              showAntivirusNotification(project, getNotificationTextForNonExcludedPaths(pathStatuses));
              setLastShownTime();
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
    }
  }

  private void showAntivirusNotification(@NotNull Project project, @NotNull String pathDetails) {
    String key = "virus.scanning.warn.message";
    boolean ignored = false;
    PropertiesComponent applicationProperties = PropertiesComponent.getInstance();
    PropertiesComponent projectProperties = PropertiesComponent.getInstance(project);
    if (applicationProperties != null) {
      ignored = applicationProperties.isValueSet("ignore." + key);
    }
    if (projectProperties != null) {
      ignored |= projectProperties.isValueSet("ignore." + key);
    }
    LOG.info("issue detected: " + key + (ignored ? " (ignored)" : ""));
    if (ignored) return;

    Notification notification = systemHealthMonitor.new MyNotification(AndroidBundle.message(key, pathDetails));
    notification.addAction(new NotificationAction(AndroidBundle.message("virus.scanning.dont.show.again")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        if (applicationProperties != null) {
          applicationProperties.setValue("ignore." + key, "true");
        }
      }
    });
    notification.addAction(new NotificationAction(AndroidBundle.message("virus.scanning.dont.show.again.this.project")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        if (projectProperties != null) {
          projectProperties.setValue("ignore." + key, "true");
        }
      }
    });
    notification.addAction(AndroidStudioSystemHealthMonitor.detailsAction("https://d.android.com/r/studio-ui/antivirus-check"));
    notification.setImportant(true);

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private static boolean shownRecently() {
    if ("true".equals(System.getProperty("disable.antivirus.notification.rate.limit"))) return false;
    String lastShownTime = PropertiesComponent.getInstance().getValue(ANTIVIRUS_NOTIFICATION_LAST_SHOWN_TIME_KEY);
    if (lastShownTime == null) return false;
    try {
      Instant lastShown = Instant.parse(lastShownTime);
      return lastShown.plus(ANTIVIRUS_MIN_INTERVAL_BETWEEN_NOTIFICATIONS).isAfter(Instant.now());
    } catch (DateTimeException e) {
      // corrupted date format. Return false here and overwrite with a good value.
      setLastShownTime();
      return false;
    }
  }

  private static void setLastShownTime() {
    PropertiesComponent.getInstance().setValue(ANTIVIRUS_NOTIFICATION_LAST_SHOWN_TIME_KEY, Instant.now().toString());
  }

  private static void logWindowsDefenderStatus(WindowsDefenderStatus.Status status, boolean projectDirExcluded, @NotNull Project project) {
    LOG.info("Windows Defender status: " + status + "; projectDirExcluded? " + projectDirExcluded);
    if (StudioFlags.ANTIVIRUS_METRICS_ENABLED.get()) {
      if (!ApplicationManager.getApplication().isInternal() && StatisticsUploadAssistant.isSendAllowed()) {
        UsageTracker.log(UsageTrackerUtils.withProjectId(AndroidStudioEvent.newBuilder()
                                                           .setKind(AndroidStudioEvent.EventKind.WINDOWS_DEFENDER_STATUS)
                                                           .setWindowsDefenderStatus(WindowsDefenderStatus.newBuilder()
                                                                                       .setProjectDirExcluded(projectDirExcluded)
                                                                                       .setStatus(status)), project));
      }
    }
  }

  /** Call {@link VirusCheckerStatusProvider} to list the paths that are excluded from realtime scanning by Windows Defender. These
   * paths can contain environment variable references, as well as wildcards ('?', which matches a single character, and
   * '*', which matches any sequence of characters (but cannot match multiple nested directories; i.e., "foo\*\bar" would
   * match foo\baz\bar but not foo\baz\quux\bar)). The behavior of wildcards with respect to case-sensitivity is undocumented.
   * Returns a list of patterns, one for each exclusion path, that emulate how Windows Defender would interpret that path.
   */
  @Slow
  @Nullable
  private List<Pattern> getExcludedPatterns() {
    try {
      List<String> excludedPaths = myVirusCheckerStatusProvider.getExcludedPaths();
      return ContainerUtil.map(excludedPaths, path -> wildcardsToRegex(expandEnvVars(path)));
    }
    catch (IOException exception) {
      LOG.warn("Error retrieving list of excluded patterns: " + exception.getMessage());
      return null;
    }
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

    // Note: Do not include ".android" because
    // 1) the location cannot be customized by the user and
    // 2) the location is not write heavy (mostly read operations)
    //paths.add(Paths.get(homeDir, ".android"));

    AndroidSdkData sdkData = AndroidSdkUtils.getProjectSdkData(project);
    if (sdkData == null) {
      sdkData = AndroidSdkUtils.getFirstAndroidModuleSdkData(project);
    }
    if (sdkData != null) {
      paths.add(Paths.get(sdkData.getLocation().toString()));
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
   * Produces a {@link Pattern} that approximates how Windows Defender interprets the exclusion path {@code path}.
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
   * Checks whether each of the given paths in {@code paths} is matched by some pattern in {@code excludedPatterns},
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
      } catch (NoSuchFileException ignored) {
        // if path doesn't exist, it doesn't matter if it's excluded, and we don't need to log this fact.
      } catch (IOException e) {
        LOG.warn("Windows Defender exclusion check couldn't get real path for " + path, e);
      }
    }
    return result;
  }

  @NotNull
  private static String getNotificationTextForNonExcludedPaths(@NotNull Map<Path, Boolean> pathStatuses) {
    StringBuilder sb = new StringBuilder();
    pathStatuses.entrySet().stream().filter(entry -> !entry.getValue()).forEach(entry -> sb.append("<br/>").append(entry.getKey()));
    return sb.toString();
  }

}
