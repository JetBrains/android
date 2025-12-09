/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.startup

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionStub
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionWrapperUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.EmptyActionGroup
import com.intellij.openapi.actionSystem.PerformWithDocumentsCommitted.isPerformWithDocumentsCommitted
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.application

object Actions {
  @JvmStatic
  fun hideAction(actionManager: ActionManager, actionId: String) {
    actionManager.getActionOrStub(actionId) ?: return
    val replacement = if (actionManager.isGroup(actionId)) EmptyActionGroup() else DumbAwareEmptyAction()
    actionManager.replaceAction(actionId, replacement)
  }

  @JvmStatic
  fun hideAction(actionManager: ActionManager, actionId: String, condition: (AnActionEvent) -> Boolean) {
    val existingAction = (actionManager.getExistingActionProvider(actionId)) ?: return
    val replacement =
      if (actionManager.isGroup(actionId)) EmptyActionGroup()
      else ConditionalActionWrapper(delegate = existingAction, replacement = DumbAwareEmptyAction(), condition)
    actionManager.replaceAction(actionId, replacement)
  }

  @JvmStatic
  fun replaceAction(actionManager: ActionManager, actionId: String, newAction: AnAction) {
    if (actionManager.getActionOrStub(actionId) != null) {
      actionManager.replaceAction(actionId, newAction)
    } else {
      actionManager.registerAction(actionId, newAction)
    }
  }

  @JvmStatic
  fun replaceAction(actionManager: ActionManager, actionId: String, newAction: AnAction, condition: (AnActionEvent) -> Boolean) {
    val existingAction = actionManager.getExistingActionProvider(actionId)
    val replacement = ConditionalActionWrapper(delegate = existingAction ?: DumbAwareEmptyAction().let { { it } }, replacement = newAction,
                                               condition)
    if (existingAction != null) {
      actionManager.replaceAction(actionId, replacement)
    } else {
      actionManager.registerAction(actionId, replacement)
    }
  }

  @JvmStatic
  fun moveAction(
    actionManager: ActionManager,
    actionId: String,
    oldGroupId: String,
    groupId: String,
    constraints: Constraints,
  ) {
    val action = actionManager.getActionOrStub(actionId)
    val group = actionManager.getAction(groupId)
    val oldGroup = actionManager.getAction(oldGroupId)
    if (action != null && oldGroup is DefaultActionGroup && group is DefaultActionGroup) {
      oldGroup.remove(action, actionManager)
      group.add(action, constraints, actionManager)
    }
  }
}


/**
 * [com.intellij.openapi.actionSystem.AnActionWrapper] inspired wrapper that delegates to either of two delegates based on a provided
 * condition.
 */
class ConditionalActionWrapper(
  delegate: () -> AnAction,
  val replacement: AnAction,
  private val replaceCondition: (e: AnActionEvent) -> Boolean,
): AnAction() {
  // Action update/perform methods are not required to be thread safe even though update now runs in the BGT.
  val delegate: AnAction by lazy(mode = LazyThreadSafetyMode.PUBLICATION) {
    delegate().also { delegate ->
      // Something deprecated that we do not support.
      if (isPerformWithDocumentsCommitted(delegate)) {
        error("Action $delegate cannot be wrapped. isPerformWithDocumentsCommitted(delegate) returns true.")
      }

      // Prevent these cases from happening at all
      if (delegate.isDumbAware && !replacement.isDumbAware) {
        error("Cannot wrap dumb-aware $delegate with non-dumb-aware $replacement.")
      }
      if (delegate.actionUpdateThread == ActionUpdateThread.BGT && replacement.actionUpdateThread != ActionUpdateThread.BGT) {
        error("Replacement $replacement must update on BGT, like $delegate.")
      }
      if (delegate.isInInjectedContext != replacement.isInInjectedContext) {
        error("Replacement $replacement should have isInInjectedContext=${delegate.isInInjectedContext}, like $delegate.")
      }
    }
  }

  fun getWrappedActionFor(e: AnActionEvent): AnAction {
    return if (replaceCondition(e)) replacement else delegate
  }

  override fun update(e: AnActionEvent) {
    ActionWrapperUtil.update(e, this, getWrappedActionFor(e))
  }

  override fun beforeActionPerformedUpdate(e: AnActionEvent) {
    getWrappedActionFor(e).beforeActionPerformedUpdate(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    ActionWrapperUtil.actionPerformed(e, this, getWrappedActionFor(e))
  }

  override fun isDumbAware(): Boolean = delegate.isDumbAware

  override fun getActionUpdateThread(): ActionUpdateThread = delegate.actionUpdateThread

  override fun isInInjectedContext(): Boolean = delegate.isInInjectedContext
}

class DumbAwareEmptyAction () : AnAction() {
  override fun actionPerformed(e: AnActionEvent) = Unit
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun isDumbAware(): Boolean = true
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
  }
}

private fun ActionManager.getExistingActionProvider(actionId: String): (() -> AnAction)? {
  // Workaround: We cannot use getAction() here as it triggers action instantiation too soon to be safe. However, if we get a stub and
  // register a replacement action under the same id, we cannot later unstub the action. A solution is to re-register the sub under a
  // different action id until we need it and unstub it later and then unregister so that it does not appear in action searches etc.
  val existingAction = this.getActionOrStub(actionId) ?: return null
  if (existingAction is ActionStub) {
    val renamedId = actionId + "_renamedStub_" + existingAction.hashCode().toULong().toString()
    thisLogger().info("Re-registering action stub for $actionId as $renamedId")
    registerAction(renamedId, ActionStub(
      existingAction.className,
      renamedId,
      existingAction.plugin,
      existingAction.iconPath,
      existingAction.projectType,
      { existingAction.templatePresentation }
    ))
    // Action update/perform methods are not required to be thread safe even though update now runs in the BGT.
    val delayedAction by lazy(mode = LazyThreadSafetyMode.PUBLICATION) {
      thisLogger().info("Unstubbing: $renamedId")
      val baseAction = getAction(renamedId)
      application.invokeLater {
        getAction(actionId) // Make sure lazy has finished initialization.
        unregisterAction(renamedId)
        thisLogger().info("Unregistering already unstubbed: $renamedId")
      }
      baseAction
    }
    return { delayedAction }
  }
  else {
    return { existingAction }
  }
}