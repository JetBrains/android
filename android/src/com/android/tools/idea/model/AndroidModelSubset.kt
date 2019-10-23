/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("AndroidModelSubsetUtil")

package com.android.tools.idea.model

import com.android.ide.common.repository.GradleCoordinate
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.AndroidModel
import com.android.projectmodel.AndroidSubmodule
import com.android.projectmodel.Artifact
import com.android.projectmodel.Config
import com.android.projectmodel.ConfigAssociation
import com.android.projectmodel.ConfigPath
import com.android.projectmodel.SubmodulePath
import com.android.projectmodel.Variant
import com.android.projectmodel.matchAllArtifacts
import com.android.projectmodel.matchNoArtifacts
import com.android.projectmodel.toConfigPath
import com.android.projectmodel.visitEach

/**
 * Identifies a subset of [Config] instances across a set of [AndroidSubmodule].
 * This determines what subset of a model should be active when the user
 * selects an "active variant". [SelectedVariantPaths] is backed by a map from
 * submodule names to the [ConfigPath] that intersects all the selected
 * variants.
 */
typealias SelectedVariantPaths = Map<String, ConfigPath>

/**
 * Holds a selected set of variants from an [AndroidModel].
 *
 * When we speak of the Android Model for a given Module, there's two important components. There is the [AndroidModel]
 * that is computed by the build system, and there is the selected variants which are either selected by the user
 * or all-inclusive.
 *
 * The pairing of user selection and build-system-derived information is what this object holds. It identifies a
 * subset of the model that is currently "selected", and also exposes the original model for operations that don't
 * care about the current selection.
 */
data class AndroidModelSubset(val model: AndroidModel, val selection: SelectedVariantPaths) {
  /**
   * Holds the path to a [Variant] along with the [AndroidSubmodule] that contains it.
   */
  data class VariantContext(val submodule: AndroidSubmodule, val variantPath: SubmodulePath) {
    /**
     * Returns an [Artifact] belonging to this variant
     */
    fun getArtifact(artifactName: String) : Artifact? = submodule.getArtifact(variantPath + artifactName)
  }

  /**
   * Holds an [Artifact] along with the [AndroidSubmodule] and [Variant] that contains it.
   */
  data class ArtifactContext(val submodule: AndroidSubmodule, val artifactPath: SubmodulePath, val artifact: Artifact)

  /**
   * Returns the first main artifact in this variant selection.
   */
  fun firstMainArtifact(): ArtifactContext? = selectedArtifacts(ARTIFACT_NAME_MAIN).firstOrNull()

  /**
   * Returns the selected [Config] instances. The result will be returned in order of precedence, with
   * higher precedence [Config] instances near the end of the list.
   *
   * @param artifactNames if specified, the result will only include [Config] instances for artifacts with one of the
   * given names. If no names are provided, the result will include [Config] instances from all artifacts.
   */
  fun selectedConfigs(vararg artifactNames: String): List<ConfigAssociation> =
    selection.flatMap { entry ->
      model.getSubmodule(entry.key)?.findConfigsFor(entry.value, *artifactNames).orEmpty()
    }

  /**
   * Returns all [ConfigAssociation] instances for the given artifact(s), including configs for deselected
   * variants.
   *
   * @param artifactNames if specified, the result will only include [Config] instances for artifacts with one of the
   * given names. If no names are provided, the result will include [Config] instances from all artifacts.
   */
  fun allConfigs(vararg artifactNames: String): List<ConfigAssociation> =
    model.submodules.flatMap { it.findConfigsFor(matchAllArtifacts(), *artifactNames) }

  /**
   * Returns all selected variants.
   */
  fun selectedVariants(): Sequence<VariantContext> =
    flatMapEachSelectedSubmodule { submodule, selection ->
      submodule.configTable.schema.allVariantPaths(selection).map {
        VariantContext(submodule, it)
      }
    }

  /**
   * Returns all artifacts with the given name that are currently selected in the given model.
   */
  fun selectedArtifacts(artifactName: String): Sequence<ArtifactContext> =
    selectedVariants().mapNotNull { context ->
      getArtifactContext(context.submodule, context.variantPath + artifactName)
    }

  private fun getArtifactContext(submodule: AndroidSubmodule, path: SubmodulePath): ArtifactContext? {
    val artifact = submodule.getArtifact(path) ?: return null
    return ArtifactContext(submodule, path, artifact)
  }

  private fun <T> flatMapEachSelectedSubmodule(func: (AndroidSubmodule, ConfigPath) -> Sequence<T>): Sequence<T> = selection.entries.asSequence().flatMap { entry ->
    model.getSubmodule(entry.key)?.let { project ->
      func(project, entry.value)
    } ?: emptySequence()
  }

  /**
   * Returns all selected artifacts in the model.
   */
  fun selectedArtifacts(): Sequence<ArtifactContext> =
    flatMapEachSelectedSubmodule { submodule, selection ->
      submodule.configTable.schema.allArtifactPaths(selection).mapNotNull {
        getArtifactContext(submodule, it)
      }
    }

  /**
   * Returns true if any of the selected artifacts depend on a library that matches the given gradle coordinate (optionally including +).
   */
  fun dependsOn(searchCriteria: GradleCoordinate): Boolean =
    selectedArtifacts().flatMap { it.artifact.resolved.compileDeps.orEmpty().asSequence() }
      .visitEach()
      .any { it.resolvedMavenCoordinate?.matches(searchCriteria) ?: false }

  private fun AndroidSubmodule.findConfigsFor(filter: ConfigPath, vararg artifactName: String): List<ConfigAssociation> =
    if (artifactName.isEmpty()) {
      configTable.filterIntersecting(filter).associations
    } else {
      val artifactFilters = artifactName.map {configTable.schema.matchArtifact(it)}
      configTable.filter {it.path.intersects(filter) && artifactFilters.any(it.path::intersects) }.associations
    }
}

/**
 * Returns an [AndroidModelSubset] that selects the [Variant] of the given name in all [AndroidSubmodule] within the given
 * model.
 */
fun selectVariant(model: AndroidModel, variant: String): AndroidModelSubset =
  AndroidModelSubset(model,
                     model.submodules.map { it.name to (it.getVariantPathByName(variant)?.toConfigPath() ?: matchNoArtifacts()) }.toMap())

/**
 * Returns an [AndroidModelSubset] that selects everything from the given [AndroidModel].
 */
fun selectAllVariants(model: AndroidModel): AndroidModelSubset =
  AndroidModelSubset(model, model.submodules.map { it.name to matchAllArtifacts() }.toMap())
