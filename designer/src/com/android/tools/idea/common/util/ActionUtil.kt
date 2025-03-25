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
package com.android.tools.idea.common.util

import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupWrapper
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction

/**
 * Wrapper that delegates whether the given [ActionGroup] is visible or not to the passed condition.
 */
class ShowGroupUnderConditionWrapper(
  delegate: ActionGroup,
  val isVisible: (DataContext) -> Boolean,
) : ActionGroupWrapper(delegate), CustomComponentAction {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val curVisibleStatus = e.presentation.isVisible
    e.presentation.isVisible = curVisibleStatus && isVisible(e.dataContext)
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(delegate, presentation, place)
}

/** Wrapper that delegates whether the given action is visible or not to the passed condition. */
class ShowUnderConditionWrapper(
  delegate: AnAction,
  private val isVisible: (DataContext) -> Boolean,
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
 * Wraps an [AnAction] to conditionally control its enabled state as well as control whether the
 * action can be performed.
 *
 * Enables the wrapped action only if [isEnabled] is `true`. When disabled, optionally displays a
 * reason using [reasonForDisabling]. The wrapped [AnAction] will only be able to be performed
 * through the [actionPerformed] method if [isEnabled] returns `true`.
 *
 * @param delegate The original action.
 * @param isEnabled Determines if the action should be enabled.
 * @param reasonForDisabling Optionally provides a reason for disabling.
 */
class EnableUnderConditionWrapper(
  delegate: AnAction,
  private val isEnabled: (context: DataContext) -> Boolean,
  private val reasonForDisabling: (context: DataContext) -> String? = { null },
) : AnActionWrapper(delegate), CustomComponentAction {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val delegateEnabledStatus = e.presentation.isEnabled
    e.presentation.isEnabled = delegateEnabledStatus && isEnabled(e.dataContext)
    if (!e.presentation.isEnabled) {
      reasonForDisabling(e.dataContext)?.let { e.presentation.description = it }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    // It sometimes takes a second or so for the action to update its presentation enabled
    // state, meaning the action can still be enabled when isEnabled returns false.
    // In those cases, we want to  prevent the user from performing the action.
    if (!isEnabled(e.dataContext)) {
      return
    }
    super.actionPerformed(e)
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(delegate, presentation, place)
}
