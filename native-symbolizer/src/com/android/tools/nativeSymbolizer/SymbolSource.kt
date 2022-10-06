/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toIoFile
import com.android.utils.FileUtils
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File

/**
 * The interface for gathering symbol directories for a given ABI. Each implementation will handle
 * collecting symbol directories from different sources.
 */
interface SymbolSource {
  fun getDirsFor(abi: Abi): Collection<File>
}

/**
 * Combines the results of multiple SymbolSource, allowing other code to only need to handle a
 * single source instance.
 */
class MergeSymbolSource(private val sources:Collection<SymbolSource>): SymbolSource {
  override fun getDirsFor(abi: Abi): Collection<File> {
    // Use a set in case multiple sources return the same path.
    return sources.flatMapTo(mutableSetOf()) { it.getDirsFor(abi) }
  }
}

/** Gets symbol directories from an APK's debug directory. */
class ApkSymbolSource(private val module: Module): SymbolSource {
  override fun getDirsFor(abi: Abi): Collection<File> {
    val apkFacet = ApkFacet.getInstance(module) ?: return emptySet()

    val folders = apkFacet.configuration.getDebugSymbolFolderPaths(listOf(abi))
    return folders.map { File(FileUtils.toSystemDependentPath(it)) }
  }
}

class NdkSymbolSource(private val module: Module): SymbolSource {
  override fun getDirsFor(abi: Abi): Collection<File> {
    val ndkFacet = NdkFacet.getInstance(module) ?: return emptySet()
    val ndkModuleModel = NdkModuleModel.get(module) ?: return emptySet()
    val selectedAbi = ndkFacet.selectedVariantAbi ?: return emptySet()

    return ndkModuleModel.symbolFolders
        .filter { it.key.abi == abi.toString() && it.key.variant == selectedAbi.variant}
        .flatMapTo(mutableSetOf()) { it.value }
  }
}

/** Gets symbol directories from a module's Gradle file. */
class JniSymbolSource(private val module: Module) : SymbolSource {
  override fun getDirsFor(abi: Abi): Collection<File> {
    return module.androidFacet?.let { SourceProviders.getInstance(it) }?.sources?.jniLibsDirectories?.map {
      it.findChild(abi.toString())?.toIoFile()
    }.orEmpty().filterNotNull()
  }
}

/** Allows us to manually add/remove symbol directories programmatically. */
class DynamicSymbolSource: SymbolSource {
  private val dirsByAbi = HashMap<String, HashSet<File>>()

  fun add(cpuArch: String, path:File): DynamicSymbolSource {
    dirsByAbi.computeIfAbsent(cpuArch) { HashSet() }.add(path)
    return this
  }

  override fun getDirsFor(abi: Abi): Collection<File> {
    return dirsByAbi.getOrDefault(abi.cpuArch, emptyList())
  }
}

/** A SymbolSource to collect native symbols from a project. */
class ProjectSymbolSource(project: Project): SymbolSource {
  private val source = MergeSymbolSource(ModuleManager.getInstance(project).modules.flatMap {
    listOf(ApkSymbolSource(it), NdkSymbolSource(it), JniSymbolSource(it))
  })

  override fun getDirsFor(abi: Abi): Collection<File> {
    return source.getDirsFor(abi)
  }
}
