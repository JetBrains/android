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
package com.android.tools.idea.lang.proguardR8.psi

import com.android.tools.idea.lang.proguardR8.JAVA_IDENTIFIER_TOKENS
import com.android.tools.idea.lang.proguardR8.parser.ProguardR8Lexer
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiTypes.LINE_CMT
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.EmptyFindUsagesProvider
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.cache.impl.id.ScanningIdIndexer
import com.intellij.psi.tree.TokenSet
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider

private val PROGUARD_R8_USAGE_TYPE = UsageType("Referenced in Shrinker Config files")

/**
 * [UsageTypeProvider] that labels references from Proguard/R8 with the right description.
 *
 * @see PROGUARD_R8_USAGE_TYPE
 */
class ProguardR8UsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement) = if (element?.containingFile is ProguardR8PsiFile) PROGUARD_R8_USAGE_TYPE else null
}

/**
 * Provides "Find Usages" functionality on elements in Proguard file.
 *
 * @see [com.android.tools.idea.lang.androidSql.refactoring.AndroidSqlFindUsagesProvider]
 */
class ProguardR8FindUsagesProvider : FindUsagesProvider by EmptyFindUsagesProvider() {
  override fun getWordsScanner() = DefaultWordsScanner(ProguardR8Lexer(), JAVA_IDENTIFIER_TOKENS, TokenSet.create(LINE_CMT), TokenSet.EMPTY)
}

/**
 * Makes correct word index on Proguard file.
 *
 * For example "My$Class" would not be consider as word in Proguard file without this Indexer.
 */
class ProguardR8IdIndexer : ScanningIdIndexer() {
  override fun createScanner() = ProguardR8FindUsagesProvider().wordsScanner
  override fun getVersion(): Int = 0
}
