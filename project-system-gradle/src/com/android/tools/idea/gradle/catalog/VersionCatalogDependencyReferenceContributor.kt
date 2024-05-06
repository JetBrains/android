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
package com.android.tools.idea.gradle.catalog

import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.childrenOfType
import com.intellij.util.ProcessingContext
import com.intellij.util.asSafely
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.name

/**
 * Contributes references for bundle literals to library alias declarations
 */
class VersionCatalogDependencyReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(bundleDeclarationPattern, VersionCatalogBundleReferenceProvider())
  }
}

internal val bundleDeclarationPattern: PsiElementPattern.Capture<TomlLiteral> =
  psiElement(TomlLiteral::class.java)
    .withParent(psiElement(TomlArray::class.java))
    .withSuperParent(3, psiElement(TomlTable::class.java).with(
   object : PatternCondition<TomlTable>(null) {
     override fun accepts(tomlTable: TomlTable, context: ProcessingContext?): Boolean =
       tomlTable.header.key?.segments?.map { it.name } == listOf("bundles")
   }
 ))

private class VersionCatalogBundleReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if(element !is TomlLiteral || element.parent !is TomlArray) return emptyArray()
    val text = StringUtil.unquoteString(element.text)
    return arrayOf(VersionCatalogDeclarationReference(element, text))
  }

  private class VersionCatalogDeclarationReference(literal: TomlLiteral, val text: String) : PsiReferenceBase<TomlLiteral>(literal) {
    override fun resolve(): PsiElement? {
      val file = element.containingFile.asSafely<TomlFile>()
      val targetTable = file?.childrenOfType<TomlTable>()?.find { it.header.key?.name == "libraries" } ?: return null
      val libs = targetTable.childrenOfType<TomlKeyValue>().mapNotNull { it.key.segments.singleOrNull() }
      return libs.find { it.name == text }
    }
  }

}