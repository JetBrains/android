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
  val unitTestProviders = this.currentUnitTestSourceProviders.associateBy { it.coreName }
  val androidTestProviders = this.currentAndroidTestSourceProviders.associateBy { it.coreName }

  return sourceProviders.map { provider ->
    val srcRoot = if (provider.scopeType == ScopeType.MAIN) provider.javaDirectoryUrls.firstOrNull()?.toFile()
    else null

    val coreName = provider.coreName // Main source provider should also contain unit tests and Android tests
    val unitTestRoot = if (provider.scopeType == ScopeType.MAIN || provider.scopeType == ScopeType.UNIT_TEST)
      unitTestProviders[coreName]?.javaDirectoryUrls?.firstOrNull()?.toFile()
    else null

    // Main and Unit Test source provider should also contain Android tests
    val testRoot = if (provider.scopeType == ScopeType.MAIN || provider.scopeType == ScopeType.ANDROID_TEST || provider.scopeType == ScopeType.UNIT_TEST)
      androidTestProviders[coreName]?.javaDirectoryUrls?.firstOrNull()?.toFile()
    else null

    NamedModuleTemplate(provider.name, AndroidModulePathsImpl(
      moduleRoot = moduleRoot,
      manifestDirectory = provider.manifestDirectoryUrls.firstOrNull()?.toFile(),
      srcRoot = srcRoot,
      unitTestRoot = unitTestRoot,
      testRoot = testRoot,
      aidlRoot = provider.aidlDirectoryUrls.firstOrNull()?.toFile(),
      resDirectories = provider.takeIf { it.scopeType.canHaveAndroidResources }?.resDirectoryUrls?.map { it.toFile() }.orEmpty(),
      mlModelsDirectories = provider.mlModelsDirectoryUrls.map { it.toFile() }
    ))
  }
}

private fun String.stripPrefix(scopeType: ScopeType): String {
  fun String.stripPrefix(prefix: String) = (removePrefix(prefix).nullize() ?: "main").usLocaleDecapitalize()

  return when (scopeType) {
    ScopeType.MAIN -> this
    ScopeType.ANDROID_TEST -> this.stripPrefix("androidTest")
    ScopeType.TEST_FIXTURES -> this.stripPrefix("testFixtures")
    ScopeType.UNIT_TEST -> this.stripPrefix("test")
    ScopeType.SHARED_TEST -> throw IllegalStateException("Unexpected source provider scope type: $scopeType")
  }
}

private val NamedIdeaSourceProvider.coreName: String get() = name.stripPrefix(scopeType)

private fun String.toFile(): File = File(VfsUtilCore.urlToPath(this))
