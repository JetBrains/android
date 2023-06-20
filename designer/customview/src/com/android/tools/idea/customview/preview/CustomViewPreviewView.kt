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
package com.android.tools.idea.customview.preview

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.OverlayLayout

/**
 * UI of the [CustomViewPreviewRepresentation].
 */
internal class CustomViewPreviewView(
  surfaceBuilder: NlDesignSurface.Builder,
  parentDisposable: Disposable,
  project: Project,
  psiFile: PsiFile,
) {
  internal val surface = surfaceBuilder.build().apply {
    name = "Custom View"
  }

  private val actionsToolbar = invokeAndWaitIfNeeded {
    ActionsToolbar(parentDisposable, surface)
  }

  internal val notificationsPanel = NotificationPanel(
    ExtensionPointName.create("com.android.tools.idea.customview.preview.customViewEditorNotificationProvider")
  )

  private val editorPanel = JPanel(BorderLayout()).apply {
    add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

    val overlayPanel = object : JPanel() {
      // Since the overlay panel is transparent, we can not use optimized drawing or it will produce rendering artifacts.
      override fun isOptimizedDrawingEnabled(): Boolean = false
    }

    overlayPanel.apply {
      layout = OverlayLayout(this)

      add(notificationsPanel)
      add(surface)
    }

    add(overlayPanel, BorderLayout.CENTER)
  }

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  internal val workbench: WorkBench<DesignSurface<*>> =
    object : WorkBench<DesignSurface<*>>(project, "Main Preview", null, parentDisposable), DataProvider {
      override fun getData(dataId: String): Any? = if (DESIGN_SURFACE.`is`(dataId)) surface else null
    }.apply {
      val issuePanelSplitter = IssuePanelSplitter(psiFile.virtualFile, surface, editorPanel)
      init(issuePanelSplitter, surface, listOf(), false)
    }

}