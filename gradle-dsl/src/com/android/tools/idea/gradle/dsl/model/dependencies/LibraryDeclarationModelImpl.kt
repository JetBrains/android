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
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationModel
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleVersionCatalogPropertyModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform
import com.android.tools.idea.gradle.dsl.parser.catalog.FakeDependencyDeclarationElement
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile.GradleDslVersionLiteral
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement

abstract class LibraryDeclarationModelImpl(open val dslElement: GradleDslElement) : LibraryDeclarationModel {
  companion object {
    val LOG = Logger.getInstance(LibraryDeclarationModelImpl::class.java)
  }
  override fun getSpec(): LibraryDeclarationSpec =
    LibraryDeclarationSpecImpl(name().toString(), group().toString(), version().getSpec())

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

  class MapDependencyDeclarationModel(override val dslElement: GradleDslExpressionMap) : LibraryDeclarationModelImpl(dslElement) {
    companion object {
      /**
       * Creates library declaration as map with in place version
       */
      @JvmStatic
      fun createNew(parent: GradlePropertiesDslElement,
                    alias: String,
                    dependency: LibraryDeclarationSpec): MapDependencyDeclarationModel {
        val declaration = GradleVersionCatalogPropertyModel(parent, PropertyType.REGULAR, alias)
        declaration.getMapValue("group").setValue(dependency.getGroup())
        declaration.getMapValue("name").setValue(dependency.getName())
        dependency.getVersion()?.let {
          if(it.compactNotation() == null) {
            LOG.warn("Version for dependency ${dependency.getName()} does not have string notation")
          }
          declaration.getMapValue("version").setValue(it.compactNotation() ?: "")
        }
        return MapDependencyDeclarationModel(declaration.element as GradleDslExpressionMap)
      }

      /**
       * Creates library declaration as map with version as reference
       */
      @JvmStatic
      fun createNew(parent: GradlePropertiesDslElement,
                    alias: String,
                    name: String,
                    group: String,
                    version: ReferenceTo): MapDependencyDeclarationModel {
        val declaration = GradleVersionCatalogPropertyModel(parent, PropertyType.REGULAR, alias)
        declaration.getMapValue("group").setValue(group)
        declaration.getMapValue("name").setValue(name)
        declaration.getMapValue("version").setValue(version)
        return MapDependencyDeclarationModel(declaration.element as GradleDslExpressionMap)
      }

      /**
       * For special cases when version is missed in declaration and will be taken from BOM
       */
      @JvmStatic
      fun createNew(parent: GradlePropertiesDslElement,
                    alias: String,
                    name: String,
                    group: String): MapDependencyDeclarationModel {
        val declaration = GradleVersionCatalogPropertyModel(parent, PropertyType.REGULAR, alias)
        declaration.getMapValue("group").setValue(group)
        declaration.getMapValue("name").setValue(name)
        return MapDependencyDeclarationModel(declaration.element as GradleDslExpressionMap)
      }
    }

    override fun name(): ResolvedPropertyModel =
      createMaybeFakeModelForModule("name", LibraryDeclarationSpec::getName, LibraryDeclarationSpecImpl::setName)

    override fun group(): ResolvedPropertyModel =
      createMaybeFakeModelForModule("group", LibraryDeclarationSpec::getGroup, LibraryDeclarationSpecImpl::setGroup)

    private fun createMaybeFakeModelForModule(
      name: String,
      getter: (LibraryDeclarationSpec) -> String?,
      setter: (LibraryDeclarationSpecImpl, String) -> Unit,
    ): ResolvedPropertyModel {
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

    override fun version(): VersionDeclarationModel =
      createVersionDeclarationModel(dslElement.getPropertyElement("version"), dslElement, "version")

    override fun updateVersion(compactNotation: String) {
      dslElement.removeProperty("version")
      dslElement.addNewLiteral("version", compactNotation)
    }

    override fun updateVersion(versionReference: VersionDeclarationModel) {
      val oldVersion = dslElement.getPropertyElement("version")
      val reference = ReferenceTo(versionReference)
      val newVersion = GradleDslVersionLiteral(dslElement,
                                               GradleNameElement.create("version"),
                                               reference.javaClass)

      if (oldVersion == null)
        dslElement.addParsedElement(newVersion)
      else{
        dslElement.removeProperty(oldVersion)
        dslElement.setNewElement(newVersion)
        newVersion.setValue(reference)
      }
    }

    override fun completeModel(): ResolvedPropertyModel? =
      GradlePropertyModelBuilder.create(dslElement).buildResolved()

  }

  class LiteralLibraryDeclarationModel(override val dslElement: GradleDslLiteral) : LibraryDeclarationModelImpl(dslElement) {

    companion object {
      /**
       * Creates library declaration as a literal
       */
      @JvmStatic
      fun createNew(parent: GradlePropertiesDslElement,
                    alias: String,
                    compactNotation: String): LiteralLibraryDeclarationModel {
        val literal = parent.setNewLiteral(alias, compactNotation)
        return LiteralLibraryDeclarationModel(literal)
      }
    }

    private fun createModelFor(name: String,
                               getFunc: (LibraryDeclarationSpec) -> String?,
                               setFunc: (LibraryDeclarationSpecImpl, String) -> Unit

    ): ResolvedPropertyModel {
      val element = dslElement
      assert(element.parent != null)
      val fakeElement: FakeElement = FakeDependencyDeclarationElement(element.parent!!, GradleNameElement.fake(name), element, getFunc,
                                                                      setFunc, false)
      val builder = GradlePropertyModelBuilder.create(fakeElement)
      return builder.addTransform(FakeElementTransform()).buildResolved()
    }

    override fun name(): ResolvedPropertyModel =
      createModelFor("name", LibraryDeclarationSpec::getName, LibraryDeclarationSpecImpl::setName)

    override fun group(): ResolvedPropertyModel =
      createModelFor("group", LibraryDeclarationSpec::getGroup, LibraryDeclarationSpecImpl::setGroup)

    override fun version(): VersionDeclarationModel {
      return createVersionDeclarationModel(createVersionElement())
    }

    private fun createVersionElement(): FakeDependencyDeclarationElement {
      assert(dslElement.parent != null) // parent cannot be null as library declaration is always under libraries table
      return FakeDependencyDeclarationElement(
        dslElement.parent!!,
        GradleNameElement.fake("version"),
        dslElement,
        { spec: LibraryDeclarationSpec -> spec.getVersion()?.compactNotation() },
        LibraryDeclarationSpecImpl::setStringVersion,
        false)
    }

    override fun updateVersion(compactNotation: String) {
      createVersionElement().setValue(compactNotation)
    }

    override fun updateVersion(version: VersionDeclarationModel) {
      createVersionElement().setValue(version.getSpec())
    }

    override fun completeModel(): ResolvedPropertyModel? =
      GradlePropertyModelBuilder.create(dslElement).buildResolved()
  }
}

