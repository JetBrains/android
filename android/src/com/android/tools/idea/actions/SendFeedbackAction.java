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
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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
        String feedbackUrlTemplate = getFeedbackUrlTemplate();
        String version = ApplicationInfo.getInstance().getStrictVersion();
        String description = getDescription(project) + extraDescriptionDetails;
        String feedbackUrl = feedbackUrlTemplate
          .replace("$STUDIO_VERSION", URLUtil.encodeURIComponent(version))
          .replace("$DESCR", URLUtil.encodeURIComponent(description));
        BrowserUtil.browse(feedbackUrl, project);
      }
    }.setCancelText("Cancel").queue();
  }

  @Slow
  public static String getDescription(@Nullable Project project) {
    // Use safe call wrapper extensively to make sure that as much as possible version context is collected and
    // that any exceptions along the way do not actually break the feedback sending flow (we're already reporting a bug,
    // so let's not make that process prone to exceptions)
    return safeCall(() -> {
      var sb = new StringBuilder();
      sb.append(String.format("AS: %1$s\n", ApplicationInfo.getInstance().getFullVersion()));
      sb.append(StringUtil.trimLeading(SendFeedbackActionJavaShim.INSTANCE.getDescription(project)));
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

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (e.getPresentation().isEnabled()) {
      e.getPresentation().setEnabled(SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isWindows);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static String getFeedbackUrlTemplate() {
    String instructions = """
      ####################################################

      Please provide all of the following information, otherwise we may not be able to route your bug report.

      ####################################################


      1. Describe the bug or issue that you're seeing.



      2. Attach log files from Android Studio
        2A. In the IDE, select the Help..Collect Logs and Diagnostic Data menu option.
        2B. Create a diagnostic report and save it to your local computer.
        2C. Attach the report to this bug using the Add attachments button.

      3. If you know what they are, write the steps to reproduce:

         3A.
         3B.
         3C.

      In addition to logs, please attach a screenshot or recording that illustrates the problem.

      For more information on how to get your bug routed quickly, see https://developer.android.com/studio/report-bugs.html
      """;

    ApplicationInfo app = ApplicationInfo.getInstance();
    String buildNumber = app.getBuild().asString();
    Date date = app.getBuildDate().getTime();
    DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
    String strDate = dateFormat.format(date);

    return "https://issuetracker.google.com/issues/new?" +
           "component=192708" +
           "&template=840533" +
           "&foundIn=$STUDIO_VERSION" +
           "&format=MARKDOWN" +
           "&description=" +
           "%60%60%60%0A" +
           URLUtil.encodeURIComponent(instructions) + "%0A" +
           "Build%3A%20" + buildNumber + "%2C%20" + strDate + "%0A" +
           "$DESCR" +
           "%60%60%60";
  }
}
