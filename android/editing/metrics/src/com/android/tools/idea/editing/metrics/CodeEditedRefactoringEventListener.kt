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
package com.android.tools.idea.editing.metrics

import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener

/** Listener for refactoring events that lets us know the code is being edited via refactoring. */
class CodeEditedRefactoringEventListener : RefactoringEventListener {
  override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
    CodeEditedMetricsService.getInstance().setCodeEditingAction(CodeEditingAction.Refactoring)
  }

  override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
    // Probably not strictly necessary as the write action will clear it, but since this method
    // must be defined, might as well do it here.
    CodeEditedMetricsService.getInstance().clearCodeEditingAction()
  }
}
