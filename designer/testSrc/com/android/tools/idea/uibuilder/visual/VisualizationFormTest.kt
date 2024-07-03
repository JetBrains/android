/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VisualizationFormTest {

  @Rule
  @JvmField
  val rule =
    AndroidProjectRule.onDisk() // We need resource folders
      .onEdt()

  @Before
  fun setUp() {
    DesignerTypeRegistrar.register(LayoutFileType)
  }

  @RunsInEdt
  @Test
  fun testInitFormWithLayoutEditor() {
    val form =
      VisualizationForm(rule.project, rule.testRootDisposable, TestVisualizationFormInitializer)
    form.activate() // Ensure the full initialization is triggered

    val file = rule.fixture.addFileToProject("res/layout/test.xml", "")
    rule.fixture.openFileInEditor(file.virtualFile)

    try {
      val editor = FileEditorManager.getInstance(rule.project).selectedEditor!!
      assertTrue(form.setNextEditor(editor))

      PlatformTestUtil.waitWithEventsDispatching(
        "Failed to open the editor",
        { form.editor == editor },
        60,
      )
    } finally {
      FileEditorManager.getInstance(rule.project).closeFile(file.virtualFile)
    }
  }

  @RunsInEdt
  @Test
  fun testNotInitWithNonLayoutResourceFile() {
    // Visualization Form doesn't work with non-layout resource files. E.g. drawables, preferences,
    // etc.
    val form =
      VisualizationForm(rule.project, rule.testRootDisposable, TestVisualizationFormInitializer)
    val file = rule.fixture.addFileToProject("res/drawable/test.xml", "")
    rule.fixture.openFileInEditor(file.virtualFile)
    try {
      val editor = FileEditorManager.getInstance(rule.project).selectedEditor!!
      assertFalse(form.setNextEditor(editor))
    } finally {
      FileEditorManager.getInstance(rule.project).closeFile(file.virtualFile)
    }
  }

  @RunsInEdt
  @Test
  fun testNotInitWithTextEditor() {
    val form =
      VisualizationForm(rule.project, rule.testRootDisposable, TestVisualizationFormInitializer)

    val file = rule.fixture.addFileToProject("test.txt", "")
    rule.fixture.openFileInEditor(file.virtualFile)
    try {
      val textEditor = FileEditorManager.getInstance(rule.project).selectedEditor!!
      assertFalse(form.setNextEditor(textEditor))
    } finally {
      FileEditorManager.getInstance(rule.project).closeFile(file.virtualFile)
    }
  }
}
