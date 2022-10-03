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
package com.android.tools.idea.layoutinspector.util

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.text.LineColumn
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.ComponentStack
import org.junit.rules.ExternalResource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout

private const val TIMEOUT = 1000L // milliseconds

class FileOpenCaptureRule(private val projectRule: AndroidProjectRule) : ExternalResource() {
  private var componentStack: ComponentStack? = null
  private var fileManager: FileEditorManager? = null

  override fun before() {
    componentStack = ComponentStack(projectRule.project)
    enableFileOpenCaptures()
  }

  override fun after() {
    componentStack!!.restore()
    componentStack = null
    fileManager = null
  }

  fun checkEditor(fileName: String, lineNumber: Int, text: String, ) {
    val descriptor = checkEditorOpened(fileName, focusEditor = true)
    val line = findLineAtOffset(descriptor.file, descriptor.offset)
    Truth.assertThat(line.second).isEqualTo(text)
    Truth.assertThat(line.first.line + 1).isEqualTo(lineNumber)
  }

  fun checkEditorOpened(fileName: String, focusEditor: Boolean): OpenFileDescriptor {
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    Mockito.verify(fileManager!!, timeout(TIMEOUT)).openEditor(file.capture(), ArgumentMatchers.eq(focusEditor))
    val descriptor = file.value
    Truth.assertThat(descriptor.file.name).isEqualTo(fileName)
    return descriptor
  }

  fun checkNoNavigation() {
    Mockito.verify(fileManager!!, never()).openEditor(any(), anyBoolean())
  }

  private fun findLineAtOffset(file: VirtualFile, offset: Int): Pair<LineColumn, String> {
    val text = String(file.contentsToByteArray(), Charsets.UTF_8)
    val line = StringUtil.offsetToLineColumn(text, offset)
    val lineText = text.substring(offset - line.column, text.indexOf('\n', offset))
    return Pair(line, lineText.trim())
  }

  private fun enableFileOpenCaptures() {
    fileManager = Mockito.mock(FileEditorManagerEx::class.java)
    whenever(fileManager!!.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(Mockito.mock(FileEditor::class.java)))
    whenever(fileManager!!.selectedEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    whenever(fileManager!!.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
    @Suppress("UnstableApiUsage")
    whenever(fileManager!!.openFilesWithRemotes).thenReturn(VirtualFile.EMPTY_ARRAY)
    whenever(fileManager!!.allEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    componentStack!!.registerComponentInstance(FileEditorManager::class.java, fileManager!!)
  }
}
