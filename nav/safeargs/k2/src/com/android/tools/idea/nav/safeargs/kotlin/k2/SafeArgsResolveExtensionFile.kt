/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionFile
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KtResolveExtensionNavigationTargetsProvider
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

abstract class SafeArgsResolveExtensionFile(val classId: ClassId) : KtResolveExtensionFile() {
  init {
    check(!classId.isLocal && classId.outermostClassId == classId) {
      "classId ${classId} must be top-level"
    }
  }

  override fun getFileName(): String = "${classId.shortClassName}.kt"

  override fun getFilePackageName(): FqName = classId.packageFqName

  override fun getTopLevelCallableNames(): Set<Name> = setOf()

  override fun getTopLevelClassifierNames(): Set<Name> = setOf(classId.shortClassName)

  protected open fun getImports(): Set<String> = setOf()

  private val fileText: String by lazy {
    buildString {
      appendLine("// This file is generated on-the-fly by SafeArgs.")
      appendLine()
      appendLine("package ${getFilePackageName()}")
      appendLine()
      getImports().sorted().forEach { appendLine("import ${it}") }
      appendLine()
      buildClassBody()
    }
  }

  protected abstract fun StringBuilder.buildClassBody()

  override fun buildFileText(): String = fileText

  protected abstract fun KtAnalysisSession.getNavigationElementForDeclaration(
    symbol: KaDeclarationSymbol
  ): PsiElement?

  protected abstract val fallbackPsi: PsiElement?

  private fun KtAnalysisSession.getNavigationElement(element: KtElement): PsiElement? =
    element.parentsWithSelf.filterIsInstance<KtDeclaration>().firstNotNullOfOrNull {
      getNavigationElementForDeclaration(it.symbol)
    } ?: fallbackPsi

  private val navigationTargetsProvider by lazy {
    object : KtResolveExtensionNavigationTargetsProvider() {
      override fun KtAnalysisSession.getNavigationTargets(
        element: KtElement
      ): Collection<PsiElement> = listOfNotNull(getNavigationElement(element))
    }
  }

  override fun createNavigationTargetsProvider() = navigationTargetsProvider
}
