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
package com.android.tools.idea.compose.preview.util

import com.android.tools.idea.preview.PsiPreviewElement
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtImportDirective

private const val EDGE_RIGHT = "EDGE_RIGHT"
private const val EDGE_LEFT = "EDGE_LEFT"
private const val EDGE_NONE = "EDGE_NONE"
private const val NAVIGATION_EVENT = "NavigationEvent"
private const val NAVIGATION_EVENT_PACKAGE_NAME = "androidx.navigationevent"
private val EDGE_NAVIGATION_CONSTANTS = setOf(EDGE_RIGHT, EDGE_LEFT, EDGE_NONE)
private val FULLY_QUALIFIED_NAME_EDGES = EDGE_NAVIGATION_CONSTANTS.map { "$NAVIGATION_EVENT_PACKAGE_NAME.$NAVIGATION_EVENT.$it" }.toSet()
private val SHORT_FORM_EDGES = EDGE_NAVIGATION_CONSTANTS.map { "$NAVIGATION_EVENT.$it" }.toSet() + EDGE_NAVIGATION_CONSTANTS

/** [PsiFile] containing this PreviewElement. null if there is no source file, like in synthetic preview elements. */
val PsiPreviewElement.containingFile: PsiFile?
  get() = runReadAction { previewBody?.containingFile ?: previewElementDefinition?.containingFile }

/**
 * Evaluates whether the user's source code explicitly implements edge navigation by checking for usage of `NavigationEvent` edge constants
 * in the file containing this [ComposePreviewElementInstance].
 *
 * @return true if the source file actively references any back navigation edge event, false otherwise.
 */
suspend fun ComposePreviewElementInstance<*>.isEdgeNavigationImplemented(): Boolean {
  return readAction {
    val previewSourceFile = findPreviewSourceFile() ?: return@readAction false
    val isImported = previewSourceFile.isNavigationEventImported()
    previewSourceFile.hasNavigationEdgeReferences(isImported)
  }
}

/** Finds the source file associated with this [ComposePreviewElementInstance]. */
private fun ComposePreviewElementInstance<*>.findPreviewSourceFile(): PsiFile? {
  return (previewBody as? SmartPsiElementPointer<*>)?.containingFile
    ?: (previewBody as? PsiElement)?.containingFile
    ?: (previewElementDefinition as? SmartPsiElementPointer<*>)?.containingFile
    ?: (previewElementDefinition as? PsiElement)?.containingFile
}

/** Returns true if this file contains an import directive or package declaration for navigation events. */
private fun PsiFile.isNavigationEventImported(): Boolean {
  var isNavigationEventImportFound = false
  accept(
    object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element is KtImportDirective || element is PsiImportStatementBase) {
          val importText = element.text
          if (
            importText.contains("$NAVIGATION_EVENT_PACKAGE_NAME.*") ||
              importText.contains("$NAVIGATION_EVENT_PACKAGE_NAME.$NAVIGATION_EVENT")
          ) {
            isNavigationEventImportFound = true
            stopWalking()
          }
        }
        super.visitElement(element)
      }
    }
  )
  return isNavigationEventImportFound
}

/**
 * Returns true if this file actively references any of the edge navigation constants.
 *
 * @param isImported true if the navigation events package or classes are imported in this file.
 */
private fun PsiFile.hasNavigationEdgeReferences(isImported: Boolean): Boolean {
  var hasReferences = false
  accept(
    object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element is PsiComment) return

        if (isEdgeImplementedInCode(element, isImported)) {
          hasReferences = true
          stopWalking()
        }
        super.visitElement(element)
      }
    }
  )
  return hasReferences
}

/** Checks whether this leaf element matches exactly one of the target edge navigation constants. */
private fun isEdgeImplementedInCode(element: PsiElement, isImported: Boolean): Boolean {
  if (element.text in FULLY_QUALIFIED_NAME_EDGES) {
    return true
  }
  return isImported && element.text in SHORT_FORM_EDGES
}
