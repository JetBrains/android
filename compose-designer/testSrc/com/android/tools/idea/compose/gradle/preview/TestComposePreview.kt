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
package com.android.tools.idea.compose.gradle.preview

import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.common.surface.SurfaceInteractable
import com.android.tools.idea.compose.preview.ComposePreviewView
import com.android.tools.idea.compose.preview.createMainDesignSurfaceBuilder
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CompletableDeferred

internal val SceneViewPeerPanel.displayName: String
  get() = sceneView.sceneManager.model.modelDisplayName ?: ""

internal class TestComposePreviewView(
  parentDisposable: Disposable,
  project: Project,
  navigationHandler: NavigationHandler = ComposePreviewNavigationHandler()
) : ComposePreviewView, JPanel() {
  var interactionPaneProvider: () -> JComponent? = { null }
  val delegateInteractionHandler = DelegateInteractionHandler()

  override val mainSurface: NlDesignSurface =
    createMainDesignSurfaceBuilder(
        project,
        navigationHandler,
        delegateInteractionHandler,
        { null },
        parentDisposable,
        ComposeSceneComponentProvider()
      )
      .setInteractableProvider {
        object : SurfaceInteractable(it) {
          override val interactionPane: JComponent
            get() = interactionPaneProvider() ?: super.interactionPane
        }
      }
      .build()
  override val component: JComponent
    get() = this
  override var bottomPanel: JComponent? = null
  override val isMessageBeingDisplayed: Boolean = false
  override var hasContent: Boolean = false
  override var hasRendered: Boolean = false

  private val nextRefreshLock = Any()
  private var nextRefreshListener: CompletableDeferred<Unit>? = null

  init {
    layout = BorderLayout()
    add(mainSurface, BorderLayout.CENTER)
  }

  override fun updateNotifications(parentEditor: FileEditor) {}

  override fun updateVisibilityAndNotifications() {}

  override fun updateProgress(message: String) {}

  override fun onRefreshCancelledByTheUser() {}

  override fun onRefreshCompleted() {
    synchronized(nextRefreshLock) {
        val current = nextRefreshListener
        nextRefreshListener = null
        current
      }
      ?.complete(Unit)
  }

  /**
   * Returns a [CompletableDeferred] that completes when the next (or current if it's running)
   * refresh finishes.
   */
  fun getOnRefreshCompletable() =
    synchronized(nextRefreshLock) {
      if (nextRefreshListener == null) nextRefreshListener = CompletableDeferred()
      nextRefreshListener!!
    }
}
