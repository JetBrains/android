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
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedLibraryDependency
import com.android.tools.idea.gradle.structure.model.helpers.dependencyVersionValues
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.VariableMatchingStrategy
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.meta.property
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import kotlin.reflect.KProperty

class PsDeclaredLibraryJavaDependency(
  parent: PsJavaModule,
  override val parsedModel: ArtifactDependencyModel
) : PsJavaDependency(parent),
    PsLibraryDependency, PsDeclaredDependency, PsDeclaredLibraryDependency {
  override val descriptor by Descriptor
  private val nameResolvedProperty = parsedModel.name()
  private val groupResolvedProperty = parsedModel.group()
  private val versionResolvedProperty = parsedModel.version()
  override val spec: PsArtifactDependencySpec
    get() = PsArtifactDependencySpec.create(
      groupResolvedProperty.toString(),
      nameResolvedProperty.forceString(),
      versionResolvedProperty.toString()
    )

  override val configurationName: String get() = parsedModel.configurationName()

  override val isDeclared: Boolean = true

  override val joinedConfigurationNames: String get() = configurationName

  override val name: String get() = spec.name

  override fun toText(): String = spec.toString()

  override var version by PsDeclaredLibraryJavaDependency.Descriptor.version

  override val versionProperty: ModelSimpleProperty<Unit, String>
    get() = object : ModelSimpleProperty<Unit, String> {
      override val description: String get() = Descriptor.version.description
      override fun bind(model: Unit): ModelPropertyCore<String> = Descriptor.version.bind(this@PsDeclaredLibraryJavaDependency)
      override fun bindContext(model: Unit): ModelPropertyContext<String> =
        Descriptor.version.bindContext(this@PsDeclaredLibraryJavaDependency)

      override fun getValue(thisRef: Unit, property: KProperty<*>): ParsedValue<String> = throw UnsupportedOperationException()
      override fun setValue(thisRef: Unit, property: KProperty<*>, value: ParsedValue<String>) = throw UnsupportedOperationException()
    }

  object Descriptor : ModelDescriptor<PsDeclaredLibraryJavaDependency, Nothing, ArtifactDependencyModel> {
    override fun getResolved(model: PsDeclaredLibraryJavaDependency): Nothing? = null

    override fun getParsed(model: PsDeclaredLibraryJavaDependency): ArtifactDependencyModel? = model.parsedModel

    override fun prepareForModification(model: PsDeclaredLibraryJavaDependency) = Unit

    // TODO(b/118814130): Java resolved dependency collection is not refreshed when requested version changes
    override fun setModified(model: PsDeclaredLibraryJavaDependency) {
      model.isModified = true
      model.parent.fireDependencyModifiedEvent(lazy {
        model.parent.dependencies.findLibraryDependencies(
          model.spec.toLibraryKey()).firstOrNull { it.configurationName == model.configurationName }
      })
    }

    private const val MAX_ARTIFACTS_TO_REQUEST = 50  // Note: we do not expect more than one result per repository.
    val version: ModelSimpleProperty<PsDeclaredLibraryJavaDependency, String> = property(
      "Version",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { this.version() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString,
      knownValuesGetter = ::dependencyVersionValues,
      variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE
    )

    override val properties: Collection<ModelProperty<PsDeclaredLibraryJavaDependency, *, *, *>> = listOf(version)
  }
}

class PsResolvedLibraryJavaDependency(
  parent: PsJavaModule,
  library: JarLibraryDependency,
  override val declaredDependencies: List<PsDeclaredLibraryJavaDependency>
) : PsJavaDependency(parent),
    PsLibraryDependency, PsResolvedDependency, PsResolvedLibraryDependency {
  internal val pomDependencies = mutableListOf<PsArtifactDependencySpec>()

  override val isDeclared: Boolean get() = declaredDependencies.isNotEmpty()

  override val joinedConfigurationNames: String = library.scope ?: ""

  override val spec: PsArtifactDependencySpec = PsArtifactDependencySpec.create(library.moduleVersion!!)

  override fun getParsedModels(): List<DependencyModel> = declaredDependencies.map { it.parsedModel }

  // TODO(b/110778597): Implement library version promotion analysis for Java modules.
  override fun hasPromotedVersion(): Boolean = false

  override val name: String get() = spec.name

  override fun toText(): String = spec.toString()

  override fun getTransitiveDependencies(): Set<PsResolvedLibraryJavaDependency> =
    pomDependencies.flatMap { parent.resolvedDependencies.findLibraryDependencies(it.group, it.name) }.toSet()

  internal fun setDependenciesFromPomFile(value: List<PsArtifactDependencySpec>) {
    pomDependencies.clear()
    pomDependencies.addAll(value)
  }
}
