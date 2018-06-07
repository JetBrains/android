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

/**
 * Helper functions for functionality that should be easy to do with the universal project model, but for now needs to be implemented using
 * whatever we have.
 *
 * TODO: remove all of this once we have the project model.
 */
@file:JvmName("AndroidProjectModelUtils")

package com.android.tools.idea

import com.android.SdkConstants.*
import com.android.ide.common.util.PathString
import com.android.projectmodel.AarLibrary
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.projectsystem.FilenameConstants
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

/**
 * Returns information about all [AarLibrary] dependencies present in the project, indexed by [AarLibrary.address] which is unique within
 * a project.
 */
fun findAllAarsLibraries(project: Project): Map<String, AarLibrary> {
  return ModuleManager.getInstance(project)
    .modules
    .asSequence()
    .map(::findAarDependenciesInfo)
    .fold(HashMap()) { inProject, inModule ->
      inProject.putAll(inModule)
      inProject
    }
}

/**
 * Returns information about all [AarLibrary] dependencies of a given module, indexed by [AarLibrary.address] which is unique within
 * a project.
 */
fun findAarDependenciesInfo(module: Module): Map<String, AarLibrary> {
  val result = mutableMapOf<String, AarLibrary>()
  val facet = AndroidFacet.getInstance(module) ?: return result
  val gradleModel = AndroidModuleModel.get(facet)
  val resourceRepositoryManager = ResourceRepositoryManager.getOrCreateInstance(module) ?: return result

  when {
    gradleModel != null -> {
      // We have the Gradle model, we can get everything from there.
      for (library in gradleModel.selectedMainCompileLevel2Dependencies.androidLibraries) {
        result[library.artifactAddress] = AarLibrary(
          address = library.artifactAddress,
          location = PathString(library.artifact),
          manifestFile = PathString(library.manifest),
          classesJar = PathString(library.jarFile),
          dependencyJars = library.localJars.map(::PathString),
          resFolder = PathString(library.resFolder),
          symbolFile = PathString(library.symbolFile),
          resApkFile = library.resStaticLibrary?.let(::PathString)
        )
      }
    }

    facet.requiresAndroidModel() && facet.configuration.model != null -> {
      // It's not Gradle so we'll have to rely on the jars returned from ClassJarProvider.
      val oldAndroidModel = facet.configuration.model!!
      for (classesJar in oldAndroidModel.classJarProvider.getModuleExternalLibraries(module)) {

        @Suppress("DEPRECATION") // This is the place were we actually have to go looking for the res folder.
        val resFolder = findResFolder(classesJar) ?: continue

        // Unfortunately we also need to get the library name from somewhere.
        val libraryName = resourceRepositoryManager.findRepositoryFor(resFolder.parentFile)?.libraryName ?: continue

        result[libraryName] = AarLibrary(
          address = libraryName,
          location = null,
          manifestFile = PathString(File(resFolder.parentFile, FN_ANDROID_MANIFEST_XML)),
          classesJar = PathString(classesJar),
          dependencyJars = emptySet(),
          resFolder = PathString(resFolder),
          symbolFile = PathString(File(resFolder.parentFile, FN_RESOURCE_TEXT)),
          resApkFile = null
        )
      }
    }

    else -> {
      // We need to get everything from the IntelliJ module structure.
      ModuleRootManager.getInstance(module)
        .orderEntries()
        .librariesOnly()
        .recursively()
        .forEachLibrary { library ->
          val roots = library.getFiles(OrderRootType.CLASSES)
          val classesJar = roots.firstOrNull { it.name == FN_CLASSES_JAR }?.let(VfsUtil::virtualToIoFile) ?: return@forEachLibrary true

          @Suppress("DEPRECATION") // This is the place were we actually have to go looking for the res folder.
          val resFolder = roots.firstOrNull { it.name == FD_RES }?.let(VfsUtil::virtualToIoFile)
                          ?: findResFolder(classesJar)
                          ?: return@forEachLibrary true

          val libraryName = library.name ?: return@forEachLibrary true
          result[libraryName] = AarLibrary(
            address = libraryName,
            location = null,
            manifestFile = PathString(File(resFolder.parentFile, FN_ANDROID_MANIFEST_XML)),
            classesJar = PathString(classesJar),
            dependencyJars = emptySet(),
            resFolder = PathString(resFolder),
            symbolFile = PathString(File(resFolder.parentFile, FN_RESOURCE_TEXT)),
            // This is for our testing purposes, legacy projects don't have res.apk files.
            resApkFile = PathString(resFolder.resolveSibling(FN_RESOURCE_STATIC_LIBRARY))
          )

          true // continue processing.
        }
    }
  }

  return result
}

/**
 * Tries to find the resources folder corresponding to a given `classes.jar` file extracted from an AAR.
 *
 * TODO: make it private and part of building the model for legacy projects where guessing is the best we can do.
 */
@Deprecated("Use AndroidProjectModelUtils.findAarDependencies instead of processing jar files and looking for resources.")
fun findResFolder(jarFile: File): File? {
  // We need to figure out the layout of the resources relative to the jar file. This changed over time, so we check for different
  // layouts until we find one we recognize.
  var resourcesDirectory: File? = null

  var aarDir = jarFile.parentFile
  if (aarDir.path.endsWith(DOT_AAR) || aarDir.path.contains(FilenameConstants.EXPLODED_AAR)) {
    if (aarDir.path.contains(FilenameConstants.EXPLODED_AAR)) {
      if (aarDir.path.endsWith(LIBS_FOLDER)) {
        // Some libraries recently started packaging jars inside a sub libs folder inside jars
        aarDir = aarDir.parentFile
      }
      // Gradle plugin version 1.2.x and later has classes in aar-dir/jars/
      if (aarDir.path.endsWith(FD_JARS)) {
        aarDir = aarDir.parentFile
      }
    }
    val path = aarDir.path
    if (path.endsWith(DOT_AAR) || path.contains(FilenameConstants.EXPLODED_AAR)) {
      resourcesDirectory = aarDir
    }
  }

  if (resourcesDirectory == null) {
    // Build cache? We need to compute the package name in a slightly different way.
    val parentFile = aarDir.parentFile
    if (parentFile != null) {
      val manifest = File(parentFile, ANDROID_MANIFEST_XML)
      if (manifest.exists()) {
        resourcesDirectory = parentFile
      }
    }
    if (resourcesDirectory == null) {
      return null
    }
  }
  return resourcesDirectory
}
