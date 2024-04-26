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
package com.android.tools.idea.gradle.dsl.model.dependencies

import com.android.tools.idea.gradle.dsl.api.dependencies.PluginDeclarationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.PluginDeclarationSpec
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleVersionCatalogPropertyModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform
import com.android.tools.idea.gradle.dsl.parser.catalog.FakePluginDeclarationElement
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement

abstract class PluginDeclarationModelImpl(open val dslElement: GradleDslElement) : PluginDeclarationModel {
  companion object {
    val LOG = Logger.getInstance(PluginDeclarationModelImpl::class.java)
  }

  override fun getSpec(): PluginDeclarationSpec =
    PluginDeclarationSpecImpl(id().toString(), version().getSpec())

  override fun compactNotation(): String = getSpec().compactNotation()

  override fun getPsiElement(): PsiElement? = dslElement.psiElement

  protected fun createVersionDeclarationModel(dslElement: GradleDslElement): VersionDeclarationModel {
    return when (val foundElement = PropertyUtil.followElement(dslElement)) {
      is GradleDslLiteral -> LiteralVersionDeclarationModel(foundElement)
      is GradleDslExpressionMap -> MapVersionDeclarationModel(foundElement)
      is FakeElement -> FakeVersionDeclarationModel(foundElement)
      else -> throw RuntimeException("Trying create version out of ${foundElement?.elementType}")
    }
  }

  /**
   * Creates VersionDeclarationModel with no DSL element as it does not exist yet
   * by default we add literal property to map as version
   */
  protected fun createVersionDeclarationModel(dslElement: GradleDslElement?,
                                              holder: GradlePropertiesDslElement,
                                              alias: String): VersionDeclarationModel {
    val element = dslElement ?: GradleDslLiteral(holder, GradleNameElement.create(alias))
    return createVersionDeclarationModel(element)
  }

  class MapPluginDeclarationModel(override val dslElement: GradleDslExpressionMap) : PluginDeclarationModelImpl(dslElement) {
    companion object {
      /**
       * Creates plugin declaration as map with in place version
       */
      @JvmStatic
      fun createNew(parent: GradlePropertiesDslElement,
                    alias: String,
                    dependency: PluginDeclarationSpec): MapPluginDeclarationModel {
        val declaration = GradleVersionCatalogPropertyModel(parent, PropertyType.REGULAR, alias)
        declaration.getMapValue("id").setValue(dependency.getId())
        dependency.getVersion().let {
          if(it.compactNotation() == null) {
            LOG.warn("Version for dependency ${dependency.getId()} does not have string notation")
          }
          declaration.getMapValue("version").setValue(it.compactNotation() ?: "")
        }
        return MapPluginDeclarationModel(declaration.element as GradleDslExpressionMap)
      }

      /**
       * Creates plugin declaration as map with version as reference
       */
      @JvmStatic
      fun createNew(parent: GradlePropertiesDslElement,
                    alias: String,
                    id: String,
                    version: ReferenceTo): MapPluginDeclarationModel {
        val declaration = GradleVersionCatalogPropertyModel(parent, PropertyType.REGULAR, alias)
        declaration.getMapValue("id").setValue(id)
        declaration.getMapValue("version").setValue(version)
        return MapPluginDeclarationModel(declaration.element as GradleDslExpressionMap)
      }
    }

    override fun id(): ResolvedPropertyModel =
      GradlePropertyModelBuilder.create(dslElement, "id").buildResolved()

    override fun version(): VersionDeclarationModel =
      createVersionDeclarationModel(dslElement.getPropertyElement("version"), dslElement, "version")


    override fun completeModel(): ResolvedPropertyModel? =
      GradlePropertyModelBuilder.create(dslElement).buildResolved()

  }

  class LiteralPluginDeclarationModel(override val dslElement: GradleDslLiteral) : PluginDeclarationModelImpl(dslElement) {

    companion object {
      /**
       * Creates plugin declaration as a literal
       */
      @JvmStatic
      fun createNew(parent: GradlePropertiesDslElement,
                    alias: String,
                    compactNotation: String): LiteralPluginDeclarationModel {
        val literal = parent.setNewLiteral(alias, compactNotation)
        return LiteralPluginDeclarationModel(literal)
      }
    }

    override fun id(): ResolvedPropertyModel {
      val element = dslElement
      assert(element.parent != null)
      val fakeElement: FakeElement = FakePluginDeclarationElement(element.parent!!, GradleNameElement.fake("id"), element, PluginDeclarationSpec::getId,
                                                                  PluginDeclarationSpecImpl::setId, false)
      val builder = GradlePropertyModelBuilder.create(fakeElement)
      return builder.addTransform(FakeElementTransform()).buildResolved()
    }

    override fun version(): VersionDeclarationModel {
      val element = dslElement
      assert(element.parent != null)
      val fakeElement: FakeElement = FakePluginDeclarationElement(element.parent!!,
                                                                      GradleNameElement.fake("version"),
                                                                      element,
                                                                      { spec: PluginDeclarationSpec -> spec.getVersion()?.compactNotation() },
                                                                      PluginDeclarationSpecImpl::setStringVersion,
                                                                      false)
      return createVersionDeclarationModel(fakeElement)
    }

    override fun completeModel(): ResolvedPropertyModel? =
      GradlePropertyModelBuilder.create(dslElement).buildResolved()
  }
}