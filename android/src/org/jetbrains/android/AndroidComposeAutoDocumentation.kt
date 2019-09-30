/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android

import com.android.tools.idea.flags.StudioFlags.COMPOSE_AUTO_DOCUMENTATION
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.project.IndexNotReadyException
import java.beans.PropertyChangeListener

/**
 * Automatically shows quick documentation for Compose functions during code completion
 */
class AndroidComposeAutoDocumentation(
  private val docManager: DocumentationManager,
  private val lookupManager: LookupManager,
  private val completionService: CompletionService
) {
  private var documentationOpenedByCompose = false

  private val lookupListener = PropertyChangeListener { evt ->
    if (LookupManager.PROP_ACTIVE_LOOKUP == evt.propertyName && evt.newValue is Lookup && COMPOSE_AUTO_DOCUMENTATION.get()) {
      val lookup = evt.newValue as Lookup
      lookup.addLookupListener(object : LookupListener {
        override fun currentItemChanged(event: LookupEvent) {
          super.currentItemChanged(event)
          showJavaDoc(lookup)
        }
      })
    }
  }

  init {
    if (COMPOSE_AUTO_DOCUMENTATION.get()) {
      lookupManager.addPropertyChangeListener(lookupListener)
    }
  }

  private fun showJavaDoc(lookup: Lookup) {
    if (lookupManager.activeLookup !== lookup) return

    val psiElement = lookup.currentItem?.psiElement ?: return

    if (!psiElement.isComposableFunction()) {
      // Close documentation for not composable function if it was opened by [AndroidComposeAutoDocumentation].
      if (documentationOpenedByCompose) {
        docManager.docInfoHint?.cancel()
        documentationOpenedByCompose = false
      }
      return
    }

    // It's composable function and documentation already opened
    if (docManager.docInfoHint != null) return  // will auto-update

    val currentItem = lookup.currentItem
    if (currentItem != null && currentItem.isValid && completionService.currentCompletion != null) {
      try {
        docManager.showJavaDocInfo(lookup.editor, lookup.psiFile, false) {
          documentationOpenedByCompose = false
        }
        documentationOpenedByCompose = true
      }
      catch (ignored: IndexNotReadyException) {
      }
    }
  }
}