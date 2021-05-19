package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.idea.appinspection.ide.analytics.AppInspectionAnalyticsTrackerService
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/**
 * Listens to App Inspection tool window's state change events
 *
 * Currently, this is used to track analytics and to show an info bubble when tool window is minimized that app inspection is running in the
 * background.
 */
class AppInspectionToolWindowManagerListener(
  private val project: Project,
  private val appInspectionView: AppInspectionView
) : ToolWindowManagerListener {
  private var isWindowMinimizedBubbleShown = false
  private var wasVisible = false
  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val inspectionToolWindow = toolWindowManager.getToolWindow(APP_INSPECTION_ID) ?: return
    val visibilityChanged = inspectionToolWindow.isVisible != wasVisible
    wasVisible = inspectionToolWindow.isVisible
    if (visibilityChanged) {
      if (inspectionToolWindow.isVisible) {
        AppInspectionAnalyticsTrackerService.getInstance(project).trackToolWindowOpened()
      }
      else {
        AppInspectionAnalyticsTrackerService.getInstance(project).trackToolWindowHidden()
        if (!isWindowMinimizedBubbleShown && appInspectionView.isInspectionActive()) {
          isWindowMinimizedBubbleShown = true
          toolWindowManager.notifyByBalloon(APP_INSPECTION_ID, MessageType.INFO, AppInspectionBundle.message("inspection.is.running"))
        }
      }
    }
  }
}