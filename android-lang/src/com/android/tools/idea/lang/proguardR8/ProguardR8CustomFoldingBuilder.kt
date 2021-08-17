/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Adds support for custom folding regions (region...endregion).
 *
 * As we don't support any folding regions besides custom folding regions [getLanguagePlaceholderText] and [isRegionCollapsedByDefault]
 * that called for NOT custom folding regions should never be called.
 */
class ProguardR8CustomFoldingBuilder : CustomFoldingBuilder() {
  override fun buildLanguageFoldRegions(descriptors: MutableList<FoldingDescriptor>, root: PsiElement, document: Document, quick: Boolean) {
    //We don't support any folding regions besides custom folding regions (region...endregion).
    return
  }

  override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
    throw UnsupportedOperationException()
  }

  override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
    throw UnsupportedOperationException()
  }
}