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
package com.android.tools.idea.compose.preview

import com.android.testutils.delayUntilCondition
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.SurfaceInteractable
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeScreenViewProvider
import com.android.tools.idea.preview.focus.FocusMode
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CompletableDeferred

/** A test implementation of [ComposePreviewView]. */
class TestComposePreviewView : ComposePreviewView, JPanel {
  override val mainSurface: NlDesignSurface
  private val onRefreshCompletedCallback: () -> Unit

  var interactionPaneProvider: () -> JComponent? = { null }
  var isInteractive = false
  val delegateInteractionHandler = DelegateInteractionHandler()

  override val component: JComponent
    get() = this

  override var bottomPanel: JComponent? = null
  override val isMessageBeingDisplayed: Boolean = false
  override var hasContent: Boolean = true
  override var hasRendered: Boolean = true
  override var focusMode: FocusMode? = null

  private val nextRefreshLock = Any()
  private var nextRefreshListener: CompletableDeferred<Unit>? = null
  val refreshCompletedListeners: MutableList<() -> Unit> = mutableListOf()

  constructor(
    parentDisposable: Disposable,
    project: Project,
    navigationHandler: NavigationHandler = ComposePreviewNavigationHandler(),
    onRefreshCompletedCallback: () -> Unit = {},
  ) {
    this.onRefreshCompletedCallback = onRefreshCompletedCallback
    this.mainSurface =
      createMainDesignSurfaceBuilder(
          project,
          navigationHandler,
          delegateInteractionHandler,
          { null },
          parentDisposable,
          ComposeSceneComponentProvider(),
          ComposeScreenViewProvider(NopComposePreviewManager()),
          { isInteractive },
        )
        .setInteractableProvider {
          object : SurfaceInteractable(it) {
            override val interactionPane: JComponent
              get() = interactionPaneProvider() ?: super.interactionPane
          }
        }
        .build()
    layout = BorderLayout()
    add(mainSurface, BorderLayout.CENTER)
  }

  constructor(mainSurface: NlDesignSurface, onRefreshCompletedCallback: () -> Unit = {}) {
    this.onRefreshCompletedCallback = onRefreshCompletedCallback
    this.mainSurface = mainSurface
    layout = BorderLayout()
    add(mainSurface, BorderLayout.CENTER)
  }

  override fun updateNotifications(parentEditor: FileEditor) {}

  override fun updateVisibilityAndNotifications() {}

  override fun updateProgress(message: String) {}

  override fun onRefreshCancelledByTheUser() {}

  override fun onRefreshCompleted() {
    onRefreshCompletedCallback()
    synchronized(nextRefreshLock) {
        val current = nextRefreshListener
        nextRefreshListener = null
        current
      }
      ?.complete(Unit)
    refreshCompletedListeners.forEach { it.invoke() }
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

  override fun onLayoutlibNativeCrash(onLayoutlibReEnable: () -> Unit) {}

  suspend fun runAndWaitForRefresh(runnable: () -> Unit) {
    var refreshCompleted = false
    val listener = { refreshCompleted = true }
    refreshCompletedListeners.add(listener)
    runnable()
    delayUntilCondition(250) { refreshCompleted }
    refreshCompletedListeners.remove(listener)
  }
}
