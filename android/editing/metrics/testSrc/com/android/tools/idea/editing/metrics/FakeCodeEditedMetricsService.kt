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

import com.intellij.openapi.editor.event.DocumentEvent

/** Fake implementation of [CodeEditedMetricsService] for use in tests. */
class FakeCodeEditedMetricsService : CodeEditedMetricsService {
  /**
   * All [DocumentEvent]s passed to [recordCodeEdited], mapped to the [CodeEditingAction] that was
   * set during the call.
   */
  val eventToAction: Map<DocumentEvent, CodeEditingAction> = mutableMapOf()
  var currentCodeEditingAction: CodeEditingAction = CodeEditingAction.Unknown
    private set

  override fun setCodeEditingAction(action: CodeEditingAction) {
    this.currentCodeEditingAction = action
  }

  override fun clearCodeEditingAction() {
    setCodeEditingAction(CodeEditingAction.Unknown)
  }

  override fun recordCodeEdited(event: DocumentEvent) {
    (eventToAction as MutableMap<DocumentEvent, CodeEditingAction>)[event] =
      currentCodeEditingAction
  }
}
