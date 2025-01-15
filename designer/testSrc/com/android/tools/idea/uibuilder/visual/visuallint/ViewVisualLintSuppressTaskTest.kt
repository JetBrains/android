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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.NlModelBuilderUtil.model
import com.android.tools.visuallint.analyzers.BoundsAnalyzer
import com.android.tools.visuallint.analyzers.LongTextAnalyzer
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ViewVisualLintSuppressTaskTest {
  @Rule @JvmField val rule = AndroidProjectRule.inMemory().onEdt()

  @RunsInEdt
  @Test
  fun testSuppressSingeAnalyzerToSingleModel() {
    val model = createModel("test.xml")
    ViewVisualLintSuppressTask(BoundsAnalyzer.type, model.treeReader.components).run()
    val attr = model.treeReader.components.first().getAttribute(TOOLS_URI, ATTR_IGNORE)
    assertEquals(BoundsAnalyzer.type.ignoredAttributeValue, attr)
  }

  @RunsInEdt
  @Test
  fun testSuppressionTaskDescriptionInUndoManager() {
    val model = createModel("test.xml")
    rule.fixture.openFileInEditor(model.virtualFile)
    ViewVisualLintSuppressTask(BoundsAnalyzer.type, model.treeReader.components).run()

    val editor = FileEditorManager.getInstance(rule.project).selectedEditor
    assertEquals(
      "Undo ${BoundsAnalyzer.type.toSuppressActionDescription()}",
      UndoManager.getInstance(rule.project).getUndoActionNameAndDescription(editor).second,
    )
  }

  @RunsInEdt
  @Test
  fun testSuppressSingeAnalyzerToMultipleModels() {
    val type = BoundsAnalyzer.type
    val model1 = createModel("test1.xml")
    val model2 = createModel("test2.xml")
    ViewVisualLintSuppressTask(type, model1.treeReader.components + model2.treeReader.components)
      .run()

    val attrOfModel1 = model1.treeReader.components.first().getAttribute(TOOLS_URI, ATTR_IGNORE)
    assertEquals(type.ignoredAttributeValue, attrOfModel1)

    val attrOfModel2 = model1.treeReader.components.first().getAttribute(TOOLS_URI, ATTR_IGNORE)
    assertEquals(type.ignoredAttributeValue, attrOfModel2)
  }

  @RunsInEdt
  @Test
  fun testSuppressAgain() {
    val type = BoundsAnalyzer.type
    // It should not suppress again.
    val model = createModel("test.xml")
    runWriteAction {
      rule.project.executeCommand("") {
        model.treeReader.components
          .first()
          .startAttributeTransaction()
          .apply { setAttribute(TOOLS_URI, ATTR_IGNORE, type.ignoredAttributeValue) }
          .commit()
      }
    }
    ViewVisualLintSuppressTask(type, model.treeReader.components).run()
    val attr = model.treeReader.components.first().getAttribute(TOOLS_URI, ATTR_IGNORE)
    assertEquals(type.ignoredAttributeValue, attr)
  }

  @RunsInEdt
  @Test
  fun testSuppressMultipleAnalyzers() {
    val type1 = BoundsAnalyzer.type
    val type2 = LongTextAnalyzer.type
    val model = createModel("test.xml")

    ViewVisualLintSuppressTask(type1, model.treeReader.components).run()
    ViewVisualLintSuppressTask(type2, model.treeReader.components).run()

    val attr = model.treeReader.components.first().getAttribute(TOOLS_URI, ATTR_IGNORE)
    val ignored = attr!!.split(",")
    assertEquals(2, ignored.size)
    ignored.contains(type1.ignoredAttributeValue)
    ignored.contains(type2.ignoredAttributeValue)
  }

  private fun createModel(fileName: String): NlModel {
    return model(
        rule.projectRule,
        SdkConstants.FD_RES_LAYOUT,
        fileName,
        ComponentDescriptor(LINEAR_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight(),
      )
      .build()
  }
}
