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
package org.jetbrains.android.compose

import com.android.tools.idea.flags.StudioFlags.COMPOSE_AUTO_DOCUMENTATION
import com.android.tools.idea.flags.StudioFlags.COMPOSE_EDITOR_SUPPORT
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.util.Alarm
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
    if (COMPOSE_EDITOR_SUPPORT.get() &&
        COMPOSE_AUTO_DOCUMENTATION.get () &&
        LookupManager.PROP_ACTIVE_LOOKUP == evt.propertyName &&
        evt.newValue is Lookup) {
      val lookup = evt.newValue as Lookup

      val moduleSystem = FileDocumentManager.getInstance().getFile(lookup.editor.document)
        ?.let { ModuleUtilCore.findModuleForFile(it, lookup.project) }
        ?.getModuleSystem()

      if (moduleSystem?.usesCompose == true) {
        lookup.addLookupListener(object : LookupListener {
          override fun currentItemChanged(event: LookupEvent) {
            showJavaDoc(lookup)
          }
        })
      }
    }
  }

  init {
    if (COMPOSE_EDITOR_SUPPORT.get() && COMPOSE_AUTO_DOCUMENTATION.get () && !ApplicationManager.getApplication().isUnitTestMode) {
      lookupManager.addPropertyChangeListener(lookupListener)
    }
  }

  private fun showJavaDoc(lookup: Lookup) {
    if (lookupManager.activeLookup !== lookup) return

    // If we open doc when lookup is not visible, doc will have wrong parent window (editor window instead of lookup).
    if ((lookup as? LookupImpl)?.isVisible != true) {
      Alarm().addRequest({ showJavaDoc(lookup) }, CodeInsightSettings.getInstance().JAVADOC_INFO_DELAY)
      return
    }

    val psiElement = lookup.currentItem?.psiElement ?: return

    if (!psiElement.isComposableFunction()) {
      // Close documentation for not composable function if it was opened by [AndroidComposeAutoDocumentation].
      // Case docManager.docInfoHint?.isFocused == true: user clicked on doc window and after that clicked on lookup and selected another
      // element. Due to bug docManager.docInfoHint?.isFocused == true even after clicking on lookup element, in that case if we close
      // docManager.docInfoHint, lookup will be closed as well.
      if (documentationOpenedByCompose && docManager.docInfoHint?.isFocused == false) {
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
