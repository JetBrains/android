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
package org.jetbrains.kotlin.android.models

import com.android.builder.model.proto.ide.Library
import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Version
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.ResolverType
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl.Companion.wellKnownOrCreate
import com.android.tools.idea.gradle.model.impl.IdeResolvedLibraryTableImpl
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependencyCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.gradle.idea.tcs.extras.documentationClasspath
import org.jetbrains.kotlin.gradle.idea.tcs.extras.sourcesClasspath
import org.jetbrains.kotlin.tooling.core.WeakInterner
import java.io.File

/**
 * Used to convert models coming from the build side in the kotlin model extras to the IDE models representation.
 */
class KotlinModelConverter {
  private val interner = WeakInterner(lock = null) // No need for a lock since the resolution happens sequentially.

  private val seenDependencies = mutableMapOf<IdeaKotlinDependencyCoordinates, LibraryReference>()
  private val libraries = mutableListOf<IdeLibrary>()

  private val useAdditionalArtifactsFromLibraries by lazy {
    GradleExperimentalSettings.getInstance().USE_MULTI_VARIANT_EXTRA_ARTIFACTS &&
    StudioFlags.GRADLE_MULTI_VARIANT_ADDITIONAL_ARTIFACT_SUPPORT.get()
  }

  private fun String.deduplicate() = interner.getOrPut(this)
  private fun File.deduplicateFile(): File = File(path.deduplicate())
  private fun com.android.builder.model.proto.ide.File.convertAndDeduplicate() = File(absolutePath.deduplicate())

  private fun computeForCoordinatesIfAbsent(
    coordinates: IdeaKotlinDependencyCoordinates?,
    action: () -> LibraryReference
  ): LibraryReference {
    return if (coordinates == null) {
      action()
    } else {
      seenDependencies.computeIfAbsent(coordinates) {
        action()
      }
    }
  }

  private fun recordLibraryDependency(library: IdeLibrary): LibraryReference {
    val index = libraries.size
    libraries.add(library)
    return LibraryReference(index, ResolverType.KMP_ANDROID)
  }

  private fun androidLibraryFrom(
    androidLibrary: Library,
    coordinates: IdeaKotlinDependencyCoordinates?
  ): LibraryReference {
    return computeForCoordinatesIfAbsent(coordinates) {
      val libraryInfo = androidLibrary.libraryInfo ?: error("libraryInfo missing for ${androidLibrary.key}")

      val androidLibraryData = androidLibrary.androidLibraryData ?: error("androidLibraryData missing for ${androidLibrary.key}")

      val artifactAddress = "${libraryInfo.group}:${libraryInfo.name}:${libraryInfo.version}@aar"
      val library = IdeAndroidLibraryImpl.create(
        artifactAddress = artifactAddress,
        name = coordinates?.toString() ?: artifactAddress,
        component = androidLibrary.getComponent(),
        folder = androidLibraryData.resFolder.convertAndDeduplicate().parentFile.deduplicateFile(),
        artifact = if (androidLibrary.hasArtifact()) androidLibrary.artifact.convertAndDeduplicate() else File(""),
        lintJar = if (androidLibrary.hasLintJar()) androidLibrary.lintJar.convertAndDeduplicate().path else null,
        srcJar = if (useAdditionalArtifactsFromLibraries && androidLibrary.hasSrcJar()) androidLibrary.srcJar.convertAndDeduplicate().path else null,
        docJar = if (useAdditionalArtifactsFromLibraries && androidLibrary.hasDocJar()) androidLibrary.docJar.convertAndDeduplicate().path else null,
        samplesJar = if (useAdditionalArtifactsFromLibraries && androidLibrary.hasSamplesJar()) androidLibrary.samplesJar.convertAndDeduplicate().path else null,
        manifest = androidLibraryData.manifest.convertAndDeduplicate().path ?: "",
        compileJarFiles = androidLibraryData.compileJarFilesList.map { it.convertAndDeduplicate().path },
        runtimeJarFiles = androidLibraryData.runtimeJarFilesList.map { it.convertAndDeduplicate().path },
        resFolder = androidLibraryData.resFolder.convertAndDeduplicate().path ?: "",
        resStaticLibrary = androidLibraryData.resStaticLibrary.convertAndDeduplicate(),
        assetsFolder = androidLibraryData.assetsFolder.convertAndDeduplicate().path ?: "",
        jniFolder = androidLibraryData.jniFolder.convertAndDeduplicate().path ?: "",
        aidlFolder = androidLibraryData.aidlFolder.convertAndDeduplicate().path ?: "",
        renderscriptFolder = androidLibraryData.renderscriptFolder.convertAndDeduplicate().path ?: "",
        proguardRules = androidLibraryData.proguardRules.convertAndDeduplicate().path ?: "",
        externalAnnotations = androidLibraryData.externalAnnotations.convertAndDeduplicate().path ?: "",
        publicResources = androidLibraryData.publicResources.convertAndDeduplicate().path ?: "",
        symbolFile = androidLibraryData.symbolFile.convertAndDeduplicate().path,
        deduplicate = { this.deduplicate() }
      )

      recordLibraryDependency(library)
    }
  }

