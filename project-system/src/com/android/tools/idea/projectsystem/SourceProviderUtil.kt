/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("SourceProviderUtil")

package com.android.tools.idea.projectsystem

import com.android.utils.usLocaleDecapitalize
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.text.nullize
import java.io.File

/**
 * Builds [NamedModuleTemplate]'s from [sourceProviders].
 *
 * Each [NamedIdeaSourceProvider] from [sourceProviders] is complemented with
 * any missing production, unit test or android test source providers if missing and available, and the triplet is converted to one
 * [NamedModuleTemplate].
 *
 * For example, `debug` source provider is complemented with `testDebug` and `androidTestDebug` source providers so that
 * the resulting [NamedModuleTemplate] would refer to sources and resources from `debug` provider, unit tests from `testDebug`
 * provider and android tests `androidTestDebug` provider.
 */
fun SourceProviders.buildNamedModuleTemplatesFor(
  moduleRoot: File?,
  sourceProviders: Collection<NamedIdeaSourceProvider>
): List<NamedModuleTemplate> {
  return buildTripletsFrom(sourceProviders)
    .map { triplet ->
      NamedModuleTemplate(triplet.name, AndroidModulePathsImpl(
        moduleRoot = moduleRoot,
        manifestDirectory = triplet.sources?.manifestDirectoryUrls?.first()?.toFile(),
        srcRoot = triplet.sources?.javaDirectoryUrls?.firstOrNull()?.toFile(),
        unitTestRoot = triplet.unitTests?.javaDirectoryUrls?.firstOrNull()?.toFile(),
        testRoot = triplet.androidTests?.javaDirectoryUrls?.firstOrNull()?.toFile(),
        aidlRoot = triplet.sources?.aidlDirectoryUrls?.firstOrNull()?.toFile(),
        resDirectories = triplet.sources?.resDirectoryUrls?.map { it.toFile() }.orEmpty(),
        mlModelsDirectories = triplet.sources?.mlModelsDirectoryUrls?.map { it.toFile() }.orEmpty()
      ))
    }
}

private data class SourceProviderTriplet(
  val name: String,
  val sources: NamedIdeaSourceProvider?,
  val unitTests: NamedIdeaSourceProvider?,
  val androidTests: NamedIdeaSourceProvider?
)

private fun String.stripPrefix(scopeType: ScopeType): String {
  fun String.stripPrefix(prefix: String) = (removePrefix(prefix).nullize() ?: "main").usLocaleDecapitalize()

  return when (scopeType) {
    ScopeType.MAIN -> this
    ScopeType.ANDROID_TEST -> this.stripPrefix("androidTest")
    ScopeType.UNIT_TEST -> this.stripPrefix("test")
    ScopeType.SHARED_TEST -> throw IllegalStateException("Unexpected source provider scope type: $scopeType")
  }
}

private val NamedIdeaSourceProvider.coreName: String get() = name.stripPrefix(scopeType)

private fun SourceProviders.buildTripletsFrom(selectedSourceProviders: Collection<NamedIdeaSourceProvider>): List<SourceProviderTriplet> {
  val unitTestProviders = this.currentUnitTestSourceProviders.associateBy { it.coreName }
  val androidTestProviders = this.currentAndroidTestSourceProviders.associateBy { it.coreName }
  return selectedSourceProviders
    .map {
      val coreName = it.coreName
      SourceProviderTriplet(
        name = it.name,
        sources = it.takeIf { it.scopeType == ScopeType.MAIN },
        unitTests = unitTestProviders[coreName],
        androidTests = androidTestProviders[coreName]
      )
    }
}

private fun String.toFile(): File = File(VfsUtilCore.urlToPath(this))
