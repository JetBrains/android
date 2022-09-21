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
package com.android.tools.idea.editors.strings.action

import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.textarea.TextComponentEditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBTextField
import com.intellij.util.Producer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

@RunWith(JUnit4::class)
class TranslationsEditorPasteActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var project: Project
  private lateinit var pasteAction: TranslationsEditorPasteAction
  private lateinit var psiFile: PsiFile
  private lateinit var editor: Editor

  @Before
  fun setUp() {
    project = projectRule.project
    pasteAction = TranslationsEditorPasteAction()

    val content = """<?xml version="1.0" encoding="utf-8"?>
    <resources>
    <string name="app_name">Test App</string>
    <string name="string1">string 1</string>
    <string name="string2">string 2</string>
    </resources>""".trimMargin()

    psiFile = psiFile(content)
    editor = createEditor(psiFile)
    val targetTag = "</resources>"
    val tagIndex = editor.document.text.indexOf(targetTag)
    runInEdtAndWait { editor.caretModel.moveToOffset(tagIndex) }
  }

  @After
  fun tearDown() {
    runInEdtAndWait {
      EditorFactory.getInstance().releaseEditor(editor)
      with(UndoManager.getGlobalInstance() as UndoManagerImpl) {
        // For AndroidProjectRule, have to manually clear the UndoManager.
        dropHistoryInTests()
        flushCurrentCommandMerger()
      }
    }
  }

  @Test
  fun constructionCopiesShortcutsFromExistingPasteAction() {
    assertThat(pasteAction.shortcutSet).isEqualTo(ActionManager.getInstance().getAction("EditorPaste").shortcutSet)
  }

  @Test
  fun nullTransferable() {
    val dataContext = createDataContext(editor, psiFile)
    runInEdtAndWait {
      pasteAction.handler.execute(editor, editor.caretModel.currentCaret, dataContext)
    }
    assertThat(editor.getUserData(EditorEx.LAST_PASTED_REGION)).isNull()
  }

  @Test
  fun nonNullTransferable() {
    val newTag = """<string name="string2">string 2</string>"""
    val dataContext = createDataContext(editor, psiFile, newTag)
    val initialOffset = runInEdtAndGet { editor.caretModel.currentCaret.offset }
    runInEdtAndWait {
      runUndoTransparentWriteAction {
        pasteAction.handler.execute(editor, editor.caretModel.currentCaret, dataContext)
      }
    }
    val lastPastedRegion = TextRange(initialOffset, initialOffset + newTag.length)
    assertThat(editor.getUserData(EditorEx.LAST_PASTED_REGION)).isEqualTo(lastPastedRegion)
    assertThat(editor.document.text).contains(newTag)
  }

  @Test
  fun fontSetCorrectlyIfJTextComponent() {
    val textEditor = TextComponentEditorImpl(project, JBTextField("Existing content"))
    val charIndex = textEditor.document.text.indexOf("content")
    runInEdtAndWait { textEditor.caretModel.moveToOffset(charIndex) }
    val dataContext = createDataContext(textEditor, "newContent")
    val originalFont = Font(Font.MONOSPACED, Font.BOLD, 16)
    textEditor.component.font = originalFont

    runInEdtAndWait {
      runUndoTransparentWriteAction {
        pasteAction.handler.execute(textEditor, textEditor.caretModel.currentCaret, dataContext)
      }
    }
    assertThat(textEditor.component.font).isEqualTo(StringResourceEditor.getFont(originalFont))
  }

  @Test
  fun fontLeftAloneIfNotJTextComponent() {
    val newTag = """<string name="string2">string 2</string>"""
    val dataContext = createDataContext(editor, psiFile, newTag)
    val originalFont = Font(Font.MONOSPACED, Font.BOLD, 16)
    editor.component.font = originalFont

    runInEdtAndWait {
      runUndoTransparentWriteAction {
        pasteAction.handler.execute(editor, editor.caretModel.currentCaret, dataContext)
      }
    }
    assertThat(editor.component.font).isEqualTo(originalFont)
  }

  private fun psiFile(content: String): PsiFile {
    val path = "/values/strings.xml"
    val fileSystem = MockVirtualFileSystem()
    val stringsFile: VirtualFile = fileSystem.file(path, content).refreshAndFindFileByPath(path)!!
    return runReadAction {
      PsiManager.getInstance(project).findFile(stringsFile)!!
    }
  }

  private fun createEditor(psiFile: PsiFile): Editor {
    val document = runReadAction { PsiDocumentManager.getInstance(project).getDocument(psiFile)!! }
    val editorFactory = EditorFactory.getInstance()
    return runInEdtAndGet { editorFactory.createEditor(document, project) }
  }

  private fun createDataContext(editor: Editor,
                                content: String? = null,
  ): DataContext = MapDataContext(mapOf(
    PasteAction.TRANSFERABLE_PROVIDER to Producer<Transferable> { content?.let { StringSelection(it) } },
    CommonDataKeys.CARET to editor.caretModel.currentCaret,
  ))

  private fun createDataContext(editor: Editor,
                                psiFile: PsiFile,
                                content: String? = null,
  ): DataContext = MapDataContext(mapOf(
    PasteAction.TRANSFERABLE_PROVIDER to Producer<Transferable> { content?.let { StringSelection(it) } },
    CommonDataKeys.CARET to editor.caretModel.currentCaret,
    CommonDataKeys.PSI_FILE to psiFile,
  ))
}