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
package org.jetbrains.kotlin.android.spellchecker

import com.android.SdkConstants
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.LanguageSpellchecking
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry

class AndroidGradleKtsSpellcheckingStrategy : SpellcheckingStrategy() {
  private val kotlinSpellcheckingStrategy by lazy {
    Language.findLanguageByID("kotlin")?.let { language ->
      LanguageSpellchecking.INSTANCE.allForLanguage(language)
        .firstOrNull { it !is AndroidGradleKtsSpellcheckingStrategy }
    }
  }

  override fun isMyContext(element: PsiElement): Boolean {
    return element.containingFile?.name?.endsWith(SdkConstants.EXT_GRADLE_KTS) ?: false
  }

  override fun getTokenizer(element: PsiElement?): Tokenizer<*> {
    if (element is KtLiteralStringTemplateEntry && !isPrint(element)) {
      return EMPTY_TOKENIZER
    }
    return kotlinSpellcheckingStrategy?.getTokenizer(element) ?: super.getTokenizer(element)
  }

  private fun isPrint(element: PsiElement): Boolean {
    return when (val maybeCall = element.parent?.parent?.parent?.parent) {
      null -> false
      is KtCallExpression -> setOf("print", "println").contains(maybeCall.calleeExpression!!.text)
      else -> false
    }
  }
}