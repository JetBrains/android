/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.ide.common.rendering.api.Bridge
import com.android.tools.adtui.stdui.ActionData
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.glance.preview.GlancePreviewBundle.message
import com.android.tools.idea.preview.mvvm.PreviewView
import com.android.tools.idea.preview.mvvm.PreviewViewModel
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.projectsystem.requestBuild
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.UIUtil
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.apache.commons.lang.time.DurationFormatUtils

/** [PreviewViewModel] for the Glance previews. */
class GlancePreviewViewModel(
  private val previewView: PreviewView,
  private val projectBuildStatusManager: ProjectBuildStatusManager,
  private val project: Project,
  private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
  private val hasRenderErrors: () -> Boolean
) : PreviewViewModel, PreviewViewModelStatus {
  private val refreshCallsCount = AtomicInteger(0)
  private val hasRendered = AtomicBoolean(false)
  private val hasPreviews = AtomicBoolean(false)

  override fun checkForNativeCrash(runnable: Runnable): Boolean {
    if (Bridge.hasNativeCrash()) {
      val message =
        "The preview has been disabled following a crash in the rendering engine. If the problem persists, please report the issue."
      val actionData =
        ActionData("Re-enable rendering") {
          Bridge.setNativeCrash(false)
          previewView.showLoadingMessage("Loading...")
          runnable.run()
        }
      previewView.showErrorMessage(message, null, actionData)
      return true
    }
    return false
  }

  override fun refreshStarted() {
    refreshCallsCount.incrementAndGet()
    updateNotifications()
  }

  override fun refreshFinished() {
    refreshCallsCount.decrementAndGet()
    updateNotifications()
  }

  override fun beforePreviewsRefreshed() {
    updateViewAndNotifications()
  }

  override fun afterPreviewsRefreshed() {
    hasRendered.set(true)
    updateViewAndNotifications()
  }

  override fun buildStarted() {
    updateViewAndNotifications()
  }

  override fun buildSucceeded() {
    updateViewAndNotifications()
  }

  override fun buildFailed() {
    updateViewAndNotifications()
  }

  override fun setHasPreviews(hasPreviews: Boolean) {
    this.hasPreviews.set(hasPreviews)
  }

  override fun refreshCompleted(isCancelled: Boolean, durationNanos: Long) {
    updateViewAndNotifications()

    invokeLater {
      if (hasRendered.get()) {
        // Only notify the preview refresh time if there are previews to show.
        val durationString = Duration.ofMillis(durationNanos / 1_000_000).toDisplayString()
        val notification =
          Notification(
            PREVIEW_NOTIFICATION_GROUP_ID,
            message("event.log.refresh.title"),
            message("event.log.refresh.total.elapsed.time", durationString),
            NotificationType.INFORMATION
          )
        Notifications.Bus.notify(notification, project)
      }
    }
  }

  override fun onEnterSmartMode() {
    updateViewAndNotifications()
  }

  override fun activate() {
    updateViewAndNotifications()
  }

  private fun updateNotifications() =
    UIUtil.invokeLaterIfNeeded {
      previewView.updateToolbar()
      if (!project.isDisposed) {
        EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile)
      }
    }
  private fun updateViewAndNotifications() =
    UIUtil.invokeLaterIfNeeded {
      updateView()
      updateNotifications()
    }

  private val buildAndRefreshAction: ActionData
    get() {
      val actionDataText =
        "${message("panel.needs.build.action.text")}${getBuildAndRefreshShortcut().asString()}"
      return ActionData(actionDataText) {
        psiFilePointer.element?.virtualFile?.let { project.requestBuild(it) }
        // workbench.repaint() // Repaint the workbench, otherwise the text and link will keep
        // displaying if the mouse is hovering the link
      }
    }
  private fun updateView() {
    if (hasRendered.get()) {
      previewView.showContent()
    } else {
      when {
        DumbService.getInstance(project).isDumb ->
          previewView.showLoadingMessage(message("panel.indexing"))
        projectBuildStatusManager.isBuilding ->
          previewView.showLoadingMessage(message("panel.building"))
        projectBuildStatusManager.status == ProjectStatus.NeedsBuild -> {
          previewView.showErrorMessage(message("panel.needs.build"), null, buildAndRefreshAction)
        }
        else -> previewView.showLoadingMessage(message("panel.initializing"))
      }
    }
  }

  override val isRefreshing: Boolean
    get() = refreshCallsCount.get() > 0

  override val hasErrorsAndNeedsBuild: Boolean
    get() = hasPreviews.get() && (!hasRendered.get() || hasRenderErrors())

  override val hasSyntaxErrors: Boolean
    get() = WolfTheProblemSolver.getInstance(project).isProblemFile(psiFilePointer.virtualFile)

  override val isOutOfDate: Boolean
    get() = projectBuildStatusManager.status is ProjectStatus.OutOfDate

  override val previewedFile: PsiFile?
    get() = psiFilePointer.element
}

/**
 * Converts the given duration to a display string that contains minutes (if the duration is greater
 * than 60s), seconds and milliseconds.
 */
private fun Duration.toDisplayString(): String {
  val durationMs = toMillis()
  val durationFormat = if (durationMs >= 60_000) "mm 'm' ss 's' SSS 'ms'" else "ss 's' SSS 'ms'"
  return DurationFormatUtils.formatDuration(durationMs, durationFormat, false)
}

private const val PREVIEW_NOTIFICATION_GROUP_ID = "Glance Preview Notification"
