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
package com.android.tools.idea.testing.ui

import com.android.testutils.waitForCondition
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.text.LineColumn
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.jetbrains.android.ComponentStack
import org.junit.rules.ExternalResource
import org.mockito.Mockito
import org.mockito.Mockito.anyBoolean
import org.mockito.invocation.Invocation
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

private const val TIMEOUT = 10L // seconds
private val OPEN_EDITOR_METHODS = setOf("openFileEditor", "requestOpenEditor", "requestOpenTextEditor")
private val OPEN_FILE_METHODS = setOf("openFile", "requestOpenFile")

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

  fun checkEditor(fileName: String, lineNumber: Int, text: String, timeout: Long = TIMEOUT, unit: TimeUnit = TimeUnit.SECONDS) {
    val descriptor = checkEditorOpened(fileName, focusEditor = true, timeout, unit)
    check(descriptor is OpenFileDescriptor) // Downcast needed to extract file offset.
    val line = findLineAtOffset(descriptor.file, descriptor.offset)
    assertThat(line.second).isEqualTo(text)
    assertThat(line.first.line + 1).isEqualTo(lineNumber)
  }

  fun checkEditorOpened(
    fileName: String,
    focusEditor: Boolean,
    timeout: Long = TIMEOUT,
    unit: TimeUnit = TimeUnit.SECONDS,
  ): FileEditorNavigatable {
    val descriptor = waitForOpen(OPEN_EDITOR_METHODS, focusEditor, timeout, unit).getArgument<FileEditorNavigatable>(0)
    assertThat(descriptor.file.name).isEqualTo(fileName)
    return descriptor
  }

  fun checkFileOpened(fileName: String, focusEditor: Boolean, timeout: Long = TIMEOUT, unit: TimeUnit = TimeUnit.SECONDS) {
    val vFile = waitForOpen(OPEN_FILE_METHODS, focusEditor, timeout, unit).getArgument<VirtualFile>(0)
    assertThat(vFile.name).isEqualTo(fileName)
  }

  fun checkNoNavigation() {
    assertThat(openInvocations(OPEN_EDITOR_METHODS + OPEN_FILE_METHODS)).isEmpty()
  }

  private fun waitForOpen(methodNames: Set<String>, focusEditor: Boolean, timeout: Long, unit: TimeUnit): Invocation {
    waitForCondition(timeout, unit) { openInvocations(methodNames).isNotEmpty() }
    return openInvocations(methodNames).single { it.requestsFocus() == focusEditor }
  }

  private fun openInvocations(methodNames: Set<String>): List<Invocation> =
    Mockito.mockingDetails(fileManager!!).invocations.filter { it.method.name in methodNames }

  private fun Invocation.requestsFocus(): Boolean =
    when (val focus = arguments.getOrNull(1)) {
      is Boolean -> focus
      is FileEditorOpenRequest -> focus.requestFocus
      else -> false
    }

  private fun findLineAtOffset(file: VirtualFile, offset: Int): Pair<LineColumn, String> {
    val text = ReadAction.compute<String, Throwable> { FileDocumentManager.getInstance().getDocument(file)!!.text }
    val line = StringUtil.offsetToLineColumn(text, offset)
    val lineText = text.substring(offset - line.column, text.indexOf('\n', offset))
    return Pair(line, lineText.trim())
  }

  private fun enableFileOpenCaptures() {
    fileManager = Mockito.mock(FileEditorManagerEx::class.java)
    whenever(fileManager!!.openEditor(any(), anyBoolean())).thenCallRealMethod()
    whenever(fileManager!!.openFileEditor(any(), anyBoolean())).thenReturn(listOf(Mockito.mock(FileEditor::class.java)))
    whenever(fileManager!!.requestOpenEditor(any(), any())).thenReturn(CompletableFuture.completedFuture(FileEditorComposite.EMPTY))
    whenever(fileManager!!.requestOpenFile(any(), any())).thenReturn(CompletableFuture.completedFuture(FileEditorComposite.EMPTY))
    whenever(fileManager!!.requestOpenTextEditor(any(), any()))
      .thenReturn(CompletableFuture.completedFuture(Mockito.mock(Editor::class.java)))
    whenever(fileManager!!.selectedEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    whenever(fileManager!!.openFiles).thenReturn(VirtualFile.EMPTY_ARRAY)
    @Suppress("UnstableApiUsage") whenever(fileManager!!.openFilesWithRemotes).thenReturn(emptyList())
    whenever(fileManager!!.allEditors).thenReturn(FileEditor.EMPTY_ARRAY)
    whenever(fileManager!!.getAllEditors(any())).thenReturn(FileEditor.EMPTY_ARRAY)
    componentStack!!.registerServiceInstance(FileEditorManager::class.java, fileManager!!)
  }
}
