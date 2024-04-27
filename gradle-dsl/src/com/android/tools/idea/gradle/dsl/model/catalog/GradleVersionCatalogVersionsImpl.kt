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

import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogVersions
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationSpec
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.dependencies.LiteralVersionDeclarationModel
import com.android.tools.idea.gradle.dsl.model.dependencies.MapVersionDeclarationModel
import com.android.tools.idea.gradle.dsl.model.dependencies.VersionDeclarationModelImpl
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.intellij.openapi.diagnostic.Logger

class GradleVersionCatalogVersionsImpl(private val dslElement: GradlePropertiesDslElement): GradleDslBlockModel(dslElement), GradleVersionCatalogVersions {
  companion object {
    val LOG = Logger.getInstance(GradleVersionCatalogVersionsImpl::class.java)
  }

  override fun getAllAliases(): Set<String> =
    dslElement.propertyElements.map{it.key}.toSet()


  override fun getAll(): Map<String, VersionDeclarationModel> =
    dslElement.allPropertyElements.flatMap{ dslProperty ->
    val alias = dslProperty.nameElement.name()
    return@flatMap when (dslProperty) {
      is GradleDslLiteral -> listOf(alias to LiteralVersionDeclarationModel(dslProperty))
      is GradleDslExpressionMap -> listOf(alias to MapVersionDeclarationModel(dslProperty))
      else -> listOf()
    }
  }.toMap()

  override fun addDeclaration(alias: String, version: String): VersionDeclarationModel? =
    LiteralVersionDeclarationModel.createNew(dslElement, alias, version)


  override fun addDeclaration(alias: String, version: VersionDeclarationSpec): VersionDeclarationModel? =
    MapVersionDeclarationModel.createNew(dslElement, alias, version)

  override fun remove(alias: String) {
    val dependency = getAll()[alias]
    if (dependency == null) {
      LOG.warn("Tried to remove an unknown dependency: $alias")
      return
    }

    assert(dependency is VersionDeclarationModelImpl)
    val dependencyElement = (dependency as VersionDeclarationModelImpl).dslElement

    val parent = dependencyElement.parent
    if (parent is GradlePropertiesDslElement){
      parent.removeProperty(alias)
    }
  }
}