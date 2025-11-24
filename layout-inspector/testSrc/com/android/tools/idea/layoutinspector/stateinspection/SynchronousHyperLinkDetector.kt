/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.stateinspection

import com.intellij.execution.impl.EditorHyperlinkListener
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.CoroutineScope

internal class SynchronousHyperLinkDetectorFactory : HyperLinkDetectorFactory {
  override fun create(
    editor: EditorEx,
    scope: CoroutineScope,
    activatedLinkListener: EditorHyperlinkListener,
  ): HyperLinkDetector = SynchronousHyperLinkDetector(editor, scope, activatedLinkListener)
}

/** HyperLinkDetector used in tests with synchronous execution. */
internal class SynchronousHyperLinkDetector(
  editor: EditorEx,
  scope: CoroutineScope,
  activatedLinkListener: EditorHyperlinkListener,
) : StateInspectionHyperLinkDetector(editor, scope, activatedLinkListener) {

  override fun detectHyperlinks() {
    // The write action allows the AsyncFilterRunner used by EditorHyperlinkSupport to run all
    // the tasks on its Queue immediately.
    WriteAction.run(ThrowableRunnable { super.detectHyperlinks() })
  }
}
