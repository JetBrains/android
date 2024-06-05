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
package com.android.tools.idea.actions

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.actions.SendFeedbackDescriptionProvider.Companion.getProviders
import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ReportFeedbackService
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.ui.LicensingFacade
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.launch
import org.jetbrains.android.util.AndroidBundle
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * This one is inspired by on com.intellij.ide.actions.SendFeedbackAction, however in addition to the basic
 * IntelliJ / Java / OS information, it enriches the bug template with Android-specific version context we'd like to
 * see pre-populated in our bug reports.
 */
class SendFeedbackAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    submit(e.project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (e.presentation.isEnabled) {
      e.presentation.setEnabled(SystemInfo.isMac || SystemInfo.isLinux || SystemInfo.isWindows)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(SendFeedbackAction::class.java)

    private const val UNKNOWN_VERSION = "Unknown"

    @JvmOverloads
    fun submit(project: Project?, extraDescriptionDetails: String? = "") {
      if (project == null) return
      service<ReportFeedbackService>().coroutineScope.launch {
        withBackgroundProgress(project, AndroidBundle.message("progress.title.collecting.data"), true) {
          reportSequentialProgress { reporter ->
            reporter.indeterminateStep(AndroidBundle.message("progress.text.collecting.feedback.information")) {
              val applicationInfo = ApplicationInfoEx.getInstanceEx()
              val feedbackUrl = if (StudioFlags.ENABLE_NEW_COLLECT_LOGS_DIALOG.get()) newFeedbackUrl else applicationInfo.getFeedbackUrl()
              val description = getDescription(project) + extraDescriptionDetails
              val appInfo = ApplicationInfoEx.getInstanceEx()
              val eap = appInfo.isEAP
              val la = LicensingFacade.getInstance()
              val url = feedbackUrl
                .replace("\$BUILD", URLUtil.encodeURIComponent(if (eap) appInfo.getBuild().asStringWithoutProductCode() else appInfo.getBuild().asString()))
                .replace("\$TIMEZONE", URLUtil.encodeURIComponent(System.getProperty("user.timezone", "")))
                .replace("\$STUDIO_VERSION", URLUtil.encodeURIComponent(getVersion(applicationInfo)))
                .replace("\$VERSION", URLUtil.encodeURIComponent(appInfo.getFullVersion()))
                .replace("\$EVAL", URLUtil.encodeURIComponent((la != null && la.isEvaluationLicense).toString()))
                .replace("\$DESCR", URLUtil.encodeURIComponent(description))
              BrowserUtil.browse(url, project)
            }
          }
        }
      }
    }

    private fun getVersion(applicationInfo: ApplicationInfoEx): String {
      val major = applicationInfo.getMajorVersion()
      if (major == null) {
        return UNKNOWN_VERSION
      }
      val minor = applicationInfo.getMinorVersion()
      if (minor == null) {
        return UNKNOWN_VERSION
      }
      val micro = applicationInfo.getMicroVersion()
      if (micro == null) {
        return UNKNOWN_VERSION
      }
      val patch = applicationInfo.getPatchVersion()
      if (patch == null) {
        return UNKNOWN_VERSION
      }

      return java.lang.String.join(".", major, minor, micro, patch)
    }

    @Slow
    fun getDescription(project: Project?): String {
      // Use safe call wrapper extensively to make sure that as much as possible version context is collected and
      // that any exceptions along the way do not actually break the feedback sending flow (we're already reporting a bug,
      // so let's not make that process prone to exceptions)
      return safeCall {
        val sb = StringBuilder(getDescription(null))
        // Add Android Studio custom information we want to see prepopulated in the bug reports
        sb.append("\n\n")
        sb.append(String.format("AS: %1\$s\n", ApplicationInfoEx.getInstanceEx().getFullVersion()))
        sb.append(String.format("Kotlin plugin: %1\$s\n", safeCall { kotlinPluginDetails }))

        for (provider in getProviders()) {
          provider.getDescription(project).forEach(
            Consumer { str: String -> sb.append(str + "\n") })
        }
        sb.toString()
      }
    }

    fun safeCall(runnable: Supplier<String?>): String {
      try {
        return runnable.get()!!
      }
      catch (e: Throwable) {
        LOG.info("Unable to prepopulate additional version information - proceeding with sending feedback anyway. ", e)
        return "(unable to retrieve additional version information)"
      }
    }

    private val kotlinPluginDetails: String
      get() {
        val kotlinPluginId = PluginId.findId("org.jetbrains.kotlin")
        val kotlinPlugin = getPlugin(kotlinPluginId)
        if (kotlinPlugin != null) {
          return kotlinPlugin.getVersion()
        }
        return "(kotlin plugin not found)"
      }

    private val newFeedbackUrl: String
      get() {
        val instructions = """
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
      
      """.trimIndent()

        val app = ApplicationInfoEx.getInstanceEx()
        val buildNumber = app.getBuild().asString()
        val date = app.getBuildDate().time
        val dateFormat: DateFormat = SimpleDateFormat("yyyyMMddHHmm", Locale.US)
        val strDate = dateFormat.format(date)

        return "https://issuetracker.google.com/issues/new?" +
               "component=192708" +
               "&template=840533" +
               "&foundIn=\$STUDIO_VERSION" +
               "&format=MARKDOWN" +
               "&description=" +
               "%60%60%60%0A" +
               URLUtil.encodeURIComponent(instructions) + "%0A" +
               "Build%3A%20" + buildNumber + "%2C%20" + strDate +
               "\$DESCR" +
               "%60%60%60"
      }
  }
}
