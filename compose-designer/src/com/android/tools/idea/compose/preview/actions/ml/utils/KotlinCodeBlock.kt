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
package com.android.tools.idea.compose.preview.actions.ml.utils

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

internal enum class KotlinCodeFragmentType {
  IMPORTS,
  CLASS,
  DECLARATION,
  BLOCK,
  COMMENT,
}

internal data class KotlinCodeBlock(val text: String, val fragments: List<KotlinCodeFragment>)

internal data class KotlinCodeFragment(val type: KotlinCodeFragmentType, val text: String) {

  fun getPsi(context: PsiElement): PsiElement {
    val project = context.project

    val psiFactory = KtPsiFactory(project)
    return when (type) {
      KotlinCodeFragmentType.IMPORTS ->
        psiFactory.createAnalyzableFile("snippet.kt", text, context).importList!!
      KotlinCodeFragmentType.CLASS -> psiFactory.createAnalyzableDeclaration<KtClass>(text, context)
      KotlinCodeFragmentType.DECLARATION -> psiFactory.createAnalyzableDeclaration(text, context)
      KotlinCodeFragmentType.BLOCK ->
        psiFactory.createBlockCodeFragment(text, context).getContentElement()
      KotlinCodeFragmentType.COMMENT -> psiFactory.createComment(text)
    }
  }
}

private fun <TDeclaration : KtDeclaration> KtPsiFactory.createAnalyzableDeclaration(
  text: String,
  context: PsiElement,
): TDeclaration {
  val file = createAnalyzableFile("file.kt", text, context)
  val declarations = file.declarations
  assert(declarations.size == 1) { "${declarations.size} declarations in $text" }
  @Suppress("UNCHECKED_CAST") return declarations.first() as TDeclaration
}
