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
package com.android.tools.idea.lint.common

import com.android.tools.lint.detector.api.Issue
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction.Priority
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandService
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting

/**
 * Base class for Lint quick fixes using the ModCommand APIs.
 *
 * The quick fixes run inside read actions on background threads, both for intention preview purposes and actual operation. The quick fix
 * logic should be provided as a [ModCommandAction] implementation. When the logic is simple enough that only the code surrounding a single
 * PSI element is modified, a [PsiUpdateModCommandAction] would probably be the simplest. In more complex scenarios, when the quick fix
 * ventures outside a single file, switches to a different editor, presents a list of conflicts, etc., a custom [ModCommandAction] can be
 * used, and the responsibility for maintaining smart PSI pointers falls on the implementor.
 *
 * Delegation provides the flexibility to apply cross-cutting concerns such as logging/analytics, and to adapt to [LocalQuickFix] and
 * [IntentionAction] as needed.
 */
@Suppress("UnstableApiUsage")
class ModCommandLintQuickFix(private val quickFixAction: ModCommandAction) : LintIdeQuickFix {

  private var priority = Priority.HIGH

  override fun setPriority(priority: Priority) {
    this.priority = priority
  }

  override fun getPriority(): Priority {
    return priority
  }

  override fun getName(): String {
    return quickFixAction.familyName
  }

  override fun startInWriteAction() = false

  fun asIntention(issue: Issue, project: Project): IntentionAction {
    return ModCommandWrapper(quickFixAction, priority, issue, project).asIntention()
  }

  fun asLocalQuickFix(issue: Issue, project: Project): LocalQuickFix {
    return ModCommandService.getInstance().wrapToQuickFix(ModCommandWrapper(quickFixAction, priority, issue, project))
  }

  @VisibleForTesting
  fun rawIntention(): IntentionAction {
    return quickFixAction.asIntention()
  }


  /**
   * Convenience wrapper for injecting default behaviors, such as custom priority and analytics.
   */
  private class ModCommandWrapper(
    private val action: ModCommandAction,
    private val priority: Priority,
    private val issue: Issue,
    private val project: Project,
  ) : ModCommandAction {

    override fun getFamilyName() = action.familyName

    override fun getPresentation(context: ActionContext): Presentation? {
      return action.getPresentation(context)?.withPriority(priority)
    }

    override fun perform(context: ActionContext): ModCommand {
      val presentation = getPresentation(context) ?: return ModCommand.nop()
      if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
        LintIdeSupport.get().logQuickFixInvocation(project, issue, presentation.name)
      }
      return action.perform(context)
    }
  }
}
