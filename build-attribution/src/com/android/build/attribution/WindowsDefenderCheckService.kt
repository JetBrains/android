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
package com.android.build.attribution

import com.android.build.attribution.ui.view.WindowsDefenderPageHandler
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.google.wireless.android.sdk.stats.WindowsDefenderStatus
import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.diagnostic.WindowsDefenderCheckerWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class WindowsDefenderCheckService(
  private val project: Project,
  private val checkerProvider: () -> WindowsDefenderCheckerWrapper
) : WindowsDefenderPageHandler {

  constructor(project: Project) : this(project, { WindowsDefenderCheckerWrapper(WindowsDefenderChecker.getInstance()) })

  companion object {
    @JvmStatic
    fun getInstance(project: Project): WindowsDefenderCheckService {
      return project.getService(WindowsDefenderCheckService::class.java)
    }

    val NO_WARNING = WindowsDefenderWarningData(shouldShowWarning = false, canRunExclusionScript = false, interestingPaths = emptyList())
  }

  /** This value should be set only from the check triggered in [StudioWindowsDefenderCheckerActivity] which should run only on Windows.*/
  private var realTimeProtectionEnabledOnStartup: Boolean? = null

  val warningData: WindowsDefenderWarningData
    get() {
      if (realTimeProtectionEnabledOnStartup == true) {
        val checker = checkerProvider()
        if (!checker.isStatusCheckIgnored(project)) {
          val paths = checker.getImportantPaths(project)
          val canRunScript = checker.canRunScript()
          return WindowsDefenderWarningData(true, canRunScript, paths)
        }
      }
      return NO_WARNING
    }

  /** This should only be called from [StudioWindowsDefenderCheckerActivity] in production code. */
  fun checkRealTimeProtectionStatus() {
    val checker = checkerProvider()
    if (checker.isStatusCheckIgnored(project)) {
      Logger.getInstance(WindowsDefenderCheckService::class.java).info("status check is disabled")
      logState(WindowsDefenderStatus.Status.UNKNOWN_STATUS) //TODO(b/285867050): change to CHECK_IGNORED (add to proto first)
      return
    }

    val protection = checker.isRealTimeProtectionEnabled
    Logger.getInstance(WindowsDefenderCheckService::class.java).info("real-time protection: $protection")
    realTimeProtectionEnabledOnStartup = protection
    val canRunExclusionScript = checker.canRunScript()
    when {
      protection == null -> logState(WindowsDefenderStatus.Status.UNKNOWN_STATUS)
      protection == false -> logState(WindowsDefenderStatus.Status.SCANNING_DISABLED)
      protection && canRunExclusionScript -> logState(WindowsDefenderStatus.Status.UNKNOWN_STATUS) //TODO(b/285867050): change to ENABLED_AUTO (add to proto first)
      protection && !canRunExclusionScript -> logState(WindowsDefenderStatus.Status.UNKNOWN_STATUS) //TODO(b/285867050): change to ENABLED_MANUAL (add to proto first)
    }

  }

  override fun runAutoExclusionScript(callback: (Boolean) -> Unit) {
    val checker = checkerProvider()
    Logger.getInstance(WindowsDefenderCheckService::class.java).info("Try exclude project paths")
    val paths = checker.getImportantPaths(project)
    @Suppress("DialogTitleCapitalization")
    runBackgroundableTask(DiagnosticBundle.message("defender.config.progress"), project, false) {
      val success = checker.excludeProjectPaths(paths)
      if (success) {
        ignoreCheckForProject()
        //TODO(b/285867050): change to DEFENDER_WARNING_AUTO_EXCLUDE_SUCCESS (add to proto first)
        logUserAction(BuildAttributionUiEvent.EventType.UNKNOWN_TYPE)
      }
      else {
        //TODO(b/285867050): change to DEFENDER_WARNING_AUTO_EXCLUDE_FAILURE (add to proto first)
        logUserAction(BuildAttributionUiEvent.EventType.UNKNOWN_TYPE)
      }
      callback(success)
    }
  }

  override fun ignoreCheckForProject() {
    Logger.getInstance(WindowsDefenderCheckService::class.java).info("Suppress warning for the project")
    checkerProvider().ignoreStatusCheck(project, true)
    //TODO(b/285867050): change to DEFENDER_WARNING_SUPPRESS_CLICKED (add to proto first)
    logUserAction(BuildAttributionUiEvent.EventType.UNKNOWN_TYPE)
  }

  data class WindowsDefenderWarningData(
    val shouldShowWarning: Boolean,
    val canRunExclusionScript: Boolean,
    val interestingPaths: List<Path>
  )

  private fun logState(status: WindowsDefenderStatus.Status) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .withProjectId(project)
        .setKind(AndroidStudioEvent.EventKind.WINDOWS_DEFENDER_STATUS)
        .setWindowsDefenderStatus(WindowsDefenderStatus.newBuilder().setStatus(status))
    )
  }

  private fun logUserAction(actionEventType: BuildAttributionUiEvent.EventType) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT)
        .withProjectId(project)
        .setBuildAttributionUiEvent(BuildAttributionUiEvent.newBuilder().apply {
          eventType = actionEventType
        })
    )
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
  }

  override suspend fun execute(project: Project) {
    WindowsDefenderCheckService.getInstance(project).checkRealTimeProtectionStatus()
  }
}
