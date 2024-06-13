/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.build.diagnostic

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.IdeInfo
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.google.wireless.android.sdk.stats.WindowsDefenderStatus
import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowLogAction
import com.intellij.ide.impl.isTrusted
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.android.util.AndroidBundle
import java.nio.file.Path

private val LOG = Logger.getInstance(WindowsDefenderCheckService::class.java)

@Service(Service.Level.PROJECT)
class WindowsDefenderCheckService(
  private val project: Project,
  private val checkerProvider: () -> WindowsDefenderChecker
) {

  constructor(project: Project) : this(project, { WindowsDefenderChecker.getInstance() })

  companion object {
    @JvmStatic
    fun getInstance(project: Project): WindowsDefenderCheckService {
      return project.getService(WindowsDefenderCheckService::class.java)
    }

    val NO_WARNING = WindowsDefenderWarningData(shouldShowWarning = false, interestingPaths = emptyList())
    val manualInstructionsLink = "https://d.android.com/r/tools/build-attribution/antivirus-check-manual-instructions"
  }

  /** This value should be set only from the check triggered in [StudioWindowsDefenderCheckerActivity] which should run only on Windows.*/
  private var realTimeProtectionEnabledOnStartup: Boolean? = null

  val warningData: WindowsDefenderWarningData
    get() {
      if (realTimeProtectionEnabledOnStartup == true && project.isTrusted()) {
        val checker = checkerProvider()
        if (!checker.isStatusCheckIgnored(project)) {
          val paths = checker.getPathsToExclude(project)
          return WindowsDefenderWarningData(shouldShowWarning = true, interestingPaths = paths)
        }
      }
      return NO_WARNING
    }

  data class WindowsDefenderWarningData(
    val shouldShowWarning: Boolean,
    val interestingPaths: List<Path>
  )

  /** This should only be called from [StudioWindowsDefenderCheckerActivity] in production code. */
  fun checkRealTimeProtectionStatus() {
    try {
      val checker = checkerProvider()
      if (checker.isStatusCheckIgnored(project)) {
        LOG.info("status check is disabled")
        logState(WindowsDefenderStatus.Status.CHECK_IGNORED)
        return
      }

      val protection = checker.isRealTimeProtectionEnabled
      LOG.info("real-time protection: $protection")
      realTimeProtectionEnabledOnStartup = protection
      when {
        protection == null -> logState(WindowsDefenderStatus.Status.UNKNOWN_STATUS)
        protection == false -> logState(WindowsDefenderStatus.Status.SCANNING_DISABLED)
        protection -> {
          val state = WindowsDefenderStatus.Status.ENABLED_AUTO
          logState(state)
          showWarningNotification(checker.getPathsToExclude(project))
        }
      }
    }
    catch (t: Throwable) {
      LOG.error("Error reading Windows Defender status", t)
      realTimeProtectionEnabledOnStartup = null
    }
  }

  fun runAutoExclusionScript(eventSourcePage: BuildAttributionUiEvent.Page.PageType, callback: (Boolean) -> Unit) {
    val checker = checkerProvider()
    LOG.info("Try exclude project paths")
    val paths = checker.getPathsToExclude(project)
    @Suppress("DialogTitleCapitalization")
    runBackgroundableTask(DiagnosticBundle.message("defender.config.progress"), project, false) {
      val success = checker.excludeProjectPaths(project, paths)
      if (success) {
        ignoreCheck(globally = false, callback = {})
        logUserAction(BuildAttributionUiEvent.EventType.DEFENDER_WARNING_AUTO_EXCLUDE_SUCCESS, eventSourcePage)
      }
      else {
        logUserAction(BuildAttributionUiEvent.EventType.DEFENDER_WARNING_AUTO_EXCLUDE_FAILURE, eventSourcePage)
      }
      callback(success)
    }
  }

  fun ignoreCheckForProject(eventSourcePage: BuildAttributionUiEvent.Page.PageType, callback: () -> Unit) {
    LOG.info("Suppress warning for the project clicked")
    ignoreCheck(globally = false, callback)
    logUserAction(BuildAttributionUiEvent.EventType.DEFENDER_WARNING_SUPPRESS_CLICKED, eventSourcePage)
  }

  private fun ignoreCheckGlobally(eventSourcePage: BuildAttributionUiEvent.Page.PageType, callback: () -> Unit) {
    LOG.info("Suppress warning globally")
    ignoreCheck(globally = true, callback)
    logUserAction(BuildAttributionUiEvent.EventType.DEFENDER_WARNING_SUPPRESS_GLOBALLY_CLICKED, eventSourcePage)
  }

  private fun ignoreCheck(globally: Boolean, callback: () -> Unit) {
    LOG.info("Suppressing check, globally=${globally}")
    val projectToUse = if (globally) null else project
    checkerProvider().ignoreStatusCheck(projectToUse, true)
    callback()
  }

  fun trackShowingManualInstructions(eventSourcePage: BuildAttributionUiEvent.Page.PageType) {
    logUserAction(BuildAttributionUiEvent.EventType.DEFENDER_WARNING_MANUAL_INSTRUCTIONS_CLICKED, eventSourcePage)
  }

  private fun logState(status: WindowsDefenderStatus.Status) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .withProjectId(project)
        .setKind(AndroidStudioEvent.EventKind.WINDOWS_DEFENDER_STATUS)
        .setWindowsDefenderStatus(WindowsDefenderStatus.newBuilder().setStatus(status))
    )
  }

  private fun logUserAction(actionEventType: BuildAttributionUiEvent.EventType, eventSourcePage: BuildAttributionUiEvent.Page.PageType) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT)
        .withProjectId(project)
        .setBuildAttributionUiEvent(BuildAttributionUiEvent.newBuilder().apply {
          currentPage = BuildAttributionUiEvent.Page.newBuilder().setPageType(eventSourcePage).setPageEntryIndex(1).build()
          eventType = actionEventType
        })
    )
  }

  private fun notification(@NlsContexts.NotificationContent content: String, type: NotificationType): Notification =
    Notification("WindowsDefender", DiagnosticBundle.message("notification.group.defender.config"), content, type)

  private fun showWarningNotification(importantPaths: List<Path>) {
    if (!project.isTrusted()) return
    val pathList = importantPaths.joinToString(separator = "<br>&nbsp;&nbsp;", prefix = "<br>&nbsp;&nbsp;") { it.toString() }
    val ignoreForProject = DiagnosticBundle.message("defender.config.suppress1")
    val auto = DiagnosticBundle.message("defender.config.auto")
    val manual = DiagnosticBundle.message("defender.config.manual")
    notification(AndroidBundle.message("android.defender.config.prompt", pathList, auto, ignoreForProject), NotificationType.INFORMATION)
      .also {
        it.isImportant = true
        it.collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
      }
      .addAction(NotificationAction.createSimpleExpiring(auto) {
        runAutoExclusionScript(BuildAttributionUiEvent.Page.PageType.WINDOWS_DEFENDER_NOTIFICATION, ::showResultNotification)
      })
      .addAction(NotificationAction.createSimple(manual, ::showManualInstructions))
      .addAction(NotificationAction.createSimpleExpiring(ignoreForProject) {
        ignoreCheckForProject(BuildAttributionUiEvent.Page.PageType.WINDOWS_DEFENDER_NOTIFICATION, ::onIgnoreCallback)
      })
      .addAction(NotificationAction.createSimpleExpiring(DiagnosticBundle.message("defender.config.suppress2")) {
        ignoreCheckGlobally(BuildAttributionUiEvent.Page.PageType.WINDOWS_DEFENDER_NOTIFICATION, ::onIgnoreCallback)
      })
      .notify(project)
  }

  private fun showResultNotification(success: Boolean) {
    if (success) {
      notification(DiagnosticBundle.message("defender.config.success"), NotificationType.INFORMATION)
        .notify(project)
    }
    else {
      val ignoreForProject = DiagnosticBundle.message("defender.config.suppress1")
      notification(AndroidBundle.message("android.defender.config.failed"), NotificationType.WARNING)
        .addAction(NotificationAction.createSimple(ActionsBundle.message("show.log.notification.text"), ShowLogAction::showLog))
        .addAction(NotificationAction.createSimple(AndroidBundle.message("android.defender.config.failed.instructions"), ::showManualInstructions))
        .addAction(NotificationAction.createSimpleExpiring(ignoreForProject) {
          ignoreCheckForProject(BuildAttributionUiEvent.Page.PageType.WINDOWS_DEFENDER_NOTIFICATION, ::onIgnoreCallback)
        })
        .notify(project)
    }
  }

  private fun showManualInstructions() {
    BrowserUtil.browse(manualInstructionsLink)
    trackShowingManualInstructions(BuildAttributionUiEvent.Page.PageType.WINDOWS_DEFENDER_NOTIFICATION)
  }

  private fun onIgnoreCallback() {
    val action = ActionsBundle.message("action.ResetWindowsDefenderNotification.text")
    notification(DiagnosticBundle.message("defender.config.restore", action), NotificationType.INFORMATION)
      .notify(project)
  }
}

/**
 * This activity checks the status of Windows Defender, approximately at the same time as IJ code in
 * [com.intellij.diagnostic.WindowsDefenderCheckerActivity] does it. We don't want to run this check on every build
 * to not provoke additional "suspicious activity" warnings from the system.
 * It runs only for windows, filter is defined in plugin.xml.
 */
class StudioWindowsDefenderCheckerActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
    if (!IdeInfo.getInstance().isAndroidStudio) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    WindowsDefenderCheckService.getInstance(project).checkRealTimeProtectionStatus()
  }
}

/**
 * Override instance of original [com.intellij.diagnostic.WindowsDefenderChecker] to make it provide our documentation link
 * in the reset flow triggered by [com.intellij.diagnostic.ResetWindowsDefenderNotification].
 */
class WindowsDefenderCheckerOverride : com.intellij.diagnostic.WindowsDefenderChecker() {
  override fun getConfigurationInstructionsUrl(): String = WindowsDefenderCheckService.manualInstructionsLink
}
