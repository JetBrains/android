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

import com.android.tools.lint.detector.api.Issue
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import javax.swing.Icon
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.plugins.gradle.config.GradleFileType
import org.jetbrains.plugins.groovy.GroovyFileType
import org.toml.lang.psi.TomlFileType

/** Intention for adding a `@SuppressLint` annotation on the given element for the given id */
class SuppressLintIntentionAction(private val id: String, element: PsiElement) :
  IntentionAction, Iconable {
  private val label = SuppressLintQuickFix.displayName(element, id)

  constructor(issue: Issue, element: PsiElement) : this(issue.id, element)

  override fun getIcon(@Iconable.IconFlags flags: Int): Icon {
    return AllIcons.Actions.Cancel
  }

  override fun getText(): String = label

  override fun getFamilyName(): String {
    return "Suppress"
  }

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    val type = file.fileType
    return type === JavaFileType.INSTANCE ||
      type === XmlFileType.INSTANCE ||
      type === GroovyFileType.GROOVY_FILE_TYPE ||
      type === GradleFileType ||
      type === KotlinFileType.INSTANCE ||
      file is PsiBinaryFile ||
      type === TomlFileType
  }

  @Throws(IncorrectOperationException::class)
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (file == null) {
      return
    }

    if (editor != null) {
      val elementOffset = editor.caretModel.offset
      val element = file.findElementAt(elementOffset) ?: return

      val fix = SuppressLintQuickFix(id, element)
      fix.applyFix(element)
    } else {
      // For example, an icon file
      SuppressLintQuickFix(id, file).applyFix(file)
    }
  }

  override fun startInWriteAction(): Boolean {
    return true
  }

  override fun toString(): String = text
}
