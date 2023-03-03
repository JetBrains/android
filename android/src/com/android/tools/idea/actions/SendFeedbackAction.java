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
package com.android.tools.idea.actions;

import com.android.annotations.concurrency.Slow;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.io.URLUtil;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This one is inspired by on com.intellij.ide.actions.SendFeedbackAction, however in addition to the basic
 * IntelliJ / Java / OS information, it enriches the bug template with Android-specific version context we'd like to
 * see pre-populated in our bug reports.
 */
public class SendFeedbackAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(SendFeedbackAction.class);

  private static final String UNKNOWN_VERSION = "Unknown";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    submit(e.getProject());
  }

  public static void submit(@Nullable Project project) {
    submit(project, "");
  }

  public static void submit(@Nullable Project project, @Nullable String extraDescriptionDetails) {
    new Task.Modal(project, "Collecting Data", false) {
      @Override
      public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
        indicator.setText("Collecting feedback information");
        indicator.setIndeterminate(true);
        ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
        String feedbackUrl = StudioFlags.ENABLE_NEW_COLLECT_LOGS_DIALOG.get()
                             ? getNewFeedbackUrl()
                             : applicationInfo.getFeedbackUrl();

        String version = getVersion(applicationInfo);
        feedbackUrl = feedbackUrl.replace("$STUDIO_VERSION", version);

        String description = getDescription(project);
        com.intellij.ide.actions.SendFeedbackAction.submit(project, feedbackUrl, description + extraDescriptionDetails);
      }
    }.setCancelText("Cancel").queue();
  }

  private static String getVersion(ApplicationInfoEx applicationInfo) {
    String major = applicationInfo.getMajorVersion();
    if (major == null) {
      return UNKNOWN_VERSION;
    }
    String minor = applicationInfo.getMinorVersion();
    if (minor == null) {
      return UNKNOWN_VERSION;
    }
    String micro = applicationInfo.getMicroVersion();
    if (micro == null) {
      return UNKNOWN_VERSION;
    }
    String patch = applicationInfo.getPatchVersion();
    if (patch == null) {
      return UNKNOWN_VERSION;
    }

    return String.join(".", major, minor, micro, patch);
  }

  @Slow
  public static String getDescription(@Nullable Project project) {
    // Use safe call wrapper extensively to make sure that as much as possible version context is collected and
    // that any exceptions along the way do not actually break the feedback sending flow (we're already reporting a bug,
    // so let's not make that process prone to exceptions)
    return safeCall(() -> {
      StringBuilder sb = new StringBuilder(com.intellij.ide.actions.SendFeedbackAction.getDescription(null));
      // Add Android Studio custom information we want to see prepopulated in the bug reports
      sb.append("\n\n");
      sb.append(String.format("AS: %1$s\n", ApplicationInfoEx.getInstanceEx().getFullVersion()));
      sb.append(String.format("Kotlin plugin: %1$s\n", safeCall(SendFeedbackAction::getKotlinPluginDetails)));

      for (SendFeedbackDescriptionProvider provider : SendFeedbackDescriptionProvider.getProviders()) {
        provider.getDescription(project).forEach(str -> sb.append(str + "\n"));
      }
      return sb.toString();
    });
  }

  public static String safeCall(@NotNull Supplier<String> runnable) {
    try {
      return runnable.get();
    }
    catch (Throwable e) {
      LOG.info("Unable to prepopulate additional version information - proceeding with sending feedback anyway. ", e);
      return "(unable to retrieve additional version information)";
    }
  }

  private static String getKotlinPluginDetails() {
    PluginId kotlinPluginId = PluginId.findId("org.jetbrains.kotlin");
    IdeaPluginDescriptor kotlinPlugin = PluginManagerCore.getPlugin(kotlinPluginId);
    if (kotlinPlugin != null) {
      return kotlinPlugin.getVersion();
    }
    return "(kotlin plugin not found)";
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (e.getPresentation().isEnabled()) {
      e.getPresentation().setEnabled(SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isWindows);
    }
  }

  private static String getNewFeedbackUrl() {
    String instructions = """
      ####################################################

      Please provide all of the following information, otherwise we may not be able to route your bug report.

      ####################################################


      1. Describe the bug or issue that you're seeing.



      2. Attach log files from Android Studio
        2A. In the IDE, select the Help..Collect Logs and Diagnostic Data menu option.
        2B. Create a diagnostic report and save it to your local computer.
        2C. Attach the report to this bug using the Attach File button.

      3. If you know what they are, write the steps to reproduce:

         3A.
         3B.
         3C.

      In addition to logs, please attach a screenshot or recording that illustrates the problem.

      For more information on how to get your bug routed quickly, see https://developer.android.com/studio/report-bugs.html
      """;

    return "https://issuetracker.google.com/issues/new?" +
           "component=192708" +
           "&template=840533" +
           "&foundIn=$STUDIO_VERSION" +
           "&format=MARKDOWN" +
           "&description=" +
           "%60%60%60%0A" +
           URLUtil.encodeURIComponent(instructions) + "%0A" +
           "Build%3A%20__BUILD_NUMBER__%2C%20__BUILD_DATE__" +
           "$DESCR" +
           "%60%60%60";
  }
}
