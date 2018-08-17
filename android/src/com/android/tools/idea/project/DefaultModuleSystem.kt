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
import com.android.SdkConstants.*
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.util.PathString
import com.android.projectmodel.AarLibrary
import com.android.projectmodel.JavaLibrary
import com.android.projectmodel.Library
import com.android.tools.idea.model.MergedManifest
import com.android.tools.idea.projectsystem.*
import com.google.common.collect.ImmutableList
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

class DefaultModuleSystem(val module: Module) : AndroidModuleSystem, ClassFileFinder by ModuleBasedClassFileFinder(module) {

  override fun registerDependency(coordinate: GradleCoordinate) {}

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
          val manifestInfo = MergedManifest.get(moduleForEntry)
          if ("android.support.v7.appcompat" == manifestInfo.`package`) {
            return GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
          }
        }
      }
    }

    return null
  }

  override fun getDependentLibraries(): Collection<Library> {
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

        // all libraries are assumed to have a classes.jar & a non-empty name
        val classesJar = roots.firstOrNull { it.name == SdkConstants.FN_CLASSES_JAR }?.let(VfsUtil::virtualToIoFile)
                         ?: return@forEachLibrary true
        val libraryName = library.name ?: return@forEachLibrary true
        val classJarLocation = PathString(classesJar)

        // For testing purposes we create libraries with a res.apk root (legacy projects don't have those). Recognize them here and
        // create AarLibrary as necessary.
        val resFolderRoot = roots.firstOrNull { it.name == FD_RES }
        val resApkRoot = roots.firstOrNull { it.name == FN_RESOURCE_STATIC_LIBRARY }
        if (resFolderRoot != null || resApkRoot != null) { // aar
          val (resFolder, resApk) = when {
            resApkRoot != null -> virtualToIoFile(resApkRoot).let { Pair(it.resolveSibling(FD_RES), it) }
            resFolderRoot != null -> virtualToIoFile(resFolderRoot).let { Pair(it, it.resolveSibling(FN_RESOURCE_STATIC_LIBRARY)) }
            else -> return@forEachLibrary true
          }

          libraries.add(AarLibrary(
            address = libraryName,
            location = null,
            manifestFile = PathString(File(resFolder.parentFile, FN_ANDROID_MANIFEST_XML)),
            classesJar = classJarLocation,
            dependencyJars = emptyList(),
            resFolder = PathString(resFolder),
            symbolFile = PathString(File(resFolder.parentFile, FN_RESOURCE_TEXT)),
            resApkFile = PathString(resApk)
          ))
        } else { // jar
          libraries.add(JavaLibrary(
            address = libraryName,
            classesJar = classJarLocation
          ))
        }

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

  override fun getInstantRunSupport(): CapabilityStatus {
    return CapabilityNotSupported()
  }
}