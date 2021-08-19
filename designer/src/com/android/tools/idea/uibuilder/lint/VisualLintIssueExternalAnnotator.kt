/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.lint

import com.android.tools.idea.actions.VISUAL_LINT_ISSUES
import com.android.tools.idea.actions.VISUAL_LINT_ISSUES_LATCH
import com.android.tools.idea.flags.StudioFlags
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import java.util.concurrent.TimeUnit

/** External Annotator that takes visual lint issues and display them to the common Problems panel. */
class VisualLintIssueExternalAnnotator : ExternalAnnotator<PsiFile, CommonPanelIssueSet>() {

  companion object {
    /** Default wait time in [doAnnotate] */
    const val WAIT_MS = 3000L
  }

  override fun collectInformation(file: PsiFile): PsiFile? {
    return collectInfo(file)
  }

  override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): PsiFile? {
    return collectInfo(file)
  }

  private fun collectInfo(file: PsiFile): PsiFile? {
    if (!StudioFlags.NELE_SHOW_VISUAL_LINT_ISSUE_IN_COMMON_PROBLEMS_PANEL.get()) {
      return null
    }
    return file
  }

  override fun doAnnotate(collectedInfo: PsiFile?): CommonPanelIssueSet? {
    if (!StudioFlags.NELE_SHOW_VISUAL_LINT_ISSUE_IN_COMMON_PROBLEMS_PANEL.get() || collectedInfo == null) {
      return null
    }

    // It's safe to block doAnnotate as long as it needs to be.
    val latch = CommonLintUserDataHandler.getLatch(VISUAL_LINT_ISSUES_LATCH, collectedInfo)
    latch.await(WAIT_MS, TimeUnit.MILLISECONDS)

    return collectedInfo.getUserData(VISUAL_LINT_ISSUES)
  }

  override fun apply(file: PsiFile, annotationResult: CommonPanelIssueSet?, holder: AnnotationHolder) {
    CommonLintUserDataHandler.resetLatch(VISUAL_LINT_ISSUES_LATCH, file)
    showIssuesInCommonProblemsPanel(annotationResult, holder)
  }
}
