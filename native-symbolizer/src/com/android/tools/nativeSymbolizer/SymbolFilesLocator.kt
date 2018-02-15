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
package com.android.tools.nativeSymbolizer

import com.android.sdklib.devices.Abi
import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.utils.FileUtils
import com.google.common.collect.Sets
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Given a map of possible symbols locations finds symbol files
 * for a (device module + CPU arch) pairs.
 */
class SymbolFilesLocator(private val cpuToSymbolDirs: Map<String, Set<File>>) {

  fun findSymbolFiles(cpuArch: String, module: String): List<File> {
    // TODO (ezemetsov): Add a cache
    // Just look in all dirs from the map and find files with the same
    // basename as a given device module.
    val symDirs = cpuToSymbolDirs.getOrDefault(cpuArch, setOf<File>()).toList()
    val baseModuleName = File(File(module).name).nameWithoutExtension
    val symNameCandidates = arrayListOf(baseModuleName + ".so", baseModuleName + ".dwo")
    val result = mutableListOf<File>()
    for (dir in symDirs) {
      result.addAll(dir.listFiles({ _, name -> symNameCandidates.contains(name) }))
    }
    return result
  }
}

/**
 * Builds a map from CPU architectures to possible directories where native symbols
 * can possibly be found for a given project.
 */
fun getArchToSymDirsMap(project: Project): Map<String, Set<File>> {
  val result: MutableMap<String, MutableSet<File>> = hashMapOf()

  val symbolDirFilter = fun(subdir: File?): Boolean {
    if (subdir == null || !subdir.isDirectory) return false
    val files = subdir.listFiles(
        { f ->
          val extension = f.extension.toLowerCase()
          extension == "so" || extension == "dwo"
        }
    )
    return files.isNotEmpty()
  }

  // Go through all ABI+module combinations and ask getModuleSymbolsDirs to find
  // symbol directories for each combination. Then check that directories exist and
  // contain "so/dwo" files.
  val allSupportedAbis = listOf(Abi.X86, Abi.X86_64, Abi.ARM64_V8A, Abi.ARMEABI, Abi.ARMEABI_V7A)
  for (abi in allSupportedAbis) {
    for (module in ModuleManager.getInstance(project).modules) {
      val symDirs = getModuleSymbolsDirs(module, abi).filter(symbolDirFilter)
      val existingDirs = result.computeIfAbsent(abi.cpuArch, { mutableSetOf() })
      existingDirs.addAll(symDirs)
    }
  }

  return result
}

/**
 * Gathers all possible location of native symbols for a given module+abi pair.
 * Symbol files can be found in 3 places
 *  1. If it's APK debugging case ApkFacet just tells us symbol dirs
 *  2. If a given module has parts built with NDK. return all dirs where native artifacts are located.
 *  (Usually such dirs look like app/build/intermediates/cmake/debug/obj/x86/ )
 *  3. Modules can also have random JNI libs inside them as resources.
 *  Return all dirs where they are.
 */
private fun getModuleSymbolsDirs(module: Module, abi: Abi): Collection<File> {
  val symDirs = Sets.newLinkedHashSet<File>()
  val abiName = abi.toString()

  // 1. APK debugging symbols dirs
  val apkFacet = ApkFacet.getInstance(module)
  if (apkFacet != null) {
    val dirs = apkFacet.configuration.getDebugSymbolFolderPaths(listOf(abi))
      .map({ File(FileUtils.toSystemDependentPath(it)) })
    symDirs.addAll(dirs)
  }

  // 2. libs built in studio by NDK and gradel
  val ndkModuleModel = NdkModuleModel.get(module)
  if (ndkModuleModel != null) {
    val dirs = ndkModuleModel.selectedVariant.artifacts
      .filter { it.abi == abiName }
      .map { it.outputFile.parentFile }
    symDirs.addAll(dirs)
  }

  // 3. JNI libs as resources
  val androidModel = AndroidModuleModel.get(module)
  if (androidModel != null) {
    val jniDirs = androidModel.activeSourceProviders
      .flatMap { it.jniLibsDirectories }
      .map { jniDir -> File(jniDir, abiName) }
    symDirs.addAll(jniDirs)

    val nativeLibraries = androidModel.selectedVariant.mainArtifact.nativeLibraries.orEmpty()
    val nativeLibDirs = nativeLibraries.filter { it.abi == abiName }.flatMap { it.debuggableLibraryFolders }
    symDirs.addAll(nativeLibDirs)
  }

  return symDirs
}


