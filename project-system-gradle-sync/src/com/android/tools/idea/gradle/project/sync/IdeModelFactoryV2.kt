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
package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.ProjectInfo
import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.IdeUnresolvedLibrary
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdePreResolvedModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedKmpAndroidModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedModuleLibraryImpl
import org.gradle.api.attributes.java.TargetJvmEnvironment
import java.io.File

class IdeModelFactoryV2(
  agpVersion: AgpVersion,
  multiVariantAdditionalArtifactSupport: Boolean,
) {

  /**
   * Sources, JavaDocs and Samples are only provided in libraries after AGP version 8.1.0-alpha8.
   * Any attempt to read these values from a version prior to this will result in an exception.
   */
  val isAGPVersion8dot1dot0alpha8orLater = agpVersion.isAtLeast(8, 1, 0, "alpha", 8, false)
  val useAdditionalArtifactsFromLibraries = isAGPVersion8dot1dot0alpha8orLater && multiVariantAdditionalArtifactSupport
  val supportsAbsoluteGradleBuildPaths = agpVersion.isAtLeast(8, 2, 0, "alpha", 13, false)

  fun androidLibraryFrom(androidLibrary: Library, deduplicate: String.() -> String) : IdeAndroidLibraryImpl {
    fun File.deduplicateFile(): File = File(path.deduplicate())

    val libraryInfo = androidLibrary.libraryInfo ?: error("libraryInfo missing for ${androidLibrary.key}")

    val androidLibraryData = androidLibrary.androidLibraryData ?: error("androidLibraryData missing for ${androidLibrary.key}")

    val artifactAddress = "${libraryInfo.group}:${libraryInfo.name}:${libraryInfo.version}@aar"
    return IdeAndroidLibraryImpl.create(
      artifactAddress = artifactAddress,
      component = androidLibrary.getComponent(),
      name = "",
      folder = androidLibraryData.resFolder.parentFile.deduplicateFile(),
      artifact = androidLibrary.artifact ?: File(""),
      lintJar = androidLibrary.lintJar?.path,
      srcJar = if (useAdditionalArtifactsFromLibraries) androidLibrary.srcJar?.path else null,
      docJar = if (useAdditionalArtifactsFromLibraries) androidLibrary.docJar?.path else null,
      samplesJar = if (useAdditionalArtifactsFromLibraries) androidLibrary.samplesJar?.path else null,
      manifest = androidLibraryData.manifest.path ?: "",
      compileJarFiles = androidLibraryData.compileJarFiles.map { it.path },
      runtimeJarFiles = androidLibraryData.runtimeJarFiles.map { it.path },
      resFolder = androidLibraryData.resFolder.path ?: "",
      resStaticLibrary = androidLibraryData.resStaticLibrary,
      assetsFolder = androidLibraryData.assetsFolder.path ?: "",
      jniFolder = androidLibraryData.jniFolder.path ?: "",
      aidlFolder = androidLibraryData.aidlFolder.path ?: "",
      renderscriptFolder = androidLibraryData.renderscriptFolder.path ?: "",
      proguardRules = androidLibraryData.proguardRules.path ?: "",
      externalAnnotations = androidLibraryData.externalAnnotations.path ?: "",
      publicResources = androidLibraryData.publicResources.path ?: "",
      symbolFile = androidLibraryData.symbolFile.path,
      deduplicate = deduplicate
    )
  }

  fun javaLibraryFrom(javaLibrary: Library) : IdeJavaLibraryImpl {
    val libraryInfo = javaLibrary.libraryInfo ?: error("libraryInfo missing for ${javaLibrary.key}")
    val artifactAddress = "${libraryInfo.group}:${libraryInfo.name}:${libraryInfo.version}@jar"
    return IdeJavaLibraryImpl(
      artifactAddress = artifactAddress,
      component = javaLibrary.getComponent(),
      name = "",
      artifact = javaLibrary.artifact!!,
      srcJar = if (useAdditionalArtifactsFromLibraries) javaLibrary.srcJar else null,
      docJar = if (useAdditionalArtifactsFromLibraries) javaLibrary.docJar else null,
      samplesJar = if (useAdditionalArtifactsFromLibraries) javaLibrary.samplesJar else null,
    )
  }

  fun moduleLibraryFrom(
    library: Library,
    androidProjectPathResolver: AndroidProjectPathResolver,
    buildPathMap: Map<String, BuildId>
  ) : IdeUnresolvedLibrary {
    val projectInfo = library.projectInfo!!
    val projectPath = projectInfo.projectPath
    val lintJar = library.lintJar
    val buildId = buildPathMap.buildPathToBuildId(projectInfo)
    // TODO(b/203750717): Model this explicitly in the tooling model.
    val artifact : ArtifactRef =
      if (projectInfo.isKmpAndroidComponent()) {
        KmpAndroidArtifactRef
      } else if (projectInfo.isAndroidComponent()) {
        val androidModule: AndroidModule =
          androidProjectPathResolver.resolve(buildId, projectInfo.projectPath)
          ?: error("Cannot find an Android module: ${projectInfo.displayName}")
        val variantName = androidModule.resolveVariantName(projectInfo, buildId)
        AndroidArtifactRef(variantName, projectInfo.isTestFixtures)
      } else {
        library.artifact?.let {NonAndroidAndroidArtifactRef(it)} ?: error(
          "Unresolved module dependency ${projectInfo.displayName} in " +
          "$projectPath ($buildId). Neither the source set nor the artifact property was populated" +
          " by the Android Gradle plugin."
        )
      }
    return when (artifact) {
      is AndroidArtifactRef ->
        IdePreResolvedModuleLibraryImpl(
          buildId = buildId.asString,
          projectPath = projectPath,
          variant = artifact.variantName,
          lintJar = lintJar?.path?.let { File(it) },
          sourceSet = if (artifact.isTestFixture) IdeModuleWellKnownSourceSet.TEST_FIXTURES else IdeModuleWellKnownSourceSet.MAIN
        )
      is KmpAndroidArtifactRef ->
        IdeUnresolvedKmpAndroidModuleLibraryImpl(
          buildId = buildId.asString,
          projectPath = projectPath,
          lintJar = lintJar?.path?.let { File(it) },
        )
      is NonAndroidAndroidArtifactRef ->
        IdeUnresolvedModuleLibraryImpl(
          buildId = buildId.asString,
          projectPath = projectPath,
          variant = null,
          lintJar = lintJar?.path?.let { File(it) },
          artifact = artifact.artifactFile
        )
    }
  }

  // --------

  private fun Map<String, BuildId>.buildPathToBuildId(projectInfo: ProjectInfo): BuildId {
    val buildTreePath = projectInfo.buildTreePath
    val buildId = if (supportsAbsoluteGradleBuildPaths) {
      this[projectInfo.buildTreePath]
    } else {
      // If there is no full support for Gradle build paths, we are looking only to match the last segment.
      // E.g. ":nested" matches ":includedBuild:nested".
      entries.firstOrNull { it.key.endsWith(buildTreePath) }?.value
    }
    return buildId ?: error("Unknown build name: '${projectInfo.displayName}'. Known names $this")
  }

  fun AndroidModule.resolveVariantName(
    projectInfo: ProjectInfo,
    buildId: BuildId
  ): String {
    return this.androidVariantResolver.resolveVariant(
      buildType = projectInfo.buildType,
      productFlavors = { dimension ->
        projectInfo.productFlavors[dimension]
        ?: run {
          // See: b/242856048 and b/242289523: Flavors in `FAKE_DIMENSION` are not reported here.
          if (dimension == FAKE_DIMENSION) {
            val suffix = projectInfo.buildType.orEmpty()
            projectInfo
              .attributes["com.android.build.gradle.internal.attributes.VariantAttr"]
              ?.takeIf { it.endsWith(suffix, ignoreCase = true) }
              ?.let { it.substring(0, it.length - suffix.length) }
          } else null
        }
        ?: error(
          "$dimension attribute not found in a dependency model " +
          "of ${projectInfo.projectPath} ($buildId) " +
          "on  ${projectInfo.displayName}"
        )
      }
    )
           ?: error(
             "Cannot find a variant matching build type '${projectInfo.buildType}' " +
             "and product flavors '${projectInfo.productFlavors}' " +
             "in ${projectInfo.displayName} " +
             "referred from ${projectInfo.projectPath} (${buildId})"
           )
  }

}