  fun Library.getComponent() = if (hasLibraryInfo()) {
    when (libraryInfo.group) {
      "__local_aars__", "__wrapped_aars__", "__local_asars__", "artifacts" -> null
      else -> Component(libraryInfo.group, libraryInfo.name, Version.parse(libraryInfo.version))
    }
  } else {
    null
  }

  /**
   * Converts kotlin's dependency notion into the notion used in the IDE models, caches the result and returns a reference to the created
   * library.
   */
  fun recordDependency(dependency: IdeaKotlinDependency): LibraryReference? {
    val libraryReference = when (dependency) {
      is IdeaKotlinBinaryDependency -> {
        val dependencyInfo = dependency.extras[androidDependencyKey]

        if (dependencyInfo != null) {
          androidLibraryFrom(dependencyInfo.library, dependency.coordinates)
        } else if (dependency is IdeaKotlinResolvedBinaryDependency) {
          computeForCoordinatesIfAbsent(dependency.coordinates) {
            recordLibraryDependency(
              IdeJavaLibraryImpl(
                artifactAddress = dependency.coordinates.toString(),
                name = dependency.coordinates.toString(),
                component = dependency.coordinates?.let {
                  if (it.version != null) {
                    Component(it.group, it.module, Version.parse(it.version!!))
                  } else {
                    null
                  }
                },
                artifact = dependency.classpath.first(),
                srcJar = if (useAdditionalArtifactsFromLibraries) dependency.sourcesClasspath.firstOrNull() else null,
                docJar = if (useAdditionalArtifactsFromLibraries) dependency.documentationClasspath.firstOrNull() else null,
                samplesJar = null,
              )
            )
          }
        } else {
          null
        }
      }

      is IdeaKotlinSourceDependency -> {
        computeForCoordinatesIfAbsent(dependency.coordinates) {
          recordLibraryDependency(
            IdeModuleLibraryImpl(
              buildId = dependency.coordinates.buildId,
              projectPath = dependency.coordinates.projectPath,
              variant = null, // TODO(b/269755640): how to combine the flavors here in the right order?
              lintJar = null,
              sourceSet = wellKnownOrCreate(dependency.coordinates.sourceSetName)
            )
          )
        }
      }

      else -> null
    }

    return libraryReference
  }


  /**
   * Creates the library table data node and attaches it to the project. When that happens, we don't need the cache for libraries anymore,
   * and so they're disposed.
   */
  fun maybeCreateLibraryTable(projectNode: DataNode<ProjectData>) {
    if (ExternalSystemApiUtil.find(projectNode, AndroidProjectKeys.KMP_ANDROID_LIBRARY_TABLE) == null) {
      projectNode.createChild(
        AndroidProjectKeys.KMP_ANDROID_LIBRARY_TABLE,
        IdeResolvedLibraryTableImpl(
          libraries = libraries.map { listOf(it) }
        )
      )

      seenDependencies.clear()
      libraries.clear()
    }
  }
}