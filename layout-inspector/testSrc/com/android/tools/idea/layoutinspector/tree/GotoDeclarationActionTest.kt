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
package com.android.tools.idea.layoutinspector.tree

import com.android.testutils.TestUtils
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.text.LineColumn
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class GotoDeclarationActionTest {

  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private var componentStack: ComponentStack? = null
  private var fileManager: FileEditorManager? = null

  @Before
  fun setup() {
    loadComposeFiles()
    componentStack = ComponentStack(projectRule.project)
    enableFileOpenCaptures()
  }

  @After
  fun after() {
    componentStack!!.restore()
    componentStack = null
    fileManager = null
  }

  @RunsInEdt
  @Test
  fun testViewNode() {
    val model = createModel()
    model.selection = model["title"]
    val event = createEvent(model)
    GotoDeclarationAction.actionPerformed(event)
    checkEditor("demo.xml", 8, "<TextView")
  }

  @RunsInEdt
  @Test
  fun testComposeViewNode() {
    val model = createModel()
    model.selection = model[-2]
    val event = createEvent(model)
    GotoDeclarationAction.actionPerformed(event)
    checkEditor("MyCompose.kt", 17, "Column(modifier = Modifier.padding(20.dp)) {")
  }

  @RunsInEdt
  @Test
  fun testComposeViewNodeInOtherFileWithSameName() {
    val model = createModel()
    model.selection = model[-5]
    val event = createEvent(model)
    GotoDeclarationAction.actionPerformed(event)
    checkEditor("MyCompose.kt", 8, "Text(text = \"Hello \$name!\")")
  }

  private fun loadComposeFiles() {
    val fixture = projectRule.fixture
    fixture.testDataPath = TestUtils.getWorkspaceFile("tools/adt/idea/layout-inspector/testData/compose").path
    fixture.copyFileToProject("java/com/example/MyCompose.kt")
    fixture.copyFileToProject("java/com/example/composable/MyCompose.kt")
  }

  private fun enableFileOpenCaptures() {
    fileManager = mock(FileEditorManagerEx::class.java)
    componentStack!!.registerComponentInstance(FileEditorManager::class.java, fileManager!!)
    `when`(fileManager!!.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(mock(FileEditor::class.java)))
    `when`(fileManager!!.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
  }

  @Suppress("SameParameterValue")
  private fun checkEditor(fileName: String, lineNumber: Int, text: String) {
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    Mockito.verify(fileManager!!).openEditor(file.capture(), ArgumentMatchers.eq(true))
    val descriptor = file.value
    val line = findLineAtOffset(descriptor.file, descriptor.offset)
    Truth.assertThat(descriptor.file.name).isEqualTo(fileName)
    Truth.assertThat(line.second).isEqualTo(text)
    Truth.assertThat(line.first.line + 1).isEqualTo(lineNumber)
  }

  private fun findLineAtOffset(file: VirtualFile, offset: Int): Pair<LineColumn, String> {
    val text = String(file.contentsToByteArray(), Charsets.UTF_8)
    val line = StringUtil.offsetToLineColumn(text, offset)
    val lineText = text.substring(offset - line.column, text.indexOf('\n', offset))
    return Pair(line, lineText.trim())
  }

  private fun createModel(): InspectorModel =
    model(projectRule.project, DemoExample.setUpDemo(projectRule.fixture) {
      view(0, qualifiedName = "androidx.ui.core.AndroidComposeView") {
        compose(-2, "Column", "MyCompose.kt", 49835523, 508, 17) {
          compose(-3, "Text", "MyCompose.kt", 49835523, 561, 18)
          compose(-4, "Greeting", "MyCompose.kt", 49835523, 590, 19) {
            compose(-5, "Text", "MyCompose.kt", 1216697758, 156, 3)
          }
        }
      }
    })

  private fun createEvent(model: InspectorModel): AnActionEvent {
    val inspector = mock(LayoutInspector::class.java)
    `when`(inspector.layoutInspectorModel).thenReturn(model)
    val dataContext = object : DataContext {
      override fun getData(dataId: String): Any? {
        return null
      }

      override fun <T> getData(key: DataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return if (key == LAYOUT_INSPECTOR_DATA_KEY) inspector as T else null
      }
    }
    val actionManager = mock(ActionManager::class.java)
    return AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, Presentation(), actionManager, 0)
  }
}
