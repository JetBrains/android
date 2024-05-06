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
package com.android.tools.idea.gradle.declarative

import com.android.SdkConstants
import com.android.tools.idea.gradle.util.findCatalogKey
import com.android.tools.idea.gradle.util.findVersionCatalog
import com.android.tools.idea.gradle.util.generateExistingPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral

class DeclarativeCatalogDependencyReferenceContributor: PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(dependencyReferencePattern, VersionCatalogReferenceProvider())
  }

  private val dependencyReferencePattern: PsiElementPattern.Capture<TomlLiteral> =
    psiElement(TomlLiteral::class.java)
      .withParent(psiElement(TomlKeyValue::class.java).withChild(psiElement(TomlKey::class.java).withText("alias")))
      .inFile(psiFile().withName(StandardPatterns.string().endsWith(SdkConstants.EXT_GRADLE_TOML)))
      .with(
        object : PatternCondition<TomlLiteral>(null) {
          override fun accepts(tomlLiteral: TomlLiteral, context: ProcessingContext?): Boolean {
            val path = generateExistingPath(tomlLiteral)
            return "dependencies" == path.firstOrNull()
          }
        }
      )
}

private class VersionCatalogReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if(!Registry.`is`("android.gradle.declarative.plugin.studio.support", false)) return emptyArray()
    if(element !is TomlLiteral) return emptyArray()
    val text = StringUtil.unquoteString(element.text)
    return arrayOf(VersionCatalogDeclarationReference(element, text))
  }

  private class VersionCatalogDeclarationReference(literal: TomlLiteral, val reference: String) : PsiReferenceBase<TomlLiteral>(literal) {
    override fun resolve(): PsiElement? {
      val project = element.project
      val file = findVersionCatalog(reference, project)?: return null
      return findCatalogKey(file, reference.substringAfter("."))
    }
  }

}