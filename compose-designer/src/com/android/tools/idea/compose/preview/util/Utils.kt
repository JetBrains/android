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
package com.android.tools.idea.compose.preview.util

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.essentials.ComposePreviewEssentialsModeManager
import com.android.tools.idea.compose.preview.hasPreviewElements
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.projectsystem.isTestFile
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Segment
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import javax.swing.JComponent

fun Segment?.containsOffset(offset: Int) =
  this?.let { it.startOffset <= offset && offset <= it.endOffset } ?: false

/**
 * For a SceneView that contains a valid Compose Preview, get its root component, that must be a
 * ComposeViewAdapter.
 */
fun SceneView.getRootComponent(): NlComponent? {
  val root = sceneManager.model.components.firstOrNull()
  assert(root == null || root.tagName == COMPOSE_VIEW_ADAPTER_FQN) {
    "Expected the root component of a Compose Preview to be a $COMPOSE_VIEW_ADAPTER_FQN, but found ${root!!.tagName}"
  }
  return root
}

/** Returns true if the ComposeViewAdapter component of this SceneView is currently selected. */
fun SceneView.isRootComponentSelected() =
  getRootComponent()?.let { surface.selectionModel.isSelected(it) } == true

/** Create [ActionToolbar] with enabled navigation. */
fun createToolbarWithNavigation(rootComponent: JComponent, place: String, actions: List<AnAction>) =
  createToolbarWithNavigation(rootComponent, place, DefaultActionGroup(actions))

/** Create [ActionToolbar] with enabled navigation. */
fun createToolbarWithNavigation(rootComponent: JComponent, place: String, actions: ActionGroup) =
  ActionManager.getInstance().createActionToolbar(place, actions, true).apply {
    targetComponent = rootComponent
    layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    ActionToolbarUtil.makeToolbarNavigable(this)
    setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

/**
 * Whether fast preview is available. In addition to checking its normal availability from
 * [FastPreviewManager], we also verify that essentials mode is not enabled, because fast preview
 * should not be available in this case.
 */
fun isFastPreviewAvailable(project: Project) =
  FastPreviewManager.getInstance(project).isAvailable &&
    !ComposePreviewEssentialsModeManager.isEssentialsModeEnabled

fun DataContext.previewElement(): ComposePreviewElementInstance? =
  getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE)

/**
 * Whether this function is not in a test file and is properly annotated with
 * [COMPOSE_PREVIEW_ANNOTATION_FQN], considering indirect annotations when the Multipreview flag is
 * enabled, and validating the location of Previews
 *
 * @see [isValidPreviewLocation]
 */
internal fun KtNamedFunction.isValidComposePreview() =
  !isInTestFile() &&
    isValidPreviewLocation() &&
    this.toUElementOfType<UMethod>()?.let { it.hasPreviewElements() } == true

/**
 * Returns whether a `@Composable` [COMPOSE_PREVIEW_ANNOTATION_FQN] is defined in a valid location,
 * which can be either:
 * 1. Top-level functions
 * 2. Non-nested functions defined in top-level classes that have a default (no parameter)
 *    constructor
 */
internal fun KtNamedFunction.isValidPreviewLocation(): Boolean {
  if (isTopLevel) {
    return true
  }

  if (parentOfType<KtNamedFunction>() == null) {
    // This is not a nested method
    val containingClass = containingClass()
    if (containingClass != null) {
      // We allow functions that are not top level defined in top level classes that have a default
      // (no parameter) constructor.
      if (containingClass.isTopLevel() && containingClass.hasDefaultConstructor()) {
        return true
      }
    }
  }
  return false
}

private fun KtClass.hasDefaultConstructor() =
  allConstructors.isEmpty().or(allConstructors.any { it.valueParameters.isEmpty() })

private fun KtNamedFunction.isInTestFile() =
  isTestFile(this.project, this.containingFile.virtualFile)
