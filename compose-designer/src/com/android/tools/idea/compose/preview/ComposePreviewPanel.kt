/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeSceneUpdateListener
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RealTimeSessionClock
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout

/**
 * [WorkBench] panel used to contain all the compose preview elements.
 *
 * @param project the current open project
 * @param psiFilePointer an [SmartPsiElementPointer] pointing to the file being rendered within this panel. Used to handle
 *   which notifications should be displayed.
 * @param projectBuildStatusManager [ProjectBuildStatusManager] used to detect the current build status and show/hide the correct loading
 *   message.
 * @param navigationHandler the [PreviewNavigationHandler] used to handle the source code navigation when user clicks a component.
 * @param dataProvider the [DataProvider] to be used by this panel.
 * @param parentDisposable the [Disposable] to use as parent disposable for this panel.
 */
internal class ComposePreviewPanel(private val project: Project,
                                   private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
                                   private val projectBuildStatusManager: ProjectBuildStatusManager,
                                   navigationHandler: PreviewNavigationHandler,
                                   dataProvider: DataProvider,
                                   parentDisposable: Disposable) :
  WorkBench<DesignSurface>(project, "Compose Preview", null, parentDisposable) {
  private val log = Logger.getInstance(ComposePreviewPanel::class.java)

  private val sceneComponentProvider = ComposeSceneComponentProvider()
  private val delegateInteractionHandler = DelegateInteractionHandler()

  val mainSurface = createPreviewDesignSurface(
    project, navigationHandler, delegateInteractionHandler, dataProvider, parentDisposable) { surface, model ->
    LayoutlibSceneManager(model, surface, sceneComponentProvider, ComposeSceneUpdateListener(), { RealTimeSessionClock() })
  }

  private val staticPreviewInteractionHandler = NlInteractionHandler(mainSurface)
  private val interactiveInteractionHandler by lazy { LayoutlibInteractionHandler(mainSurface) }

  /**
   * Vertical splitter where the top component is the main Compose Preview panel and the bottom component, when visible, is an auxiliary
   * panel associated with the preview. For example, it can be an animation inspector that lists all the animations the preview has.
   */
  private val mainPanelSplitter = JBSplitter(true, 0.7f).apply { dividerWidth = 3 }

  private val notificationPanel = NotificationPanel(
    ExtensionPointName.create("com.android.tools.idea.compose.preview.composeEditorNotificationProvider"))

  init {
    // Start handling events for the static preview.
    delegateInteractionHandler.delegate = staticPreviewInteractionHandler

    val contentPanel = JPanel(BorderLayout()).apply {
      val actionsToolbar = ActionsToolbar(parentDisposable, mainSurface)
      add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

      val overlayPanel = object : JPanel() {
        // Since the overlay panel is transparent, we can not use optimized drawing or it will produce rendering artifacts.
        override fun isOptimizedDrawingEnabled(): Boolean = false
      }

      overlayPanel.apply {
        layout = OverlayLayout(this)

        add(notificationPanel)
        add(mainSurface)
      }

      mainPanelSplitter.firstComponent = overlayPanel
      add(mainPanelSplitter, BorderLayout.CENTER)
    }

    val issueErrorSplitter = IssuePanelSplitter(mainSurface, contentPanel)

    init(issueErrorSplitter, mainSurface, listOf(), false)
    showLoading(message("panel.building"))
  }

  /**
   * If the content is not already visible, shows a "Building" message.
   */
  fun showBuildingMessage() = UIUtil.invokeLaterIfNeeded {
    if (isMessageVisible) {
      showLoading(message("panel.building"))
      hideContent()
    }
  }

  /**
   * Hides the content if visible and displays the given [message].
   */
  fun showModalErrorMessage(message: String) = UIUtil.invokeLaterIfNeeded {
    log.debug("showModelErrorMessage: $message")
    loadingStopped(message)
  }

  /**
   * Method called to force an update on the notifications for the given [FileEditor].
   */
  fun updateNotifications(parentEditor: FileEditor) = UIUtil.invokeLaterIfNeeded {
    if (Disposer.isDisposed(this) || project.isDisposed || !parentEditor.isValid) return@invokeLaterIfNeeded

    notificationPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
  }

  /**
   * Method called to ask all notifications to update.
   */
  private fun updateNotifications() = UIUtil.invokeLaterIfNeeded {
    // Make sure all notifications are cleared-up
    if (!project.isDisposed) {
      EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile)
    }
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  fun updateVisibilityAndNotifications() = UIUtil.invokeLaterIfNeeded {
    if (isMessageVisible && projectBuildStatusManager.status == NeedsBuild) {
      log.debug("Needs successful build")
      showModalErrorMessage(message("panel.needs.build"))
    }
    else {
      log.debug("Show content")
      hideLoading()
      showContent()
    }

    updateNotifications()
  }

  /**
   * Sets whether the scene overlay is visible. If true, the outline for the components will be visible and selectable.
   */
  var hasComponentsOverlay: Boolean
    get() = sceneComponentProvider.enabled
    set(value) {
      sceneComponentProvider.enabled = value
    }

  /**
   * Sets whether the panel is in "interactive" mode. If it's in interactive mode, the clicks will be forwarded to the
   * code being previewed using the [LayoutlibInteractionHandler].
   */
  var isInteractive: Boolean = false
    set(value) {
      field = value
      if (value) {
        delegateInteractionHandler.delegate = interactiveInteractionHandler
      }
      else {
        delegateInteractionHandler.delegate = staticPreviewInteractionHandler
      }
    }

  var bottomPanel: JComponent?
    set(value) {
      mainPanelSplitter.secondComponent = value
    }
    get() = mainPanelSplitter.secondComponent
}