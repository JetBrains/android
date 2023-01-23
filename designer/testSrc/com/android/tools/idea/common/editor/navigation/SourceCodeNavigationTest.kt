/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.editor.navigation

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.replaceService

class SourceCodeNavigationTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {

  override fun setUp() {
    super.setUp()
    val project = project
    project.replaceService(FileEditorManager::class.java, FileEditorManagerImpl(project, project.coroutineScope), testRootDisposable)
  }

  fun testNavigationWithinFile() {
    myFixture.configureByText("${getTestName(false)}.java", """
      class AA {}

      class BV extends A<caret>A {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_DECLARATION)
    myFixture.checkResult("""
      class <caret>AA {}

      class BV extends AA {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_BACK)
    myFixture.checkResult("""
      class AA {}

      class BV extends A<caret>A {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_FORWARD)
    myFixture.checkResult("""
      class <caret>AA {}

      class BV extends AA {}""".trimIndent())
  }
}