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
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.getPathFromFqcn
import com.android.tools.idea.rendering.classloading.loaders.JarManager
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.base.facet.externalProjectId
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import java.util.Optional
import java.util.regex.Pattern
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * Scope to be used while create the [CompileRoots].
 *
 * [traverseTestDependencies] will be true in those cases where the compiler roots should include the
 * test dependencies as part of the outpu.
 */
private enum class CompileRootsScope(val traverseTestDependencies: Boolean) {
  /** Include only main sourceset */
  MAIN(false),
  /** Include main and `androidTest` sourceset */
  MAIN_AND_ANDROID_TEST(true),
  /** Include main and `screenshotTest` sourceset */
  MAIN_AND_SCREENSHOT_TEST(true),
}

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
 * Map [CompileRootsScope] to the [ScopeType] dependencies to be included in the search. [ScopeType.MAIN] is always included.
 */
private fun getAllCompileOutputScopes(scope: CompileRootsScope): EnumSet<ScopeType> =
  when (scope) {
    CompileRootsScope.MAIN -> EnumSet.of(ScopeType.MAIN)
    CompileRootsScope.MAIN_AND_ANDROID_TEST -> EnumSet.of(ScopeType.MAIN, ScopeType.ANDROID_TEST)
    CompileRootsScope.MAIN_AND_SCREENSHOT_TEST -> EnumSet.of(ScopeType.MAIN, ScopeType.SCREENSHOT_TEST)
  }

/**
 * Calculates the output roots for the module, including all the dependencies for the given [scope]. The resulting [CompileRoots] will
 * contain the paths to the given sourcesets.
 */
private fun Module.getNonCachedCompileOutputsIncludingDependencies(
  scope: CompileRootsScope
): CompileRoots =
    CompileRoots(
      (this.getAllDependencies(scope.traverseTestDependencies))
        .flatMap {
          GradleClassFinderUtil.getModuleCompileOutputs(it, getAllCompileOutputScopes(scope)).toList()
        }
        .map { it.toPath() }
        .toList(),
      JarManager.getInstance(project),
    )
      .also {
        Logger.getInstance(GradleClassFileFinder::class.java).debug("CompileRoots recalculated $it")
      }

/**
 * Returns a set containing the current [Module] and all its direct and transitive dependencies.
 */
fun Module.getAllDependencies(includeAndroidTests: Boolean): Set<Module> {
  val dependencies = mutableSetOf(this)
  val queue = ArrayDeque<Module>()
  queue.add(this)

  while (queue.isNotEmpty()) {
    ModuleRootManager.getInstance(queue.removeFirst()).getDependencies(includeAndroidTests).forEach {
      if (dependencies.add(it)) {
        queue.add(it)
      }
    }
  }

  return dependencies
}


/** Key used to cache the [CompileRoots] for a non test module. */
private val PRODUCTION_ROOTS_KEY: Key<ParameterizedCachedValue<CompileRoots, Module>> =
  Key.create("production roots")
/** [ParameterizedCachedValueProvider] to calculate the output roots for a non test module. */
private val PRODUCTION_ROOTS_PROVIDER =
  ParameterizedCachedValueProvider<CompileRoots, Module> { module ->
    CachedValueProvider.Result.create(
      module.getNonCachedCompileOutputsIncludingDependencies(CompileRootsScope.MAIN),
      ProjectSyncModificationTracker.getInstance(module.project),
      ProjectBuildTracker.getInstance(module.project)
    )
  }

/** Key used to cache the [CompileRoots] for a `androidTest` module. */
private val ANDROID_TEST_ROOTS_KEY: Key<ParameterizedCachedValue<CompileRoots, Module>> =
  Key.create("androidTest roots")
/** [ParameterizedCachedValueProvider] to calculate the output roots for a test module. */
private val ANDROID_TEST_ROOTS_PROVIDER =
  ParameterizedCachedValueProvider<CompileRoots, Module> { module ->
    CachedValueProvider.Result.create(
      module.getNonCachedCompileOutputsIncludingDependencies(CompileRootsScope.MAIN_AND_ANDROID_TEST),
      ProjectSyncModificationTracker.getInstance(module.project),
      ProjectBuildTracker.getInstance(module.project)
    )
  }

/** Key used to cache the [CompileRoots] for a `screenshotTest` module. */
private val SCREENSHOT_TEST_ROOTS_KEY: Key<ParameterizedCachedValue<CompileRoots, Module>> =
  Key.create("screenshotTest roots")
/** [ParameterizedCachedValueProvider] to calculate the output roots for a `screenshotTest` module. */
private val SCREENSHOT_TEST_ROOTS_PROVIDER =
  ParameterizedCachedValueProvider<CompileRoots, Module> { module ->
    CachedValueProvider.Result.create(
      module.getNonCachedCompileOutputsIncludingDependencies(CompileRootsScope.MAIN_AND_SCREENSHOT_TEST),
      ProjectSyncModificationTracker.getInstance(module.project),
      ProjectBuildTracker.getInstance(module.project)
    )
  }

/** Returns the list of [Path]s to external JAR files referenced by the class loader. */
private fun Module.getCompileOutputs(scope: CompileRootsScope): CompileRoots {
  if (this.isDisposed) {
    return CompileRoots.EMPTY
  }

  return when (scope) {
    CompileRootsScope.MAIN -> CachedValuesManager.getManager(project)
      .getParameterizedCachedValue(
        this,
        PRODUCTION_ROOTS_KEY,
        PRODUCTION_ROOTS_PROVIDER,
        false,
        this
      )

    CompileRootsScope.MAIN_AND_ANDROID_TEST -> CachedValuesManager.getManager(project)
      .getParameterizedCachedValue(this, ANDROID_TEST_ROOTS_KEY, ANDROID_TEST_ROOTS_PROVIDER, false, this)

    CompileRootsScope.MAIN_AND_SCREENSHOT_TEST -> CachedValuesManager.getManager(project)
      .getParameterizedCachedValue(this, SCREENSHOT_TEST_ROOTS_KEY, SCREENSHOT_TEST_ROOTS_PROVIDER, false, this)
  }
}

/** A [ClassFileFinder] that finds classes into the compile roots of a Gradle project. */
class GradleClassFileFinder
private constructor(private val module: Module, private val scope: CompileRootsScope) :
  ClassFileFinder {

  override fun findClassFile(fqcn: String): ClassContent? {
    return module.getCompileOutputs(scope).findClass(fqcn) ?: module.findAndroidModule()?.getCompileOutputs(scope)?.findClass(fqcn)
  }

  companion object {
    /**
     * Create a [GradleClassFileFinder] that includes dependencies of the given [module] excluding any tests.
     */
    fun createWithoutTests(module: Module) =
      GradleClassFileFinder(module, CompileRootsScope.MAIN)

    /**
     * Create a [GradleClassFileFinder] that includes dependencies of the given [module] including `androidTest` tests.
     */
    fun createIncludingAndroidTest(module: Module) =
      GradleClassFileFinder(module, CompileRootsScope.MAIN_AND_ANDROID_TEST)

    /**
     * Create a [GradleClassFileFinder] that includes dependencies of the given [module] including `screenshotTest` tests.
     */
    fun createIncludingScreenshotTest(module: Module) =
      GradleClassFileFinder(module, CompileRootsScope.MAIN_AND_SCREENSHOT_TEST)
  }
}

//todo: move this into some shared place
internal fun Module.findAndroidModule(): Module? {
  return project.modules.filter { it.externalProjectId == this.externalProjectId }
    .firstNotNullOfOrNull { AndroidFacet.getInstance(it) }?.module
}
