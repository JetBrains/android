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
package com.android.tools.idea.appinspection.ide.ui

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.ClassUtil
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import kotlinx.coroutines.withContext

class AppInspectionToolWindow(toolWindow: ToolWindow, private val project: Project) : Disposable {
  companion object {
    @JvmStatic
    fun show(project: Project, callback: Runnable? = null) =
      ToolWindowManagerEx.getInstanceEx(project).getToolWindow(APP_INSPECTION_ID)?.show(callback)
  }

  private val ideServices: AppInspectionIdeServices =
    object : AppInspectionIdeServices {
      private val notificationGroup =
        NotificationGroup.toolWindowGroup(
          APP_INSPECTION_ID,
          APP_INSPECTION_ID,
          true,
          PluginId.getId("org.jetbrains.android")
        )

      @UiThread override fun showToolWindow() = toolWindow.show(null)

      @UiThread
      override fun showNotification(
        content: String,
        title: String,
        severity: AppInspectionIdeServices.Severity,
        hyperlinkClicked: () -> Unit
      ) {
        val type =
          when (severity) {
            AppInspectionIdeServices.Severity.INFORMATION -> NotificationType.INFORMATION
            AppInspectionIdeServices.Severity.ERROR -> NotificationType.ERROR
          }

        notificationGroup
          .createNotification(title, content, type)
          .setListener(
            object : NotificationListener.Adapter() {
              override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
                hyperlinkClicked()
                notification.expire()
              }
            }
          )
          .notify(project)
      }

      override suspend fun navigateTo(codeLocation: AppInspectionIdeServices.CodeLocation) {
        val fqcn = codeLocation.fqcn
        val navigatable: Navigatable? = runReadAction {
          if (fqcn != null) {
            ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(project), fqcn)
          } else {
            val fileName = codeLocation.fileName!!
            FilenameIndex.getFilesByName(
                project,
                fileName,
                GlobalSearchScope.allScope(project),
                false
              )
              .firstOrNull()
              ?.virtualFile
              ?.let { virtualFile ->
                OpenFileDescriptor(
                  project,
                  virtualFile,
                  codeLocation.lineNumber?.let { it - 1 } ?: -1,
                  0
                )
              }
          }
        }

        if (navigatable != null) {
          withContext(AndroidDispatchers.uiThread) { navigatable.navigate(true) }
        }
      }

      override fun isTabSelected(inspectorId: String): Boolean {
        return appInspectionView.isTabSelected(inspectorId)
      }
    }

  // Coroutine scope tied to the lifecycle of this tool window. It will be cancelled when the tool
  // window is disposed.
  private val scope = AndroidCoroutineScope(this)

  private val appInspectionView =
    AppInspectionView(
      project,
      AppInspectionDiscoveryService.instance.apiServices,
      ideServices,
      scope,
      AndroidDispatchers.uiThread,
      isPreferredProcess = { RecentProcess.isRecentProcess(it, project) }
    )
  val component: JComponent = appInspectionView.component

  init {
    Disposer.register(this, appInspectionView)
    project
      .messageBus
      .connect(this)
      .subscribe(
        ToolWindowManagerListener.TOPIC,
        AppInspectionToolWindowManagerListener(project, ideServices, toolWindow, appInspectionView)
      )
  }

  override fun dispose() {
    // Although we do nothing here, because this class is disposable, other components can register
    // against it
  }
}
