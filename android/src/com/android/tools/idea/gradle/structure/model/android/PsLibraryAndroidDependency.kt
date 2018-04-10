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

import com.android.builder.model.level2.Library
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsDependency
import com.android.tools.idea.gradle.structure.model.PsDependency.TextType.PLAIN_TEXT
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.google.common.collect.ImmutableSet
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import javax.swing.Icon

open class PsLibraryAndroidDependency internal constructor(
  parent: PsAndroidModule,
  override val spec: PsArtifactDependencySpec,
  containers: Collection<PsAndroidArtifact>,
  override val resolvedModel: Library?,
  parsedModels: Collection<ArtifactDependencyModel>
) : PsAndroidDependency(parent, containers, parsedModels), PsLibraryDependency {
  private val pomDependencies = mutableListOf<PsArtifactDependencySpec>()

  internal fun setDependenciesFromPomFile(value: List<PsArtifactDependencySpec>) {
    pomDependencies.clear()
    pomDependencies.addAll(value)
  }

  fun getTransitiveDependencies(artifactDependencies: PsAndroidDependencyCollection): Set<PsLibraryAndroidDependency> {
    val transitive = ImmutableSet.builder<PsLibraryAndroidDependency>()
    for (dependency in pomDependencies) {
      // TODO(b/74948244): Include the requested version as a parsed model so that we see any promotions.
      val found = artifactDependencies.findLibraryDependencies(dependency.group, dependency.name)
      transitive.addAll(found)
    }

    return transitive.build()
  }

  override fun addParsedModel(parsedModel: DependencyModel) {
    assert(parsedModel is ArtifactDependencyModel)
    super.addParsedModel(parsedModel)
  }

  override val name: String get() = spec.name

  override val icon: Icon get() = LIBRARY_ICON

  override fun toText(type: PsDependency.TextType): String = spec.toString()

  override fun hasPromotedVersion(): Boolean {
    // TODO(solodkyy): Review usages in the case of declared dependencies.
    val declaredSpecs = parsedModels.map {
      PsArtifactDependencySpec.create(it as ArtifactDependencyModel)
    }
    for (declaredSpec in declaredSpecs) {
      if (spec.version != null && declaredSpec.version != null) {
        val declaredVersion = GradleVersion.tryParse(declaredSpec.version!!)
        if (declaredVersion != null && declaredVersion < spec.version!!) {
          return true
        }
      }
    }
    return false
  }

  override fun toString(): String = toText(PLAIN_TEXT)
}
