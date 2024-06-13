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
package com.android.tools.compose

import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.core.completion.DeclarationLookupObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClass

@VisibleForTesting
internal fun PsiElement?.shouldShowDocumentation(): Boolean =
  when {
    this == null -> false
    isComposableFunction() -> true
    this is KtNamedFunction ->
      receiverTypeReference?.text == "androidx.compose.ui.Modifier" ||
        containingClass()?.fqName?.asString() == "androidx.compose.ui.Modifier"
    else -> false
  }

/** Automatically shows quick documentation for Compose functions during code completion */
@Service(Service.Level.PROJECT)
class ComposeAutoDocumentation(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  private var documentationOpenedByCompose = false

  class MyLookupManagerListener(private val project: Project) : LookupManagerListener {

    override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
      if (newLookup == null) return
      val file = FileDocumentManager.getInstance().getFile(newLookup.editor.document) ?: return
      // Register the listener on spec. If we wind up not needing it, we can unregister. If we
      // do need it, it will already be registered, can capture any missed events, and we
      // can re-trigger them after we activate below.
      val listener =
        object : LookupListener {
          private var active = false
          private var missedEvent: LookupEvent? = null

          suspend fun activate() {
            withContext(Dispatchers.EDT) {
              // We're on the EDT, so we can't race with ourselves in
              // currentItemChanged below.
              if (active) return@withContext
              active = true
              missedEvent?.let(::currentItemChanged)
              missedEvent = null
            }
          }

          override fun currentItemChanged(event: LookupEvent) {
            if (!active) missedEvent = event else getInstance(project).showJavaDoc(newLookup)
          }
        }
      newLookup.addLookupListener(listener)

      getInstance(project).coroutineScope.launch {
        // This is a @Slow operation and must be kept off the EDT.
        val module =
          withContext(Dispatchers.Default) {
            ModuleUtilCore.findModuleForFile(file, newLookup.project)
          }

        if (module?.getModuleSystem()?.usesCompose == true) listener.activate()
        else newLookup.removeLookupListener(listener)
      }
    }
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): ComposeAutoDocumentation = project.service()
  }

  private fun showJavaDoc(lookup: Lookup) {
    if (LookupManager.getInstance(project).activeLookup !== lookup) return

    // Don't bother if we're already showing JavaDoc for everything.
    if (CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO) {
      documentationOpenedByCompose = false
      return
    }

    // If we open doc when lookup is not visible, doc will have wrong parent window (editor window
    // instead of lookup).
    if (lookup !is LookupImpl || !lookup.isVisible) {
      Alarm()
        .addRequest({ showJavaDoc(lookup) }, CodeInsightSettings.getInstance().JAVADOC_INFO_DELAY)
      return
    }

    val docManager = DocumentationManager.getInstance(project)
    val psiElement =
      lookup.currentItem?.run {
        psiElement ?: (getObject() as? DeclarationLookupObject)?.psiElement
      }
    if (!psiElement.shouldShowDocumentation()) {
      // Close documentation for not composable function if it was opened by
      // [AndroidComposeAutoDocumentation].
      // Case docManager.docInfoHint?.isFocused == true: user clicked on doc window and after that
      // clicked on lookup and selected another
      // element. Due to bug docManager.docInfoHint?.isFocused == true even after clicking on lookup
      // element, in that case if we close
      // docManager.docInfoHint, lookup will be closed as well.
      if (documentationOpenedByCompose && docManager.docInfoHint?.isFocused == false) {
        docManager.docInfoHint?.cancel()
        documentationOpenedByCompose = false
      }
      return
    }

    // It's composable function and documentation already opened
    if (docManager.docInfoHint != null) return // will auto-update

    val currentItem = lookup.currentItem
    if (
      currentItem != null &&
        currentItem.isValid &&
        CompletionService.getCompletionService().currentCompletion != null
    ) {
      try {
        docManager.showJavaDocInfo(lookup.editor, lookup.psiFile, false) {
          documentationOpenedByCompose = false
        }
        documentationOpenedByCompose = true
      } catch (ignored: IndexNotReadyException) {}
    }
  }
}
