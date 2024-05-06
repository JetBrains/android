/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager

/**
 * Helper method that navigates back to the previous [PreviewMode] for all [PreviewModeManager]s in
 * the given [AnActionEvent]'s [DataContext].
 *
 * @param e the [AnActionEvent] holding the context of the action
 */
fun navigateBack(e: AnActionEvent) {
  findPreviewModeManagersForContext(e.dataContext).forEach { it.restorePrevious() }
}

/**
 * Returns a list of all [PreviewModeManager]s related to the current context (which is implied to
 * be bound to a particular file). The search is done among the open preview parts and
 * [PreviewRepresentation]s (if any) of open file editors.
 *
 * This call might access the [CommonDataKeys.VIRTUAL_FILE] so it should not be called in the EDT
 * thread. For actions using it, they should use [ActionUpdateThread.BGT].
 */
internal fun findPreviewModeManagersForContext(context: DataContext): List<PreviewModeManager> {
  context.getData(PreviewModeManager.KEY)?.let {
    // The context is associated to a PreviewModeManager so return it
    return listOf(it)
  }

  // Fallback to finding the PreviewModeManager by looking into all the editors
  val project = context.getData(CommonDataKeys.PROJECT) ?: return emptyList()
  val file = context.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyList()

  return FileEditorManager.getInstance(project)?.getAllEditors(file)?.mapNotNull {
    it.getPreviewModeManager()
  } ?: emptyList()
}

/**
 * Returns the [PreviewModeManager] or null if this [FileEditor]'s preview representation is not a
 * [PreviewModeManager].
 */
private fun FileEditor.getPreviewModeManager(): PreviewModeManager? =
  when (this) {
    is MultiRepresentationPreview -> this.currentRepresentation as? PreviewModeManager
    is TextEditorWithMultiRepresentationPreview<out MultiRepresentationPreview> ->
      this.preview.currentRepresentation as? PreviewModeManager
    else -> null
  }

private class PreviewNonInteractiveActionWrapper(actions: List<AnAction>) :
  DefaultActionGroup(actions) {
  override fun update(e: AnActionEvent) {
    super.update(e)

    e.getData(PreviewModeManager.KEY)?.let { e.presentation.isVisible = it.mode.value.isNormal }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

/**
 * Makes the given list of actions only visible when the preview is not in interactive or animation
 * modes. Returns an [ActionGroup] that handles the visibility.
 */
fun List<AnAction>.visibleOnlyInStaticPreview(): ActionGroup =
  PreviewNonInteractiveActionWrapper(this)

/**
 * Makes the given action only visible when the Compose preview is not in interactive or animation
 * modes. Returns an [ActionGroup] that handles the visibility.
 */
fun AnAction.visibleOnlyInStaticPreview(): ActionGroup = listOf(this).visibleOnlyInStaticPreview()

/** Hide the given actions if the [sceneView] contains render errors. */
fun List<AnAction>.hideIfRenderErrors(): List<AnAction> = map {
  ShowUnderConditionWrapper(it) { context -> !hasSceneViewErrors(context) }
}

/** Wrapper that delegates whether the given action is visible or not to the passed condition. */
private class ShowUnderConditionWrapper(
  delegate: AnAction,
  private val isVisible: (DataContext) -> Boolean
) : AnActionWrapper(delegate), CustomComponentAction {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val curVisibleStatus = e.presentation.isVisible
    e.presentation.isVisible = curVisibleStatus && isVisible(e.dataContext)
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(delegate, presentation, place)
}

/**
 * The given disables the actions if the given predicate returns true.
 *
 * @param predicate the predicate that returns true if the action should be disabled.
 */
fun List<AnAction>.disabledIf(predicate: (DataContext) -> Boolean): List<AnAction> = map {
  EnableUnderConditionWrapper(it) { context -> !predicate(context) }
}

/**
 * Returns if the preview attached to the given [DataContext] is refreshing or not. The preview
 * needs to have set a [PreviewViewModelStatus] using the [PREVIEW_VIEW_MODEL_STATUS] key in the
 * [DataContext].
 *
 * @param dataContext
 */
fun isPreviewRefreshing(dataContext: DataContext) =
  dataContext.getData(PREVIEW_VIEW_MODEL_STATUS)?.isRefreshing == true

fun hasSceneViewErrors(dataContext: DataContext) =
  dataContext.getData(SCENE_VIEW)?.hasRenderErrors() == true

/**
 * Wrapper that delegates whether the given action is enabled or not to the passed condition. If
 * [isEnabled] returns true, the `delegate` action will be shown as disabled.
 */
private class EnableUnderConditionWrapper(
  delegate: AnAction,
  private val isEnabled: (context: DataContext) -> Boolean
) : AnActionWrapper(delegate), CustomComponentAction {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val delegateEnabledStatus = e.presentation.isEnabled
    e.presentation.isEnabled = delegateEnabledStatus && isEnabled(e.dataContext)
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(delegate, presentation, place)
}
