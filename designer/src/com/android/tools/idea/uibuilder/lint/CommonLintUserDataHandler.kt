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

import com.android.tools.idea.actions.ATF_ISSUES
import com.android.tools.idea.actions.ATF_ISSUES_LATCH
import com.android.tools.idea.actions.VISUAL_LINT_ISSUES
import com.android.tools.idea.actions.VISUAL_LINT_ISSUES_LATCH
import com.android.tools.idea.common.error.Issue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import java.util.concurrent.CountDownLatch

/** Holds issues to be displayed in common problems panel */
object CommonLintUserDataHandler {

  private fun getOrCreateNew(key: Key<CommonPanelIssueSet>, file: PsiFile): CommonPanelIssueSet {
    var issues = file.getUserData(key)
    if (issues == null) {
      issues = HashSet()
      file.putUserData(key, issues)
    }
    return issues
  }

  /** Returns latch to be used for whether issues are available or not */
  @Synchronized
  fun getLatch(key: Key<CountDownLatch>, file: PsiFile): CountDownLatch {
    var latch = file.getUserData(key)
    if (latch == null) {
      latch = CountDownLatch(1)
      file.putUserData(key, latch)
    }
    return latch
  }

  /** Reset the countdown latch count */
  @Synchronized
  fun resetLatch(key: Key<CountDownLatch>, file: PsiFile) {
    file.putUserData(key, CountDownLatch(1))
  }

  /** Update atf specific issues to display in the common problems panel */
  fun updateAtfIssues(file: XmlFile, issues: HashSet<Issue>) {
    val atfIssues = getOrCreateNew(ATF_ISSUES, file)

    ApplicationManager.getApplication().runReadAction {
      issues.forEach {
        atfIssues.add(CommonProblemsPanelIssue(it))
      }
    }

    getLatch(ATF_ISSUES_LATCH, file).countDown()
  }

  /** Update visual lint issues to be displayed in the common problems panel */
  fun updateVisualLintIssues(file: PsiFile, values: Collection<MutableMap<String, MutableList<Issue>>>) {
    val visualLintIssues = getOrCreateNew(VISUAL_LINT_ISSUES, file)

    ApplicationManager.getApplication().runReadAction {
      values.flatMap { it.values }.flatten().forEach {
        visualLintIssues.add(CommonProblemsPanelIssue(it))
      }
    }

    getLatch(VISUAL_LINT_ISSUES_LATCH, file).countDown()
  }

}