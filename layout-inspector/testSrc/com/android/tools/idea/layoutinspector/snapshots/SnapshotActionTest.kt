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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.util.FileOpenCaptureRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.util.io.write
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.doAnswer
import java.awt.Component
import java.nio.file.Path

class SnapshotActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fileOpenCaptureRule)!!

  private var isConnected = false

  @Test
  fun testSaveSnapshot() {
    val event = createEvent()
    ExportSnapshotAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    isConnected = true
    ExportSnapshotAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()

    val tempFile = FileUtil.createTempFile("foo", "bar.li")
    overrideFileChooser("process.name", VirtualFileWrapper(tempFile))
    ExportSnapshotAction.actionPerformed(event)
    runInEdt { UIUtil.dispatchAllInvocationEvents() }
    fileOpenCaptureRule.checkEditorOpened(tempFile.name, focusEditor = false)
  }

  @Test
  fun testLoadSnapshot() {
    val event = createEvent()
    ImportSnapshotAction.update(event)
    assertThat(event.presentation.isEnabled).isTrue()

    val tempFile = FileUtil.createTempFile("foo", "bar.li")
    overrideFileChooser("process.name", VirtualFileWrapper(tempFile))
    ImportSnapshotAction.actionPerformed(event)
    runInEdt { UIUtil.dispatchAllInvocationEvents() }
    fileOpenCaptureRule.checkEditorOpened(tempFile.name, focusEditor = true)
  }

  private fun createEvent(): AnActionEvent {
    val inspector: LayoutInspector = mock()
    val model: InspectorModel = mock()
    val client: InspectorClient = mock()
    val process: ProcessDescriptor = mock()
    whenever(inspector.currentClient).thenReturn(client)
    whenever(inspector.layoutInspectorModel).thenReturn(model)
    whenever(model.project).thenReturn(projectRule.project)
    whenever(client.process).thenReturn(process)
    whenever(process.name).thenReturn("process.name")
    doAnswer { invocation ->
      val path = invocation.arguments[0] as Path
      path.write(byteArrayOf(1,2,3))
    }.whenever(client).saveSnapshot(any(Path::class.java))
    doAnswer { isConnected }.whenever(client).isConnected
    val dataContext = DataContext { dataId -> if (dataId == LAYOUT_INSPECTOR_DATA_KEY.name) inspector else null }
    return AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, Presentation(), mock(), 0)
  }

  @Suppress("SameParameterValue")
  private fun overrideFileChooser(expectedFileName: String, fileToReturn: VirtualFileWrapper?) {
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog {
        return object : FileSaverDialog {
          override fun save(baseDir: VirtualFile?, filename: String?): VirtualFileWrapper? {
            assertThat(filename?.startsWith(expectedFileName) ?: false).isTrue()
            return fileToReturn
          }

          override fun save(baseDir: Path?, filename: String?): VirtualFileWrapper? {
            assertThat(filename?.startsWith(expectedFileName) ?: false).isTrue()
            return fileToReturn
          }
        }
      }

      override fun createFileChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): FileChooserDialog {
        return object : FileChooserDialog {
          @Deprecated("Deprecated in Java")
          override fun choose(toSelect: VirtualFile?, project: Project?): Array<VirtualFile> {
            error("not implemented")
          }

          override fun choose(project: Project?, vararg toSelect: VirtualFile?): Array<VirtualFile> {
            return arrayOf(fileToReturn!!.virtualFile!!)
          }
        }
      }
    }
    projectRule.replaceService(FileChooserFactory::class.java, factory)
  }
}