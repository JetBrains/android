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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.pickers.preview.utils.addNewValueArgument
import com.android.tools.idea.compose.preview.util.containingFile
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintOrigin
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintSuppressTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

/** [VisualLintIssueProvider] to be used when dealing with Compose. */
class ComposeVisualLintIssueProvider(parentDisposable: Disposable) :
  VisualLintIssueProvider(parentDisposable) {

  fun onUiCheckStart(instanceId: String) {
    uiCheckInstanceId = instanceId
    getIssues().forEach { it.unfreeze() }
  }

  fun onUiCheckStop() {
    uiCheckInstanceId = null
    getIssues().forEach { it.freeze() }
  }

  override fun customizeIssue(issue: VisualLintRenderIssue) {
    val model = issue.models.firstOrNull() ?: return
    val previewElement = model.dataContext.previewElement() ?: return

    issue.customizeIsSuppressed {
      val suppressedTypes = runReadAction {
        previewElement.previewElementDefinition?.let { pointer ->
          val annotationEntry = (pointer.element as? KtAnnotationEntry) ?: return@let emptyList()
          val composableFunction =
            annotationEntry.parentOfType<KtFunction>() ?: return@let emptyList()
          val suppress =
            composableFunction.annotationEntries.find { it.fqNameMatches("kotlin.Suppress") }
              ?: return@let emptyList()
          suppress.valueArguments
            .mapNotNull { it.getArgumentExpression()?.text }
            .mapNotNull {
              VisualLintErrorType.getTypeByIgnoredAttribute(it.substring(1, it.length - 1))
            }
        } ?: emptyList()
      }
      issue.type in suppressedTypes
    }

    issue.addSuppress(
      Issue.Suppress(
        message("ui.check.mode.suppress.action.title"),
        message("ui.check.mode.suppress.action.description", issue.type),
        ComposeVisualLintSuppressTask(model.facet, model.project, previewElement, issue.type),
      )
    )

    if (StudioFlags.COMPOSE_UI_CHECK_AI_QUICK_FIX.get()) {
      issue.addFix(
        Issue.Fix(
          message("ui.check.mode.quick.fix.ai.title"),
          message("ui.check.mode.quick.fix.ai.description"),
          ComposeVisualLintAiFix(model.project, issue, runReadAction { previewElement.methodFqn }),
        )
      )
    }
  }

  override fun dispose() {
    super.dispose()
    onUiCheckStop()
  }
}

/**
 * Task used to suppress a Visual Lint error happening in Compose. It adds a [Suppress] annotation
 * to the Composable that created the [previewElement], adding the type of error to suppress as an
 * argument to the annotation.
 */
class ComposeVisualLintSuppressTask(
  private val facet: AndroidFacet,
  private val project: Project,
  private val previewElement: PsiComposePreviewElement,
  private val issueType: VisualLintErrorType,
) : VisualLintSuppressTask {

  override fun run() {
    VisualLintUsageTracker.getInstance()
      .trackIssueIgnored(issueType, VisualLintOrigin.UI_CHECK, facet)
    val previewElementDefinitionPtr = previewElement.previewElementDefinition ?: return
    var composableFunctionPtr: SmartPsiElementPointer<KtFunction>? = null
    var suppressPtr: SmartPsiElementPointer<KtAnnotationEntry>? = null
    ApplicationManager.getApplication().runReadAction {
      val annotationEntry =
        (previewElementDefinitionPtr.element as? KtAnnotationEntry) ?: return@runReadAction
      val composableFunction = annotationEntry.parentOfType<KtFunction>() ?: return@runReadAction
      composableFunctionPtr = composableFunction.createSmartPointer()
      suppressPtr =
        composableFunction.annotationEntries
          .find { it.fqNameMatches("kotlin.Suppress") }
          ?.createSmartPointer()
    }

    val composableFunction = composableFunctionPtr?.element ?: return

    WriteCommandAction.runWriteCommandAction(
      project,
      issueType.toSuppressActionDescription(),
      null,
      {
        if (suppressPtr != null) {
          suppressPtr
            ?.element
            ?.addNewValueArgument(
              KtPsiFactory(project).createArgument("\"${issueType.ignoredAttributeValue}\""),
              KtPsiFactory(project),
            )
        } else {
          val suppress =
            KtPsiFactory(project)
              .createAnnotationEntry("@kotlin.Suppress(\"${issueType.ignoredAttributeValue}\")")
          ShortenReferencesFacility.getInstance()
            .shorten(composableFunction.addAnnotationEntry(suppress))
        }
      },
      previewElement.containingFile,
    )
  }

  override fun isValid(): Boolean {
    return previewElement.previewElementDefinition?.let { runReadAction { it.element?.isValid } }
      ?: false
  }
}
