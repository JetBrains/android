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
package com.android.tools.idea.projectsystem

import com.android.resources.ResourceFolderType
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * Immutable data object responsible for determining all the files that contribute to
 * the merged manifest of a particular [AndroidFacet] at a particular moment in time.
 *
 * Note that any navigation files are also considered contributors, since you can
 * specify the <nav-graph> tag in your manifest and the navigation component will
 * replace it at merge time with intent filters derived from the module's navigation
 * files. See https://developer.android.com/guide/navigation/navigation-deep-link
 */
data class MergedManifestContributors(
  @JvmField val primaryManifest: VirtualFile?,
  @JvmField val flavorAndBuildTypeManifests: List<VirtualFile>,
  @JvmField val libraryManifests: List<VirtualFile>,
  @JvmField val navigationFiles: List<VirtualFile>,
  @JvmField val flavorAndBuildTypeManifestsOfLibs: List<VirtualFile>) {

  @JvmField
  val allFiles = flavorAndBuildTypeManifests +
                 listOfNotNull(primaryManifest) +
                 libraryManifests +
                 navigationFiles +
                 flavorAndBuildTypeManifestsOfLibs
}

fun AndroidModuleSystem.defaultGetMergedManifestContributors(): MergedManifestContributors {
  val facet = module.androidFacet!!
  val dependencies = getResourceModuleDependencies().mapNotNull { it.androidFacet }
  return MergedManifestContributors(
    primaryManifest = facet.sourceProviders.mainManifestFile,
    flavorAndBuildTypeManifests = facet.getFlavorAndBuildTypeManifests(),
    libraryManifests = if (facet.configuration.isAppOrFeature) facet.getLibraryManifests(dependencies) else emptyList(),
    navigationFiles = facet.getTransitiveNavigationFiles(dependencies),
    flavorAndBuildTypeManifestsOfLibs = facet.getFlavorAndBuildTypeManifestsOfLibs(dependencies)
  )
}

fun AndroidFacet.getFlavorAndBuildTypeManifests(): List<VirtualFile> {
  // get all other manifests for this module, (NOT including the default one)
  val sourceProviderManager = sourceProviders
  val defaultSourceProvider = sourceProviderManager.mainIdeaSourceProvider
  return sourceProviderManager.currentSourceProviders
    // currentSourceProviders is in overlay order (later files override earlier ones),
    // but the manifest merger expects *reverse* overlay order (earlier files take priority).
    .asReversed()
    .filter { it != defaultSourceProvider }
    .flatMap { it.manifestFiles }
}

fun AndroidFacet.getFlavorAndBuildTypeManifestsOfLibs(dependencies: List<AndroidFacet>): List<VirtualFile> {
  return dependencies.flatMap(AndroidFacet::getFlavorAndBuildTypeManifests)
}

private fun AndroidFacet.getLibraryManifests(dependencies: List<AndroidFacet>): List<VirtualFile> {
  if (isDisposed) return emptyList()
  return dependencies.mapNotNull { it.sourceProviders.mainManifestFile }
}


/**
 * Returns all navigation files for the facet's module and its transitive dependencies,
 * ordered from higher precedence to lower precedence.
 * TODO(b/70815924): Change implementation to use resource repository API
 */
fun AndroidFacet.getTransitiveNavigationFiles(transitiveDependencies: List<AndroidFacet>): List<VirtualFile> {
  return (sequenceOf(this) + transitiveDependencies.asSequence())
    .flatMap { it.getNavigationFiles() }
    .toList()
}

private fun AndroidFacet.getNavigationFiles(): Sequence<VirtualFile> {
  return sourceProviders.currentSourceProviders
    .asReversed() // iterate over providers in reverse order so higher precedence navigation files are first
    .asSequence()
    .flatMapWithoutNulls { provider -> provider.resDirectories.asSequence() }
    .flatMapWithoutNulls { resDir -> resDir.children?.asSequence() }
    .filter { resDirFolder -> ResourceFolderType.getFolderType(resDirFolder.name) == ResourceFolderType.NAVIGATION }
    .flatMapWithoutNulls { navDir -> navDir.children?.asSequence() }
    .filter { potentialNavFile -> !potentialNavFile.isDirectory }
}

private fun <T, R : Any> Sequence<T>.flatMapWithoutNulls(transform: (T) -> Sequence<R?>?): Sequence<R> {
  return flatMap { transform(it) ?: emptySequence() }.filterNotNull()
}
