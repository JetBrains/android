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
package com.android.tools.idea.lang.androidSql.refactoring

import com.android.tools.idea.lang.androidSql.COMMENTS
import com.android.tools.idea.lang.androidSql.IDENTIFIERS
import com.android.tools.idea.lang.androidSql.STRING_LITERALS
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlNameElement
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.EmptyFindUsagesProvider
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.cache.impl.id.ScanningIdIndexer
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider

/**
 * No-op [FindUsagesProvider] that provides the right [WordsScanner] for SQL.
 *
 * Having one for the language enables the "find usages" UI, which is what we want. Currently there are no PSI references pointing to
 * SQL elements, so this provider doesn't have to do anything, it never actually gets invoked. The only references are from SQL to Java,
 * but that is handled by java usages providers.
 *
 * We cannot inherit from [EmptyFindUsagesProvider], because [com.intellij.find.actions.FindUsagesInFileAction] has an explicit check
 * that disables the action if the provider is an instance of [EmptyFindUsagesProvider].
 */
class AndroidSqlFindUsagesProvider : FindUsagesProvider by EmptyFindUsagesProvider() {
  override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(AndroidSqlLexer(), IDENTIFIERS, COMMENTS, STRING_LITERALS)
}

class AndroidSqlIdIndexer : ScanningIdIndexer() {
  override fun createScanner(): WordsScanner = AndroidSqlFindUsagesProvider().wordsScanner
  override fun getVersion(): Int = 0
}

private val SQL_USAGE_TYPE = UsageType("Referenced in SQL query")

/**
 * [UsageTypeProvider] that labels references from SQL with the right description.
 *
 * @see SQL_USAGE_TYPE
 */
class AndroidSqlUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement) = if (element is AndroidSqlNameElement) SQL_USAGE_TYPE else null
}
