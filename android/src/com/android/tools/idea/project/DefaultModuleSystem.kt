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
package com.android.tools.idea.project

import com.android.SdkConstants
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.ide.common.repository.GradleCoordinate
import com.android.manifmerger.ManifestSystemProperty
import com.android.projectmodel.ExternalLibrary
import com.android.projectmodel.Library
import com.android.projectmodel.RecursiveResourceFolder
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.CapabilityNotSupported
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.SampleDataDirectoryProvider
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.res.MainContentRootSampleDataDirectoryProvider
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toPathString
import com.google.common.collect.ImmutableList
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.util.text.nullize
import org.jetbrains.android.dom.manifest.cachedValueFromPrimaryManifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils

private val PACKAGE_NAME = Key.create<CachedValue<String?>>("merged.manifest.package.name")

/** Creates a map for the given pairs, filtering out null values. */
private fun <K, V> notNullMapOf(vararg pairs: Pair<K, V?>): Map<K, V> {
  return pairs.asSequence()
    .filter { it.second != null }
    .toMap() as Map<K, V>
}

class DefaultModuleSystem(override val module: Module) :
  AndroidModuleSystem,
  ClassFileFinder by ModuleBasedClassFileFinder(module),
  SampleDataDirectoryProvider by MainContentRootSampleDataDirectoryProvider(module) {

  override fun canRegisterDependency(type: DependencyType): CapabilityStatus {
    return CapabilityNotSupported()
  }

  override fun registerDependency(coordinate: GradleCoordinate) {
    registerDependency(coordinate, DependencyType.IMPLEMENTATION)
  }

  override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) {
  }

  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? = null

  override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    // TODO(b/79883422): Replace the following code with the correct logic for detecting .aar dependencies.
    // The following if / else if chain maintains previous support for supportlib and appcompat until
    // we can determine it's safe to take away.
    if (SdkConstants.SUPPORT_LIB_ARTIFACT == "${coordinate.groupId}:${coordinate.artifactId}") {
      val entries = ModuleRootManager.getInstance(module).orderEntries
      for (orderEntry in entries) {
        if (orderEntry is LibraryOrderEntry) {
          val classes = orderEntry.getRootFiles(OrderRootType.CLASSES)
          for (file in classes) {
            if (file.name == "android-support-v4.jar") {
              return GoogleMavenArtifactId.SUPPORT_V4.getCoordinate("+")
            }
          }
        }
      }
    }
    else if (SdkConstants.APPCOMPAT_LIB_ARTIFACT == "${coordinate.groupId}:${coordinate.artifactId}") {
      val entries = ModuleRootManager.getInstance(module).orderEntries
      for (orderEntry in entries) {
        if (orderEntry is ModuleOrderEntry) {
          val moduleForEntry = orderEntry.module
          if (moduleForEntry == null || moduleForEntry == module) {
            continue
          }
          AndroidFacet.getInstance(moduleForEntry) ?: continue
          val manifestInfo = MergedManifestManager.getSnapshot(moduleForEntry)
          if ("android.support.v7.appcompat" == manifestInfo.`package`) {
            return GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
          }
        }
      }
    }

    return null
  }

  // We don't offer maven artifact support for JPS projects because there aren't any use cases that requires this feature.
  // JPS also import their dependencies as modules and don't translate very well to the original maven artifacts.
  override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>)
    : Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> {
    return Triple(emptyList(), dependenciesToAdd, "")
  }

  override fun getResourceModuleDependencies() = AndroidUtils.getAllAndroidDependencies(module, true).map(AndroidFacet::getModule)

  override fun getDirectResourceModuleDependents(): List<Module> = ModuleManager.getInstance(module.project).getModuleDependentModules(module)

  override fun getResolvedDependentLibraries(): Collection<Library> {
    val libraries = mutableListOf<Library>()

    ModuleRootManager.getInstance(module)
      .orderEntries()
      .librariesOnly()
      .recursively()
      .forEachLibrary { library ->
        // Typically, a library xml looks like the following:
        //     <CLASSES>
        //      <root url="file://$USER_HOME$/.gradle/caches/transforms-1/files-1.1/appcompat-v7-27.1.1.aar/e2434af65905ee37277d482d7d20865d/res" />
        //      <root url="jar://$USER_HOME$/.gradle/caches/transforms-1/files-1.1/appcompat-v7-27.1.1.aar/e2434af65905ee37277d482d7d20865d/jars/classes.jar!/" />
        //    </CLASSES>
        val roots = library.getFiles(OrderRootType.CLASSES)

        // all libraries are assumed to have a non-empty name
        val classesJar = roots.firstOrNull { it.name == SdkConstants.FN_CLASSES_JAR }?.toPathString()
        val libraryName = library.name ?: return@forEachLibrary true

        // For testing purposes we create libraries with a res.apk root (legacy projects don't have those). Recognize them here and
        // create ExternalLibrary as necessary.
        val resFolderRoot = roots.firstOrNull { it.name == FD_RES }?.toPathString()
        val resApkRoot = roots.firstOrNull { it.name == FN_RESOURCE_STATIC_LIBRARY }?.toPathString()
        val (resFolder, resApk) = when {
          resApkRoot != null -> Pair(resApkRoot.parentOrRoot.resolve(FD_RES), resApkRoot)
          resFolderRoot != null -> Pair(resFolderRoot, resFolderRoot.parentOrRoot.resolve(FN_RESOURCE_STATIC_LIBRARY))
          else -> return@forEachLibrary true
        }

        libraries.add(ExternalLibrary(
          address = libraryName,
          manifestFile = resFolder.parentOrRoot.resolve(FN_ANDROID_MANIFEST_XML),
          classJars = if (classesJar == null) emptyList() else listOf(classesJar),
          resFolder = RecursiveResourceFolder(resFolder),
          symbolFile = resFolder.parentOrRoot.resolve(FN_RESOURCE_TEXT),
          resApkFile = resApk
        ))

        true // continue processing.
      }

    return ImmutableList.copyOf(libraries)
  }

  override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    return emptyList()
  }

  override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
    return CapabilityNotSupported()
  }

  override fun getPackageName(): String? {
    val facet = AndroidFacet.getInstance(module)!!
    val cachedValue = facet.cachedValueFromPrimaryManifest {
      packageName.nullize(true)
    }
    return facet.putUserDataIfAbsent(PACKAGE_NAME, cachedValue).value
  }

  override fun getManifestOverrides(): ManifestOverrides {
    val androidModel = module.androidFacet?.let(AndroidModel::get) ?: return ManifestOverrides()
    val directOverrides = notNullMapOf(
      ManifestSystemProperty.MIN_SDK_VERSION to androidModel.minSdkVersion?.apiString,
      ManifestSystemProperty.TARGET_SDK_VERSION to androidModel.targetSdkVersion?.apiString,
      ManifestSystemProperty.VERSION_CODE to androidModel.versionCode?.takeIf { it > 0 }?.toString(),
      ManifestSystemProperty.PACKAGE to androidModel.applicationId
    )
    return ManifestOverrides(directOverrides)
  }

  override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope {
    val includeTests = when (scopeType) {
      ScopeType.MAIN -> false
      ScopeType.ANDROID_TEST, ScopeType.UNIT_TEST -> true
      else -> error("unknown scope type")
    }
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests)
  }
}
