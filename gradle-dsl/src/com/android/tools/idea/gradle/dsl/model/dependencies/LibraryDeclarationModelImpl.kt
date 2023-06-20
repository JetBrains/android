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

import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationSpec
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleVersionCatalogPropertyModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform
import com.android.tools.idea.gradle.dsl.parser.catalog.FakeDependencyDeclarationElement
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.intellij.psi.PsiElement

abstract class LibraryDeclarationModelImpl(open val dslElement: GradleDslElement): LibraryDeclarationModel {

  companion object {
    @JvmStatic
    fun createNew(parent: GradlePropertiesDslElement,
                  alias: String,
                  dependency: LibraryDeclarationSpec) {
      val declaration = GradleVersionCatalogPropertyModel(parent, PropertyType.REGULAR, alias)
      declaration.getMapValue("group")?.setValue(dependency.getGroup())
      declaration.getMapValue("name")?.setValue(dependency.getName())
      dependency.getVersion()?.let {
        declaration.getMapValue("version")?.setValue(it)
      }
    }
  }

  override fun getSpec(): LibraryDeclarationSpec =
    LibraryDeclarationSpecImpl(name().toString(), group().toString(), version().toString())


  override fun compactNotation(): String = getSpec().compactNotation()

  override fun getPsiElement(): PsiElement? = dslElement.psiElement

}

class MapDependencyDeclarationModel(override val dslElement: GradleDslExpressionMap): LibraryDeclarationModelImpl(dslElement) {
  override fun name(): ResolvedPropertyModel =
    createMaybeFakeModelForModule("name", LibraryDeclarationSpec::getName, LibraryDeclarationSpecImpl::setName)

  override fun group(): ResolvedPropertyModel =
    createMaybeFakeModelForModule("group", LibraryDeclarationSpec::getGroup, LibraryDeclarationSpecImpl::setGroup)

  private fun createMaybeFakeModelForModule(
    name: String, getter: (LibraryDeclarationSpec) -> String?,
    setter: (LibraryDeclarationSpecImpl, String) -> Unit,
  ): ResolvedPropertyModel{
    val module = dslElement.getPropertyElement("module", GradleDslLiteral::class.java)
    if (module != null) {
      val element: FakeElement = FakeDependencyDeclarationElement(dslElement,
                                                                  GradleNameElement.fake(name),
                                                                  module,
                                                                  getter,
                                                                  setter,
                                                                  false)
      return GradlePropertyModelBuilder.create(element).addTransform(FakeElementTransform()).buildResolved()
    }
    return GradlePropertyModelBuilder.create(dslElement, name).buildResolved()
  }

  override fun version(): ResolvedPropertyModel =
    GradlePropertyModelBuilder.create(dslElement, "version").buildResolved()


  override fun completeModel(): ResolvedPropertyModel? =
    GradlePropertyModelBuilder.create(dslElement).buildResolved()

}
class LiteralLibraryDeclarationModel(override val dslElement: GradleDslLiteral): LibraryDeclarationModelImpl(dslElement){

  private fun createModelFor(name: String,
                             getFunc: (LibraryDeclarationSpec) -> String?,
                             setFunc: (LibraryDeclarationSpecImpl, String) -> Unit,
                             canDelete: Boolean,
                             additionalTransformer: PropertyTransform?
  ): ResolvedPropertyModel {
    val element = dslElement
    assert(element.parent!= null)
    val fakeElement: FakeElement = FakeDependencyDeclarationElement(element.parent!!, GradleNameElement.fake(name), element, getFunc, setFunc, canDelete)
    var builder = GradlePropertyModelBuilder.create(fakeElement)
    if (additionalTransformer != null) {
      builder = builder.addTransform(additionalTransformer)
    }
    return builder.addTransform(FakeElementTransform()).buildResolved()
  }
  override fun name(): ResolvedPropertyModel =
    createModelFor("name", LibraryDeclarationSpec::getName, LibraryDeclarationSpecImpl::setName, false, null)

  override fun group(): ResolvedPropertyModel =
    createModelFor("group", LibraryDeclarationSpec::getGroup, LibraryDeclarationSpecImpl::setGroup, false, null)

  override fun version(): ResolvedPropertyModel =
    createModelFor("version", LibraryDeclarationSpec::getVersion, LibraryDeclarationSpecImpl::setVersion, false, null)

  override fun completeModel(): ResolvedPropertyModel? =
     GradlePropertyModelBuilder.create(dslElement).buildResolved()
}

