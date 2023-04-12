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
package com.android.tools.idea.logcat.testing

import com.android.tools.idea.logcat.util.createLogcatEditor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.junit.rules.ExternalResource

/** A Rule that provides a Logcat Editor to a test */
internal class LogcatEditorRule(private val projectRule: ProjectRule) : ExternalResource() {
  lateinit var editor: EditorEx
    private set

  override fun before() {
    editor = runInEdtAndGet { createLogcatEditor(projectRule.project) }
  }

  override fun after() {
    runInEdtAndWait { EditorFactory.getInstance().releaseEditor(editor) }
  }
}
