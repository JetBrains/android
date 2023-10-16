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
package com.android.tools.idea.gradle.navigation

import com.android.SdkConstants
import com.android.tools.idea.gradle.util.findCatalogKey
import com.android.tools.idea.gradle.util.findVersionCatalog
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

/**
 * Go to declaration handler for providing navigation from references to version
 * catalog references -- both from references in KTS files (e.g. from dependency
 * and plugin references) and within TOML files (e.g. from library version variable
 * references and from bundle array references).
 *
 * Surely the right solution here is to actually have an index, and to perform
 * indexing of version catalog files. This is a temporary quick solution
 * which manually looks for the gradle catalogs and parses them on the fly.
 */
class VersionCatalogGoToDeclarationHandler : GotoDeclarationHandlerBase() {
  override fun getGotoDeclarationTarget(sourceElement: PsiElement?, editor: Editor?): PsiElement? {
    sourceElement ?: return null
    val parent = sourceElement.parent ?: return null
    val grandParent = parent.parent ?: return null

    //TODO add support of non trivial cases like  "id(libs.plugins.android.application.get().pluginId) apply false"
    // Reference from build.gradle.kts to dependency?
    if (grandParent is KtDotQualifiedExpression) {
      val key = grandParent.text
      val catalog = findVersionCatalog(key, sourceElement.project)
      if (catalog != null && grandParent.containingFile.name.endsWith(SdkConstants.DOT_KTS)) {
        // If you have dashes in the library name, Gradle will convert this into dotted notation, and will actually
        // create a "group" DSL object for the libraries sharing the same prefix. But we typically don't have a
        // TOML object for this; e.g. we may have "constraint-layout=X", and maybe "constraint-solver=Y"; if we've
        // tried to navigate to the "constraint" part of "libs.constraint.layout", we shouldn't attempt to find
        // a key named "constraint" in the TOML file, we'll look for the whole reference instead.
        val argument = grandParent.getParentOfType<KtValueArgument>(true) ?: return null
        val fullKey = argument.text
        return findCatalogKey(catalog, fullKey.substringAfter("."))
      }
      return null
    }

    // Reference within a Groovy build.gradle file
    // In declaration api libs.plugins.ksp we can jump to plugins table and ksp plugin declaration
    // IMPORTANT: in case JetBrains provides native handler [GradleVersionCatalogTomlAwareGotoDeclarationHandler]
    // It will be called first. Once it navigates somewhere, Idea stops checking other handlers.
    // That means current handler will not be called. System works with multiple handlers cover same cases -
    // first in line wins.
    if (parent is GrReferenceExpression) {
      val catalog = findVersionCatalog(parent.text, parent.project)
      if (catalog != null && grandParent.containingFile.name.endsWith(SdkConstants.DOT_GRADLE)) {
        val key = parent.text
        if (key != null) {
          // search by whole key: attempting to find elements (e.g. the `plugins` table) by subkey is inconsistent with the
          // platform and also vulnerable to users defining keys which are effectively prefixes of each other.
          val wholeKey = getWholeKey(sourceElement)
          if (wholeKey != null) {
            findCatalogKey(catalog, wholeKey.substringAfter("."))?.let { return it }
          }
        }
        return null
      }
    }

    // Reference within TOML file
    if (parent is TomlLiteral) {
      if (grandParent is TomlArray) {
        // Bundle definition -- string value points to a library key
        val file = parent.containingFile as? TomlFile ?: return null
        val libraryVar = parent.getString()
        return findCatalogKey(file, libraryVar)
      } else if (grandParent is TomlKeyValue) {
        // version references in library definition -- string value points to a version variable key
        if (grandParent.key.text == "version.ref") {
          val versionVar = parent.getString()
          val file = parent.containingFile as? TomlFile ?: return null
          return findCatalogKey(file, "versions.$versionVar")
        }
      }
    }

    return null
  }

  /**
   * Need to travel up through Psi tree until parent is
   * - GrArgumentList for 'api libs.my.lib'
   * - GrCommandArgumentList 'alias(libs.plugins.myplugin)'
   */
  private fun getWholeKey(sourceElement: PsiElement): String? {
    var currElement = sourceElement
    while (currElement.parent != null) {
      if (currElement.parent is GrArgumentList || currElement.parent is GrCommandArgumentList) {
        return currElement.text
      }
      else currElement = currElement.parent
    }
    return null
  }

}

private fun TomlLiteral.getString(): String =
   (kind as? TomlLiteralKind.String)?.value ?: text

