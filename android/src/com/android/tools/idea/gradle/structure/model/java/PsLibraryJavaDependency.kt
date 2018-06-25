/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.java

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.model.java.JarLibraryDependency
import com.android.tools.idea.gradle.structure.model.*
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearchService
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import javax.swing.Icon
import kotlin.reflect.KProperty

class PsLibraryJavaDependency(
  parent: PsJavaModule,
  override val spec: PsArtifactDependencySpec,
  val resolvedModel: JarLibraryDependency?,
  override val parsedModel: ArtifactDependencyModel
) : PsJavaDependency(parent),
    PsLibraryDependency, PsDeclaredDependency, PsResolvedDependency, PsDeclaredLibraryDependency, PsResolvedLibraryDependency {
  override val declaredDependencies: List<PsDeclaredDependency> = listOf()

  override val configurationName: String = parsedModel.configurationName()

  override fun getParsedModels(): List<DependencyModel> = listOf(parsedModel)

  override val isDeclared: Boolean = true

  override val joinedConfigurationNames: String = configurationName

  override val name: String get() = spec.name

  override val icon: Icon get() = LIBRARY_ICON

  override fun hasPromotedVersion(): Boolean = false

  override fun toText(type: PsDependency.TextType): String = spec.toString()

  var version by PsLibraryJavaDependency.Descriptor.version

  override val versionProperty: ModelSimpleProperty<ArtifactRepositorySearchService, Unit, String>
    get() = object : ModelSimpleProperty<ArtifactRepositorySearchService, Unit, String> {
      override val description: String get() = Descriptor.version.description
      override fun bind(model: Unit): ModelPropertyCore<String> = Descriptor.version.bind(this@PsLibraryJavaDependency)
      override fun bindContext(context: ArtifactRepositorySearchService, model: Unit): ModelPropertyContext<String> =
        Descriptor.version.bindContext(context, this@PsLibraryJavaDependency)

      override fun getValue(thisRef: Unit, property: KProperty<*>): ParsedValue<String> = throw UnsupportedOperationException()
      override fun setValue(thisRef: Unit, property: KProperty<*>, value: ParsedValue<String>) = throw UnsupportedOperationException()
    }

  object Descriptor : ModelDescriptor<PsLibraryJavaDependency, Nothing, ArtifactDependencyModel> {
    override fun getResolved(model: PsLibraryJavaDependency): Nothing? = null

    override fun getParsed(model: PsLibraryJavaDependency): ArtifactDependencyModel? = model.parsedModel

    // TODO(solodkyy): Ensure setModified refreshes the resolved dependency collection when required.
    override fun setModified(model: PsLibraryJavaDependency) {
      model.isModified = true
    }

    val version: ModelSimpleProperty<ArtifactRepositorySearchService, PsLibraryJavaDependency, String> = property(
      "Version",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { this.version() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )
  }
}
