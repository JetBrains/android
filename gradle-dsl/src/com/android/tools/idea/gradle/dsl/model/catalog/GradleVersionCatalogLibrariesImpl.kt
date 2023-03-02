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
package com.android.tools.idea.gradle.dsl.model.catalog

import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogLibraries
import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationSpec
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModelImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.LibraryDeclarationModelImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.LibraryDeclarationSpecImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.LiteralLibraryDeclarationModel
import com.android.tools.idea.gradle.dsl.model.dependencies.MapDependencyDeclarationModel
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement

class GradleVersionCatalogLibrariesImpl (private val dslElement: GradlePropertiesDslElement): GradleDslBlockModel(dslElement), GradleVersionCatalogLibraries {
  companion object {
    val LOG = Logger.getInstance(DependenciesModelImpl::class.java)
  }
  override fun getAllAliases(): Set<String> =
    dslElement.propertyElements.map{it.key}.toSet()

  override fun getAll(): Map<String, LibraryDeclarationModel> =
    dslElement.allPropertyElements.flatMap{ dslProperty ->
      val alias = dslProperty.nameElement.name()
      return@flatMap when (dslProperty) {
        is GradleDslLiteral -> listOf(alias to LiteralLibraryDeclarationModel(dslProperty))
        is GradleDslExpressionMap -> listOf(alias to MapDependencyDeclarationModel(dslProperty))
        else -> listOf()
      }
    }.toMap()

  override fun addDeclaration(alias: String, compactNotation: String) {
    val dependency: LibraryDeclarationSpec? = LibraryDeclarationSpecImpl.create(compactNotation)
    if (dependency == null) {
      val msg = String.format("'%1\$s' is not a valid artifact dependency", compactNotation)
      LOG.warn(msg)
      return
    }
    addDeclaration(alias, dependency)
  }

  override fun getPsiElement(): PsiElement? = dslElement.psiElement

  override fun addDeclaration(alias: String, dependencySpec: LibraryDeclarationSpec) =
    LibraryDeclarationModelImpl.createNew(myDslElement, alias, dependencySpec)

  override fun remove(alias: String) {
    val dependency = getAll()[alias]
    if (dependency == null) {
      DependenciesModelImpl.LOG.warn("Tried to remove an unknown dependency: $alias")
      return
    }

    assert(dependency is LibraryDeclarationModelImpl)
    val dependencyElement = (dependency as LibraryDeclarationModelImpl).dslElement

    val parent = dependencyElement.parent
    if (parent is GradlePropertiesDslElement){
      parent.removeProperty(alias)
    }

  }

}