private sealed class ArtifactRef
private object KmpAndroidArtifactRef : ArtifactRef()
private data class AndroidArtifactRef(val variantName: String, val isTestFixture: Boolean) : ArtifactRef()
private data class NonAndroidAndroidArtifactRef(val artifactFile: File) : ArtifactRef()

/**
 * Determines whether a dependency target is an Android component.
 *
 * It was initially assumed that the Android Gradle plugin should only populate `identifier.artifact` property
 * for non-Android components. However, it happened that it is also not null in the case of dependencies of
 * `androidTest` (or similarly of `TEST_ONLY` projects) artifact on `main` artifacts.
 *
 * Also, unfortunately, the artifact file returned in this case is not known to the IDE.
 *
 * Temporarily, detect Android components by presence of a build type or a product flavor.
 */
private fun ProjectInfo.isAndroidComponent(): Boolean = buildType != null || productFlavors.isNotEmpty()

private fun ProjectInfo.isKmpAndroidComponent(): Boolean =
  attributes["org.jetbrains.kotlin.platform.type"] == "jvm" &&
  attributes[TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE.name] == TargetJvmEnvironment.ANDROID

private fun Library.getComponent() = libraryInfo?.let {
  when (it.group) {
    "__local_aars__", "__wrapped_aars__", "__local_asars__", "artifacts" -> null
    else -> Component(it.group, it.name, Version.parse(it.version))
  }
}