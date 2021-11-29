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
package com.android.tools.idea.logcat.filters

import com.android.ddmlib.Log
import com.android.tools.idea.logcat.filters.parser.LogcatFilterTypes
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

private val STRING_KEYS = listOf(
  "line",
  "message",
  "package",
  "tag",
)

private val LEVEL_KEYS = listOf(
  "fromLevel:",
  "level:",
  "toLevel:",
)

private const val AGE_KEY = "age:"

private const val PROJECT_APP = "app! "

private val KEYS = STRING_KEYS.map { listOf("$it:", "-$it:", "$it~:", "-$it~:") }.flatten() + LEVEL_KEYS + AGE_KEY + PROJECT_APP

private val KEYS_LOOKUP_BUILDERS = KEYS.map { LookupElementBuilder.create(it) }

private val LEVEL_LOOKUP_BUILDERS = Log.LogLevel.values().map { LookupElementBuilder.create("${it.name} ") }

/**
 * A [CompletionContributor] for the Logcat Filter Language.
 */
internal class LogcatFilterCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(LogcatFilterTypes.VALUE),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               result.addAllElements(KEYS_LOOKUP_BUILDERS)
             }
           })
    extend(CompletionType.BASIC, psiElement(LogcatFilterTypes.KVALUE),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               val key = PsiTreeUtil.findSiblingBackward(parameters.position, LogcatFilterTypes.KEY, null)
               if (key?.text in LEVEL_KEYS) {
                 result.addAllElements(LEVEL_LOOKUP_BUILDERS)
               }
             }
           })
  }
}
