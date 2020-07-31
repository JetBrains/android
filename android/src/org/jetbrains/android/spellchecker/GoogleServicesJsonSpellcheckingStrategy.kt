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
package org.jetbrains.android.spellchecker

import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer

/**
 * Custom spellchecking strategy for Json files, takes precedence over [com.intellij.json.JsonSpellcheckerStrategy]
 */
class GoogleServicesJsonSpellcheckingStrategy : SpellcheckingStrategy() {

  companion object {
    val IGNORE_SUFFIX: List<String> = listOf("_url", "_bucket", "_id", "_hash", "_key")
  }

  override fun getTokenizer(element: PsiElement?): Tokenizer<*> {
    return if (element is JsonStringLiteral) {
      object : Tokenizer<JsonStringLiteral>() {
        override fun tokenize(element: JsonStringLiteral, consumer: TokenConsumer) {
          val property = element.parentOfType<JsonProperty>() ?: return consumer.consumeToken(element, PlainTextSplitter.getInstance())
          val name = (property.nameElement as? JsonStringLiteral)?.value ?: return
          if (IGNORE_SUFFIX.none { name.endsWith(it) }) {
            consumer.consumeToken(element, PlainTextSplitter.getInstance())
          }
        }
      }
    }
    else {
      EMPTY_TOKENIZER
    }
  }

  override fun isMyContext(element: PsiElement): Boolean {
    val file = element.containingFile ?: return false
    return file.virtualFile.name.equals("google-services.json", ignoreCase = !file.virtualFile.isCaseSensitive)
  }
}