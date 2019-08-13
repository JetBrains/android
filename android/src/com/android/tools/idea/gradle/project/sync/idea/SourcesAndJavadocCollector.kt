/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.builder.model.AndroidProject
import com.android.builder.model.BaseArtifact
import com.android.builder.model.Library
import com.android.builder.model.Variant
import com.android.ide.common.repository.GradleVersion
import com.android.model.sources.SourcesAndJavadocArtifactIdentifier
import com.android.model.sources.SourcesAndJavadocArtifacts
import com.android.model.sources.SourcesAndJavadocParameter
import com.android.tools.idea.gradle.project.sync.idea.svs.AndroidModule
import com.google.common.annotations.VisibleForTesting
import org.gradle.tooling.BuildController
import org.jetbrains.plugins.gradle.model.ProjectImportExtraModelProvider

@UsedInBuildAction
fun getSourcesAndJavadocArtifacts(
  controller: BuildController,
  inputModules: List<AndroidModule>,
  cachedSourcesAndJavadoc: Collection<String>,
  consumer: ProjectImportExtraModelProvider.BuildModelConsumer
) {
  inputModules.forEach { module ->
    if (!is3Dot5OrNewer(module.androidProject)) return@forEach

    // Get variants from AndroidProject if it's not empty, otherwise get from VariantGroup.
    // The first case indicates full-variants sync and the later single-variant sync.
    val variants = if (module.androidProject.variants.isNotEmpty()) module.androidProject.variants else module.variantGroup.variants
    // Collect the library identifiers to download sources and javadoc for, and filter the cached ones.
    val identifiers = collectIdentifiers(variants).filter { !cachedSourcesAndJavadoc.contains(idToString(it)) }

    // Query for SourcesAndJavadocArtifacts model.
    if (identifiers.isNotEmpty()) {
      controller.findModel(module.gradleProject, SourcesAndJavadocArtifacts::class.java,
                           SourcesAndJavadocParameter::class.java) { parameter ->
        parameter.artifactIdentifiers = identifiers
      }?.also {
        consumer.consume(module.ideaModule, it, SourcesAndJavadocArtifacts::class.java)
      }
    }
  }
}

@UsedInBuildAction
private fun collectIdentifiers(
  variants: Collection<Variant>
): List<SourcesAndJavadocArtifactIdentifier> {
  val libraries = mutableListOf<Library>()
  // Collect libraries from all artifacts of all variants.
  @Suppress("DEPRECATION")
  variants.forEach { variant ->
    val artifacts = mutableListOf<BaseArtifact>(variant.mainArtifact)
    artifacts.addAll(variant.extraAndroidArtifacts)
    artifacts.addAll(variant.extraJavaArtifacts)
    artifacts.forEach {
      libraries.addAll(it.compileDependencies.javaLibraries)
      libraries.addAll(it.compileDependencies.libraries)
    }
  }

  return libraries.filter { it.project == null }.map { it.resolvedCoordinates }.toSet().map {
    object : SourcesAndJavadocArtifactIdentifier {
      override fun getVersion(): String = it.version
      override fun getArtifactId(): String = it.artifactId
      override fun getGroupId(): String = it.groupId
    }
  }
}

@VisibleForTesting
@UsedInBuildAction
fun is3Dot5OrNewer(project: AndroidProject): Boolean {
  try {
    GradleVersion.tryParse(project.modelVersion)?.let {
      return it.isAtLeast(3, 5, 0)
    }
  }
  catch (ignored: Throwable) {
    // If model version doesn't exist or is mal-formatted, this is very likely an old AGP.
  }
  return false
}

@UsedInBuildAction
fun idToString(identifier: SourcesAndJavadocArtifactIdentifier): String {
  return identifier.groupId + ":" + identifier.artifactId + ":" + identifier.version
}
