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
package com.android.tools.idea.projectsystem.gradle

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.ProjectBuildTracker
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.getPathFromFqcn
import com.android.tools.idea.rendering.classloading.loaders.JarManager
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import com.intellij.util.io.isFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.regex.Pattern
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * [CompileRoots] of a module, including dependencies. [directories] is the list of paths to
 * directories containing class outputs. [jars] constains a list of the jar outputs in the
 * [CompileRoots] (typically R.jar files).
 */
private data class CompileRoots(val allRoots: List<Path>, val jarManager: JarManager?) {
  private val RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$")

  /** Returns true if [className] is an R class name. */
  private fun isResourceClassName(className: String): Boolean =
    RESOURCE_CLASS_NAME.matcher(className).matches()

  /** Cache to avoid querying the CompileRoots on every query since many of them are repeated. */
  private val cache: Cache<String, Optional<ClassContent>> =
    CacheBuilder.newBuilder()
      .softValues()
      .maximumSize(StudioFlags.GRADLE_CLASS_FINDER_CACHE_LIMIT.get())
      .build()

  /** List of paths to directories containing class outputs */
  private val directories: List<Path>
    get() = allRoots
      .filter { Files.isDirectory(it) }

  /** Contains a list of the jar outputs in the [CompileRoots] (typically R.jar files) */
  private val jars: List<Path> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    allRoots.filter { it.isRegularFile() && it.extension == SdkConstants.EXT_JAR }
  }

  /** Finds a class in the directories included in this [CompileRoots]. */
  private fun findClassInDirectoryRoots(fqcn: String): ClassContent? {
    return directories
      .map { it.resolve(getPathFromFqcn(fqcn)).toFile() }
      .firstOrNull { it.isFile() }
      ?.let { ClassContent.loadFromFile(it) }
  }

  /** Finds a class in the jars included in this [CompileRoots]. */
  private fun findClassInJarRoots(fqcn: String): ClassContent? {
    val entryPath = getPathFromFqcn(fqcn)

    return jars.firstNotNullOfOrNull {
      val bytes = jarManager?.loadFileFromJar(it, entryPath) ?: return@firstNotNullOfOrNull null
      ClassContent.fromJarEntryContent(it.toFile(), bytes)
    }
  }

  fun findClass(fqcn: String): ClassContent? =
    cache
      .get(fqcn) {
        Optional.ofNullable(
          if (isResourceClassName(fqcn)) {
            findClassInJarRoots(fqcn) ?: findClassInDirectoryRoots(fqcn)
          } else {
            findClassInDirectoryRoots(fqcn) ?: findClassInJarRoots(fqcn)
          }
        )
      }
      .orElse(null)

  companion object {
    val EMPTY = CompileRoots(listOf(), null)
  }
}

/**
 * Calculates the output roots for the module, including all the dependencies. If
 * [includeAndroidTests] is true, the output roots for test dependencies will be included.
 */
private fun Module.getNonCachedCompileOutputsIncludingDependencies(
  includeAndroidTests: Boolean
): CompileRoots =
    CompileRoots(
      (listOf(this) + ModuleRootManager.getInstance(this).getDependencies(includeAndroidTests))
        .flatMap {
          GradleClassFinderUtil.getModuleCompileOutputs(it, includeAndroidTests).toList()
        }
        .map { it.toPath() }
        .toList(),
      JarManager.getInstance(project),
    )
      .also {
        Logger.getInstance(GradleClassFileFinder::class.java).debug("CompileRoots recalculated $it")
      }


/** Key used to cache the [CompileRoots] for a non test module. */
private val PRODUCTION_ROOTS_KEY: Key<ParameterizedCachedValue<CompileRoots, Module>> =
  Key.create("production roots")
/** [ParameterizedCachedValueProvider] to calculate the output roots for a non test module. */
private val PRODUCTION_ROOTS_PROVIDER =
  ParameterizedCachedValueProvider<CompileRoots, Module> { module ->
    CachedValueProvider.Result.create(
      module.getNonCachedCompileOutputsIncludingDependencies(false),
      ProjectSyncModificationTracker.getInstance(module.project),
      ProjectBuildTracker.getInstance(module.project)
    )
  }

/** Key used to cache the [CompileRoots] for a test module. */
private val TEST_ROOTS_KEY: Key<ParameterizedCachedValue<CompileRoots, Module>> =
  Key.create("test roots")
/** [ParameterizedCachedValueProvider] to calculated the output roots for a test module. */
private val TEST_ROOTS_PROVIDER =
  ParameterizedCachedValueProvider<CompileRoots, Module> { module ->
    CachedValueProvider.Result.create(
      module.getNonCachedCompileOutputsIncludingDependencies(true),
      ProjectSyncModificationTracker.getInstance(module.project),
      ProjectBuildTracker.getInstance(module.project)
    )
  }

/** Returns the list of [Path]s to external JAR files referenced by the class loader. */
private fun Module.getCompileOutputs(includeAndroidTests: Boolean): CompileRoots {
  if (this.isDisposed) {
    return CompileRoots.EMPTY
  }

  return if (includeAndroidTests) {
    CachedValuesManager.getManager(project)
      .getParameterizedCachedValue(this, TEST_ROOTS_KEY, TEST_ROOTS_PROVIDER, false, this)
  } else {
    CachedValuesManager.getManager(project)
      .getParameterizedCachedValue(
        this,
        PRODUCTION_ROOTS_KEY,
        PRODUCTION_ROOTS_PROVIDER,
        false,
        this
      )
  }
}

/** A [ClassFileFinder] that finds classes into the compile roots of a Gradle project. */
class GradleClassFileFinder
private constructor(private val module: Module, private val includeAndroidTests: Boolean) :
  ClassFileFinder {

  override fun findClassFile(fqcn: String): ClassContent? {
    return module.getCompileOutputs(includeAndroidTests).findClass(fqcn)
  }

  companion object {
    @JvmOverloads
    fun create(module: Module, includeAndroidTests: Boolean = false) =
      GradleClassFileFinder(module, includeAndroidTests)
  }
}
