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

import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationSpec
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleVersionCatalogPropertyModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform
import com.android.tools.idea.gradle.dsl.parser.catalog.FakeVariableDeclarationElement
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.intellij.psi.PsiElement

abstract class VersionDeclarationModelImpl(open val dslElement: GradleDslElement) : GradleVersionCatalogPropertyModel(
  dslElement), VersionDeclarationModel {

  override fun getSpec(): VersionDeclarationSpec =
    VersionDeclarationSpecImpl(require().toString(), strictly().toString(), prefer().toString())

  override fun compactNotation(): String? = getSpec().compactNotation()

  override fun getPsiElement(): PsiElement? = dslElement.psiElement

}

class MapVersionDeclarationModel(override val dslElement: GradleDslExpressionMap) : VersionDeclarationModelImpl(dslElement) {
  companion object {
    @JvmStatic
    fun createNew(parent: GradlePropertiesDslElement,
                  alias: String,
                  version: VersionDeclarationSpec): MapVersionDeclarationModel? {
      val declaration = GradleDslExpressionMap(parent, GradleNameElement.create(alias))
      version.getStrictly()?.let {
        declaration.addNewLiteral("strictly", it)
      }
      version.getPrefer()?.let {
        declaration.addNewLiteral("prefer", it)
      }
      version.getRequire()?.let {
        declaration.addNewLiteral("require", it)
      }
      parent.setNewElement(declaration)
      return MapVersionDeclarationModel(declaration)
    }
  }

  override fun require(): ResolvedPropertyModel =
    GradlePropertyModelBuilder.create(dslElement, "require").buildResolved()

  override fun strictly(): ResolvedPropertyModel =
    GradlePropertyModelBuilder.create(dslElement, "strictly").buildResolved()

  override fun prefer(): ResolvedPropertyModel =
    GradlePropertyModelBuilder.create(dslElement, "prefer").buildResolved()

  override fun completeModel(): ResolvedPropertyModel? =
    GradlePropertyModelBuilder.create(dslElement).buildResolved()
}

class LiteralVersionDeclarationModel(override val dslElement: GradleDslLiteral) : VersionDeclarationModelImpl(dslElement) {

  companion object {
    @JvmStatic
    fun createNew(parent: GradlePropertiesDslElement,
                  alias: String,
                  version: String): LiteralVersionDeclarationModel? {
      if(version.isBlank()) return null
      val literal = parent.setNewLiteral(alias,version)
      return LiteralVersionDeclarationModel(literal)
    }
  }

  private fun createModelFor(name: String,
                             getFunc: (VersionDeclarationSpec) -> String?,
                             setFunc: (VersionDeclarationSpecImpl, String) -> Unit): ResolvedPropertyModel {
    val element = dslElement
    assert(element.parent != null)
    val fakeElement: FakeElement = FakeVariableDeclarationElement(element.parent!!, GradleNameElement.fake(name), element, getFunc, setFunc,
                                                                  false)
    val builder = GradlePropertyModelBuilder.create(fakeElement)
    return builder.addTransform(FakeElementTransform()).buildResolved()
  }

  override fun require(): ResolvedPropertyModel =
    createModelFor("require", VersionDeclarationSpec::getRequire, VersionDeclarationSpecImpl::setRequire)

  override fun strictly(): ResolvedPropertyModel =
    createModelFor("strictly", VersionDeclarationSpec::getStrictly, VersionDeclarationSpecImpl::setStrictly)

  override fun prefer(): ResolvedPropertyModel =
    createModelFor("prefer", VersionDeclarationSpec::getPrefer, VersionDeclarationSpecImpl::setPrefer)

  override fun completeModel(): ResolvedPropertyModel? =
    GradlePropertyModelBuilder.create(dslElement).buildResolved()
}

class FakeVersionDeclarationModel(override val dslElement: FakeElement) : VersionDeclarationModelImpl(dslElement) {

  private fun createModelFor(name: String,
                             getFunc: (VersionDeclarationSpec) -> String?,
                             setFunc: (VersionDeclarationSpecImpl, String) -> Unit
  ): ResolvedPropertyModel {
    val element = dslElement
    assert(element.parent != null)
    val fakeElement: FakeElement = FakeVariableDeclarationElement(element.parent!!, GradleNameElement.fake(name), element, getFunc, setFunc,
                                                                  false)
    val builder = GradlePropertyModelBuilder.create(fakeElement)
    return builder.addTransform(FakeElementTransform()).buildResolved()
  }

  override fun require(): ResolvedPropertyModel =
    createModelFor("require", VersionDeclarationSpec::getRequire, VersionDeclarationSpecImpl::setRequire)

  override fun strictly(): ResolvedPropertyModel =
    createModelFor("strictly", VersionDeclarationSpec::getStrictly, VersionDeclarationSpecImpl::setRequire)

  override fun prefer(): ResolvedPropertyModel =
    createModelFor("prefer", VersionDeclarationSpec::getPrefer, VersionDeclarationSpecImpl::setPrefer)

  override fun completeModel(): ResolvedPropertyModel? =
    GradlePropertyModelBuilder.create(dslElement).buildResolved()
}