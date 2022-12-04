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
package com.android.tools.idea.logcat

import com.intellij.execution.ui.ConsoleViewContentType.USER_INPUT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.ENTER
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_BACKSPACE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_DELETE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_PASTE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TAB
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer.SYNTAX
import com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicReference

/**
 * Handles typing of text by user in a Logcat [EditorEx].
 *
 * Text typed by user is appended to the document unless the caret or selection is already inside a region of text already typed by user.
 *
 * This is consistent with the behavior of ConsoleViewImpl and is based on code in ConsoleViewImpl.registerConsoleEditorActions().
 *
 * TODO(b/261527041): Maybe find a way to persist the user input text so it survives reloading of messages when filters change.
 */
internal class UserInputHandlers(private val editor: EditorEx) {
  private val project = editor.project
  private val document = editor.document
  private val selectionModel = editor.selectionModel
  private val caretModel = editor.caretModel
  private val scrollingModel = editor.scrollingModel
  private val markupModel = DocumentMarkupModel.forDocument(document, project, true) as MarkupModelEx
  private val copyPasteManager = CopyPasteManager.getInstance()

  fun install() {
    editor.contentComponent.addKeyListener(UserInputKeyListener())
    SpecialCharHandler("\n").registerCustomShortcutSet(ENTER, editor.contentComponent)
    registerActionHandler(ACTION_EDITOR_TAB, SpecialCharHandler("\t"))
    registerActionHandler(ACTION_EDITOR_BACKSPACE, DeleteBackspaceHandler(-1))
    registerActionHandler(ACTION_EDITOR_DELETE, DeleteBackspaceHandler(0))
    registerActionHandler(ACTION_EDITOR_PASTE, PasteHandler())
  }

  private fun registerActionHandler(actionId: String, action: AnAction) {
    val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)
    action.registerCustomShortcutSet(CustomShortcutSet(*shortcuts), editor.contentComponent)
  }

  /**
   * Returns true if the specified range is in a user input range
   */
  private fun isInUserInputRange(start: Int, end: Int = start) = findUserInputRange(start, end) != null

  /**
   * Returns the user input [RangeHighlighterEx] that contains given range or null if given range is not contained in one.
   */
  private fun findUserInputRange(start: Int, end: Int): RangeHighlighterEx? {
    val range = AtomicReference<RangeHighlighterEx?>(null)
    markupModel.processRangeHighlightersOverlappingWith(start, end) {
      if (it.textAttributesKey == USER_INPUT.attributesKey && it.startOffset <= start && it.endOffset >= end) {
        range.set(it)
        false
      }
      else
        true
    }
    return range.get()
  }

  /**
   * Type the given text into the document.
   */
  private fun type(text: String) {
    val lastOffset = if (selectionModel.hasSelection()) selectionModel.selectionStart else caretModel.offset + 1
    if (!isInUserInputRange(lastOffset)) {
      // Not inside a user input range, append text
      append(text)
    }
    else {
      // Inside a user range, insert text
      val typeOffset = if (selectionModel.hasSelection()) {
        val start = selectionModel.selectionStart
        deleteSelection()
        start
      }
      else {
        editor.caretModel.offset
      }
      insert(text, typeOffset)
    }
  }

  private fun deleteSelection() {
    document.deleteString(selectionModel.selectionStart, selectionModel.selectionEnd)
    selectionModel.removeSelection()
  }

  /**
   * Append text to the document and mark it with [USER_INPUT] text attributes.
   */
  private fun append(text: String) {
    var start = document.textLength
    val end = start + text.length
    document.insertString(start, text)
    selectionModel.removeSelection()
    caretModel.moveToOffset(document.textLength)
    scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    if (start > 0) {
      val prevMarker = findUserInputRange(start - 1, start)
      if (prevMarker != null) {
        start = prevMarker.startOffset
        prevMarker.dispose()
      }
    }
    markupModel.addRangeHighlighter(USER_INPUT.attributesKey, start, end, SYNTAX, EXACT_RANGE)
  }

  private fun insert(text: String, offset: Int) {
    document.insertString(offset, text)
    caretModel.moveToOffset(offset + text.length)
  }

  /**
   * Handles normal typing.
   *
   * In ConsoleViewImpl, this is done with a [com.intellij.openapi.editor.actionSystem.TypedActionHandler] using
   * [com.intellij.openapi.editor.actionSystem.TypedAction.setupHandler] but `setupHandler` is deprecated and the replacement doesn't
   * quite work, so we use a [java.awt.event.KeyListener]
   */
  private inner class UserInputKeyListener : KeyAdapter() {
    override fun keyTyped(e: KeyEvent) {
      type(e.keyChar.toString())
    }
  }

  /**
   * Handles ENTER & TAB keys.
   */
  private inner class SpecialCharHandler(val text: String) : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      type(text)
    }
  }

  /**
   * Handles PASTE action.
   */
  private inner class PasteHandler : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val text = copyPasteManager.getContents<String>(DataFlavor.stringFlavor)
      if (text != null) {
        type(text)
      }
    }
  }

  /**
   * Handles DELETE and BACKSPACE keys.
   */
  private inner class DeleteBackspaceHandler(private val relativeOffset: Int) : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      if (document.textLength == 0) {
        return
      }
      if (selectionModel.hasSelection()) {
        if (isInUserInputRange(selectionModel.selectionStart, selectionModel.selectionEnd)) {
          deleteSelection()
          insert("", selectionModel.selectionStart)
        }
      }
      else {
        val offset: Int = caretModel.offset + relativeOffset
        if (offset >= 0 && offset < document.textLength && isInUserInputRange(offset)) {
          document.deleteString(offset, offset + 1)
        }
      }
    }
  }
}