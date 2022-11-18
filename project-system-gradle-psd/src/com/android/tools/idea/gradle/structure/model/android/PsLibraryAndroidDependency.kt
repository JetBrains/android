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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
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
import com.google.common.base.CaseFormat
import kotlin.reflect.KProperty

open class PsDeclaredLibraryAndroidDependency(
  parent: PsAndroidModule
) : PsLibraryAndroidDependency(parent),
    PsDeclaredDependency, PsDeclaredLibraryDependency {
  final override lateinit var parsedModel: ArtifactDependencyModel ; private set
  override val descriptor by Descriptor
  override val spec: PsArtifactDependencySpec
    get() = PsArtifactDependencySpec.create(
      parsedModel.group().toString(),
      parsedModel.name().forceString(),
      parsedModel.version().toString()
    )

  fun init(parsedModel: ArtifactDependencyModel) {
    this.parsedModel = parsedModel
  }

  override fun canExtractVariable(): Boolean = !parsedModel.isVersionCatalogDependency

  override val isDeclared: Boolean = true
  final override val configurationName: String get() = parsedModel.configurationName()
  override val joinedConfigurationNames: String get() = configurationName

  override var version by Descriptor.version
  override val versionProperty: ModelSimpleProperty<Unit, String>
    get() = object : ModelSimpleProperty<Unit, String> {
      override val description: String get() = Descriptor.version.description
      override fun bind(model: Unit): ModelPropertyCore<String> = Descriptor.version.bind(this@PsDeclaredLibraryAndroidDependency)
      override fun bindContext(model: Unit): ModelPropertyContext<String> =
        Descriptor.version.bindContext(this@PsDeclaredLibraryAndroidDependency)

      override fun getValue(thisRef: Unit, property: KProperty<*>): ParsedValue<String> = throw UnsupportedOperationException()
      override fun setValue(thisRef: Unit, property: KProperty<*>, value: ParsedValue<String>) = throw UnsupportedOperationException()
    }

  object Descriptor : ModelDescriptor<PsDeclaredLibraryAndroidDependency, Nothing, ArtifactDependencyModel> {
    override fun getResolved(model: PsDeclaredLibraryAndroidDependency): Nothing? = null

    override fun getParsed(model: PsDeclaredLibraryAndroidDependency): ArtifactDependencyModel? = model.parsedModel

    override fun prepareForModification(model: PsDeclaredLibraryAndroidDependency) = Unit

    // TODO(solodkyy): Ensure setModified refreshes the resolved dependency collection when required.
    override fun setModified(model: PsDeclaredLibraryAndroidDependency) {
      // NOTE: There is no need to re-index the declared dependency collection. Version is not a part of the key.
      model.isModified = true
      // TODO(solodkyy): Make setModified() customizable at the property level since some properties will need to call resetDependencies().
      model.parent.resetResolvedDependencies()
      model.parent.fireDependencyModifiedEvent(lazy {
        model.parent.dependencies.findLibraryDependencies(
          model.spec.toLibraryKey()).firstOrNull { it.configurationName == model.configurationName }
      })
    }

    private fun preferredVariableName (model: ArtifactDependencyModel): String {
      val name = model.name().getValue(GradlePropertyModel.STRING_TYPE) ?: return "var"
      return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, "$name-version")
    }

    val version: ModelSimpleProperty<PsDeclaredLibraryAndroidDependency, String> = property(
      "Version",
      preferredVariableName = { preferredVariableName(this.parsedModel) },
      resolvedValueGetter = { null },
      parsedPropertyGetter = { this.version() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString,
      knownValuesGetter = ::dependencyVersionValues,
      variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE
    )

    override val properties: Collection<ModelProperty<PsDeclaredLibraryAndroidDependency, *, *, *>> = listOf(version)
  }
}

open class PsResolvedLibraryAndroidDependency(
  parent: PsAndroidModule,
  val collection: PsAndroidArtifactDependencyCollection,
  override val spec: PsArtifactDependencySpec,
  val artifact: PsAndroidArtifact,
  override val declaredDependencies: List<PsDeclaredLibraryAndroidDependency>
) : PsLibraryAndroidDependency(parent), PsResolvedDependency, PsResolvedLibraryDependency {
  internal val pomDependencies = mutableListOf<PsArtifactDependencySpec>()
  override val isDeclared: Boolean get() = !declaredDependencies.isEmpty()

  override fun hasPromotedVersion(): Boolean = getReverseDependencies().any { it.isPromoted }

  fun getReverseDependencies(): Set<ReverseDependency> =
    artifact.dependencies.reverseDependencies[spec.toLibraryKey()].orEmpty()

  override fun getTransitiveDependencies(): Set<PsResolvedLibraryAndroidDependency> =
    pomDependencies.flatMap { artifact.dependencies.findLibraryDependencies(it.group, it.name) }.toSet()

  internal fun setDependenciesFromPomFile(value: List<PsArtifactDependencySpec>) {
    pomDependencies.clear()
    pomDependencies.addAll(value)
  }
}

abstract class PsLibraryAndroidDependency internal constructor(
  parent: PsAndroidModule
) : PsAndroidDependency(parent), PsLibraryDependency {

  override val name: String get() = spec.name

  override fun toText(): String = spec.toString()

  override fun toString(): String = toText()
}
