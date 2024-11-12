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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.application
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/** Service that collects and reports metrics related to code editing. */
interface CodeEditedMetricsService {
  fun setCodeEditingAction(action: CodeEditingAction)

  fun clearCodeEditingAction()

  fun recordCodeEdited(event: DocumentEvent)

  companion object {
    fun getInstance(): CodeEditedMetricsService = service()
  }
}

/**
 * The actual implementation of [CodeEditedMetricsService] that works as an application-level
 * service.
 */
@VisibleForTesting
class CodeEditedMetricsServiceImpl
@TestOnly
constructor(coroutineScope: CoroutineScope, dispatcher: CoroutineDispatcher) :
  CodeEditedMetricsService, Disposable.Default {
  private val eventChannel = Channel<CodeEdited>(Channel.UNLIMITED)

  /**
   * Stores the ongoing action which will add the next piece of code. This allows added code to be
   * tagged for metrics.
   */
  private val codeEditingAction: AtomicReference<CodeEditingAction> =
    AtomicReference(CodeEditingAction.Unknown)

  constructor(coroutineScope: CoroutineScope) : this(coroutineScope, Dispatchers.Default)

  init {
    application.addApplicationListener(
      object : ApplicationListener {
        override fun afterWriteActionFinished(action: Any) {
          // Whenever a write action finishes, the source for any added code is no longer valid.
          clearCodeEditingAction()
        }
      },
      this,
    )

    coroutineScope.launch(dispatcher) {
      for (codeEditedEvent in eventChannel) {
        for (listener in CodeEditedListener.EP_NAME.extensionList) {
          listener.onCodeEdited(codeEditedEvent)
        }
      }
    }
  }

  override fun setCodeEditingAction(action: CodeEditingAction) {
    codeEditingAction.set(action)
  }

  override fun clearCodeEditingAction() {
    codeEditingAction.set(CodeEditingAction.Unknown)
  }

  override fun recordCodeEdited(event: DocumentEvent) {
    val newFragment = event.newFragment
    val oldFragment = event.oldFragment
    if (newFragment.isEmpty() && oldFragment.isEmpty()) return

    codeEditingAction
      .get()
      .getCodeEditedEvents(newFragment.toString(), oldFragment.toString())
      // trySend _always_ succeeds because we are using Channel.UNLIMITED above.
      .forEach(eventChannel::trySend)
  }
}

data class CodeEdited(
  val addedCharacterCount: Int,
  val deletedCharacterCount: Int,
  val source: Source,
)

/** Potential sources of code edits. */
enum class Source {
  UNKNOWN,
  TYPING,
  USER_PASTE,
  AI_CODE_COMPLETION,
  AI_CODE_GENERATION,
  IDE_ACTION,
  PASTE_FROM_AI_CHAT,
}
