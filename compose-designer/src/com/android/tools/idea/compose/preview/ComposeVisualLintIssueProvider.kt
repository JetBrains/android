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
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.compose.pickers.preview.utils.addNewValueArgument
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintOrigin
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.preview.ComposePreviewElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

/** [VisualLintIssueProvider] to be used when dealing with Compose. */
class ComposeVisualLintIssueProvider(parentDisposable: Disposable) :
  VisualLintIssueProvider(parentDisposable) {

  override fun customizeIssue(issue: VisualLintRenderIssue) {
    val model = issue.models.firstOrNull() ?: return
    val previewElement = model.dataContext.previewElement() ?: return

    issue.customizeIsSuppressed {
      val suppressedTypes = runReadAction {
        previewElement.previewElementDefinitionPsi?.let { pointer ->
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
        ComposeVisualLintSuppressTask(model, previewElement, issue.type)
      )
    )
  }
}

/**
 * Task used to suppress a Visual Lint error happening in Compose. It adds a [Suppress] annotation
 * to the Composable that created the [previewElement], adding the type of error to suppress as an
 * argument to the annotation.
 */
class ComposeVisualLintSuppressTask(
  private val model: NlModel,
  private val previewElement: ComposePreviewElement,
  private val issueType: VisualLintErrorType
) : Runnable {

  override fun run() {
    VisualLintUsageTracker.getInstance()
      .trackIssueIgnored(issueType, VisualLintOrigin.UI_CHECK, model.facet)
    WriteCommandAction.runWriteCommandAction(
      model.project,
      issueType.toSuppressActionDescription(),
      null,
      {
        previewElement.previewElementDefinitionPsi?.let { pointer ->
          val annotationEntry = (pointer.element as? KtAnnotationEntry) ?: return@let
          val composableFunction = annotationEntry.parentOfType<KtFunction>() ?: return@let
          var suppress =
            composableFunction.annotationEntries.find { it.fqNameMatches("kotlin.Suppress") }
          if (suppress != null) {
            suppress.addNewValueArgument(
              KtPsiFactory(model.project).createArgument("\"${issueType.ignoredAttributeValue}\""),
              KtPsiFactory(model.project),
            )
          } else {
            suppress =
              KtPsiFactory(model.project)
                .createAnnotationEntry("@kotlin.Suppress(\"${issueType.ignoredAttributeValue}\")")
            ShortenReferencesFacility.getInstance()
              .shorten(composableFunction.addAnnotationEntry(suppress))
          }
        }
      },
      previewElement.containingFile
    )
  }
}
