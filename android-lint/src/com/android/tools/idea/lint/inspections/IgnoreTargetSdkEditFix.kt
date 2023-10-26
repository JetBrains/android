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
package com.android.tools.idea.lint.inspections

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.lint.checks.GradleDetector
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class IgnoreTargetSdkEditFix :
  // Error message chosen to be alphabetically later than "Launch SDK Upgrade Assistant"
  // such that it doesn't show up first
  DefaultLintQuickFix("Override warning; I know what I'm doing") {

  override fun isApplicable(
    startElement: PsiElement,
    endElement: PsiElement,
    contextType: AndroidQuickfixContexts.ContextType
  ): Boolean = IdeInfo.getInstance().isAndroidStudio

  override fun apply(
    startElement: PsiElement,
    endElement: PsiElement,
    context: AndroidQuickfixContexts.Context
  ) {
    stopFlaggingTargetSdkEditsForSession(startElement.project)
  }

  override fun generatePreview(
    project: Project,
    editor: Editor,
    file: PsiFile
  ): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}

fun stopFlaggingTargetSdkEditsForSession(project: Project) {
  GradleDetector.Companion.stopFlaggingTargetSdkEdits()
  DaemonCodeAnalyzer.getInstance(project).restart()
}
