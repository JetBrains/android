/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.declarative.DeclarativeFileType
import com.android.tools.lint.detector.api.Issue
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.plugins.gradle.config.GradleFileType
import org.jetbrains.plugins.groovy.GroovyFileType
import org.toml.lang.psi.TomlFileType

/** Intention for adding a `@SuppressLint` annotation on the given element for the given id */
class SuppressLintIntentionAction(
  private val id: String,
  element: PsiElement,
  private val issue: Issue? = null,
) : ModCommandAction {
  private val label = SuppressLintQuickFix.displayName(element, id)

  constructor(issue: Issue, element: PsiElement) : this(issue.id, element)

  override fun getFamilyName(): String {
    return "Suppress"
  }

  override fun perform(context: ActionContext): ModCommand {
    val file = context.file
    val target = file.findElementAt(context.offset) ?: file
    val result = SuppressLintQuickFix(id, target).applyFix(target, context)

    if (issue != null && !IntentionPreviewUtils.isIntentionPreviewActive()) {
      LintIdeSupport.get().logQuickFixInvocation(context.project, issue, label)
    }
    return result
  }

  override fun getPresentation(context: ActionContext): Presentation? {
    val type = context.file.fileType
    return if (
      type === JavaFileType.INSTANCE ||
        type === XmlFileType.INSTANCE ||
        type === GroovyFileType.GROOVY_FILE_TYPE ||
        type === GradleFileType ||
        type === KotlinFileType.INSTANCE ||
        context.file is PsiBinaryFile ||
        type === TomlFileType ||
        type === DeclarativeFileType.INSTANCE
    )
      Presentation.of(label).withIcon(AllIcons.Actions.Cancel)
    else null
  }
}
