/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface.ZoomControlsPolicy
import com.android.tools.idea.ui.designer.EditorDesignSurface
import com.intellij.openapi.project.Project
import java.awt.LayoutManager
import java.util.function.Consumer

/**
 * @param actionHandlerProvider Allow a test to override myActionHandlerProvider when the surface is
 *   a mockito mock
 */
abstract class PreviewSurface<T : SceneManager>(
  val project: Project,
  val selectionModel: SelectionModel,
  val zoomControlsPolicy: ZoomControlsPolicy,
  layout: LayoutManager,
) : EditorDesignSurface(layout) {

  init {
    isOpaque = true
    isFocusable = false
  }

  protected open fun useSmallProgressIcon(): Boolean {
    return true
  }

  /**
   * All the selectable components in the design surface
   */
  abstract val selectableComponents: List<NlComponent>

  abstract val layoutManagerSwitcher: LayoutManagerSwitcher?

  private val myIssueListeners: MutableList<IssueListener> = ArrayList()

  val issueListener: IssueListener = IssueListener { issue: Issue? ->
    myIssueListeners.forEach(
      Consumer { listener: IssueListener -> listener.onIssueSelected(issue) }
    )
  }

  fun addIssueListener(listener: IssueListener) {
    myIssueListeners.add(listener)
  }

  fun removeIssueListener(listener: IssueListener) {
    myIssueListeners.remove(listener)
  }
}
