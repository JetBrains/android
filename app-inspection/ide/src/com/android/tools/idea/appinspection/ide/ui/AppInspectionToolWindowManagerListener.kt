package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.idea.appinspection.ide.analytics.AppInspectionAnalyticsTrackerService
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/**
 * Listens to App Inspection tool window's state change events
 *
 * Currently, this is used to track analytics and to show an info bubble when tool window is
 * minimized that app inspection is running in the background.
 */
class AppInspectionToolWindowManagerListener(
  private val project: Project,
  private val ideServices: AppInspectionIdeServices,
  private val toolWindow: ToolWindow,
  private val appInspectionView: AppInspectionView,
) : ToolWindowManagerListener {
  private var wasVisible = toolWindow.isVisible

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val isVisible = toolWindow.isVisible
    val visibilityChanged = isVisible != wasVisible
    wasVisible = isVisible

    if (visibilityChanged) {
      if (isVisible) {
        AppInspectionAnalyticsTrackerService.getInstance(project).trackToolWindowOpened()
      } else {
        AppInspectionAnalyticsTrackerService.getInstance(project).trackToolWindowHidden()
        if (appInspectionView.isInspectionActive()) {
          ideServices.showNotification(
            AppInspectionBundle.message("inspection.is.running"),
            hyperlinkClicked = { appInspectionView.stopInspectors() }
          )
        }
      }
    }
  }
}
