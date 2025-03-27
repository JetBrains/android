/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.editing.metrics

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Key

/**
 * [BulkAwareDocumentListener] that tracks changes to documents for code edited metrics. Since
 * documents can be opened in multiple editors, it ensures that it's only registered once for each
 * document so that edits aren't double-counted.
 */
private class CodeEditedDocumentListener private constructor() : BulkAwareDocumentListener {
  private var editorCount: Int = 0

  override fun documentChangedNonBulk(event: DocumentEvent) {
    CodeEditedMetricsService.getInstance().recordCodeEdited(event)
  }

  companion object {
    private val KEY = Key.create<CodeEditedDocumentListener>("CodeEditedDocumentListener")

    @Synchronized
    fun startListening(document: Document) {
      val listener =
        document.getUserData(KEY)
          ?: CodeEditedDocumentListener().also {
            document.putUserData(KEY, it)
            document.addDocumentListener(it)
          }
      listener.editorCount++
    }

    @Synchronized
    fun stopListening(document: Document) {
      val listener = document.getUserData(KEY)
      if (listener == null) {
        thisLogger().error("Should never have a null listener when stopping.")
        return
      }

      listener.editorCount--
      if (listener.editorCount == 0) {
        document.removeDocumentListener(listener)
        document.putUserData(KEY, null)
      }
    }
  }
}

/** [EditorFactoryListener] responsible for registering [CodeEditedDocumentListener]. */
internal class CodeEditedEditorFactoryListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    CodeEditedDocumentListener.startListening(event.editor.document)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    CodeEditedDocumentListener.stopListening(event.editor.document)
  }
}
