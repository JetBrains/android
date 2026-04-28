/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.SourceSetModuleClassFileFinder.CompileRootsScope
import com.android.tools.idea.util.findAndroidModule
import com.android.tools.idea.util.isAndroidModule
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import java.util.Optional
import java.util.regex.Pattern
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * [CompileRoots] of a module, including dependencies. [directories] is the list of paths to directories containing class outputs. [jars]
 * constains a list of the jar outputs in the [CompileRoots] (typically R.jar files).
 */
private data class CompileRoots(val allRoots: List<Path>, val finder: SourceSetModuleClassFileFinder?, val project: Project?) {
  private val RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\\$[^.]+)?$")

  /** Returns true if [className] is an R class name. */
  private fun isResourceClassName(className: String): Boolean = RESOURCE_CLASS_NAME.matcher(className).matches()

  /** Cache to avoid querying the CompileRoots on every query since many of them are repeated. */
  private val cache: Cache<String, Optional<ClassContent>> =
    CacheBuilder.newBuilder().softValues().maximumSize(StudioFlags.GRADLE_CLASS_FINDER_CACHE_LIMIT.get()).build()

  /** List of paths to directories containing class outputs */
  private val directories: List<Path>
    get() = allRoots.filter { Files.isDirectory(it) }

  /** Contains a list of the jar outputs in the [CompileRoots] (typically R.jar files) */
  private val jars: List<Path> by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) { allRoots.filter { it.isRegularFile() && it.extension == SdkConstants.EXT_JAR } }

  /** Finds a class in the directories included in this [CompileRoots]. */
  private fun findClassInDirectoryRoots(fqcn: String): ClassContent? {
    return directories.map { it.resolve(getPathFromFqcn(fqcn)).toFile() }.firstOrNull { it.isFile() }?.let { ClassContent.loadFromFile(it) }
  }

  /** Finds a class in the jars included in this [CompileRoots]. */
  private fun findClassInJarRoots(fqcn: String): ClassContent? {
    val entryPath = getPathFromFqcn(fqcn)

    return jars.firstNotNullOfOrNull { jar ->
      project?.let { finder?.loadClassFileFromJar(it, jar, entryPath) }?.let { ClassContent.fromJarEntryContent(jar.toFile(), it) }
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
    fun createEmpty(finder: SourceSetModuleClassFileFinder) = CompileRoots(listOf(), finder, null)
  }
}

/** Map [CompileRootsScope] to the [ScopeType] dependencies to be included in the search. [ScopeType.MAIN] is always included. */
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
private fun Module.getNonCachedCompileOutputsIncludingDependencies(scope: CompileRootsScope): CompileRoots {
  val scopes = getAllCompileOutputScopes(scope)
  val finder = this.getModuleSystem().createModuleClassFileFinder(scopes) as? SourceSetModuleClassFileFinder
  val allRoots =
    finder
      ?.let {
        this.getAllDependencies(scope.traverseTestDependencies).flatMap {
          finder.getModuleCompileOutputs(it, getAllCompileOutputScopes(scope))
        }
      }
      .orEmpty()

  return CompileRoots(allRoots, finder, project).also {
    Logger.getInstance(SourceSetModuleClassFileFinder::class.java).debug("CompileRoots recalculated $it")
  }
}

/** Returns a set containing the current [Module] and all its direct and transitive dependencies. */
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
private val PRODUCTION_ROOTS_KEY: Key<ParameterizedCachedValue<CompileRoots, Module>> = Key.create("production roots")
/** [ParameterizedCachedValueProvider] to calculate the output roots for a non test module. */
private val PRODUCTION_ROOTS_PROVIDER =
  ParameterizedCachedValueProvider<CompileRoots, Module> { module ->
    val roots = module.getNonCachedCompileOutputsIncludingDependencies(CompileRootsScope.MAIN)
    val trackers = mutableListOf<ModificationTracker>(ProjectSyncModificationTracker.getInstance(module.project))
    roots.finder?.let { trackers.add(it.getModificationTracker(module)) }
    CachedValueProvider.Result.create(roots, *trackers.toTypedArray())
  }

/** Key used to cache the [CompileRoots] for a `androidTest` module. */
private val ANDROID_TEST_ROOTS_KEY: Key<ParameterizedCachedValue<CompileRoots, Module>> = Key.create("androidTest roots")
/** [ParameterizedCachedValueProvider] to calculate the output roots for a test module. */
private val ANDROID_TEST_ROOTS_PROVIDER =
  ParameterizedCachedValueProvider<CompileRoots, Module> { module ->
    val roots = module.getNonCachedCompileOutputsIncludingDependencies(CompileRootsScope.MAIN_AND_ANDROID_TEST)
    val trackers = mutableListOf<ModificationTracker>(ProjectSyncModificationTracker.getInstance(module.project))
    roots.finder?.let { trackers.add(it.getModificationTracker(module)) }
    CachedValueProvider.Result.create(roots, *trackers.toTypedArray())
  }

/** Key used to cache the [CompileRoots] for a `screenshotTest` module. */
private val SCREENSHOT_TEST_ROOTS_KEY: Key<ParameterizedCachedValue<CompileRoots, Module>> = Key.create("screenshotTest roots")
/** [ParameterizedCachedValueProvider] to calculate the output roots for a `screenshotTest` module. */
private val SCREENSHOT_TEST_ROOTS_PROVIDER =
  ParameterizedCachedValueProvider<CompileRoots, Module> { module ->
    val roots = module.getNonCachedCompileOutputsIncludingDependencies(CompileRootsScope.MAIN_AND_SCREENSHOT_TEST)
    val trackers = mutableListOf<ModificationTracker>(ProjectSyncModificationTracker.getInstance(module.project))
    roots.finder?.let { trackers.add(it.getModificationTracker(module)) }
    CachedValueProvider.Result.create(roots, *trackers.toTypedArray())
  }

/** Returns the list of [Path]s to external JAR files referenced by the class loader. */
private fun Module.getCompileOutputs(scope: CompileRootsScope, finder: SourceSetModuleClassFileFinder): CompileRoots {
  if (this.isDisposed) {
    return CompileRoots.createEmpty(finder)
  }

  return when (scope) {
    CompileRootsScope.MAIN ->
      CachedValuesManager.getManager(project)
        .getParameterizedCachedValue(this, PRODUCTION_ROOTS_KEY, PRODUCTION_ROOTS_PROVIDER, false, this)

    CompileRootsScope.MAIN_AND_ANDROID_TEST ->
      CachedValuesManager.getManager(project)
        .getParameterizedCachedValue(this, ANDROID_TEST_ROOTS_KEY, ANDROID_TEST_ROOTS_PROVIDER, false, this)

    CompileRootsScope.MAIN_AND_SCREENSHOT_TEST ->
      CachedValuesManager.getManager(project)
        .getParameterizedCachedValue(this, SCREENSHOT_TEST_ROOTS_KEY, SCREENSHOT_TEST_ROOTS_PROVIDER, false, this)
  }
}

/** A [ClassFileFinder] that finds classes into the compile roots of a project module. */
abstract class SourceSetModuleClassFileFinder protected constructor(protected val module: Module, protected val scope: CompileRootsScope) :
  ClassFileFinder {

  /**
   * Scope to be used while create the [CompileRoots].
   *
   * [traverseTestDependencies] will be true in those cases where the compiler roots should include the test dependencies as part of the
   * outpu.
   */
  enum class CompileRootsScope(val traverseTestDependencies: Boolean) {
    /** Include only main sourceset */
    MAIN(false),
    /** Include main and `androidTest` sourceset */
    MAIN_AND_ANDROID_TEST(true),
    /** Include main and `screenshotTest` sourceset */
    MAIN_AND_SCREENSHOT_TEST(true),
  }

  init {
    validateModule(module)
  }

  abstract fun getModuleCompileOutputs(module: Module, scopes: EnumSet<ScopeType>): List<Path>

  abstract fun getModificationTracker(module: Module): ModificationTracker

  abstract fun validateModule(module: Module)

  abstract fun loadClassFileFromJar(project: Project, jarPath: Path, entryPath: String): ByteArray?

  override fun findClassFile(fqcn: String): ClassContent? {
    return if (module.isAndroidModule()) {
      module.getCompileOutputs(scope, this).findClass(fqcn)
    } else {
      module.findAndroidModule()?.getCompileOutputs(scope, this)?.findClass(fqcn)
    }
  }

  companion object {
    fun createWithoutTests(module: Module): ClassFileFinder =
      module.getModuleSystem().createModuleClassFileFinder(EnumSet.of(ScopeType.MAIN))

    fun createIncludingAndroidTest(module: Module): ClassFileFinder =
      module.getModuleSystem().createModuleClassFileFinder(EnumSet.of(ScopeType.MAIN, ScopeType.ANDROID_TEST))

    fun createIncludingScreenshotTest(module: Module): ClassFileFinder =
      module.getModuleSystem().createModuleClassFileFinder(EnumSet.of(ScopeType.MAIN, ScopeType.SCREENSHOT_TEST))

    private val LOG = Logger.getInstance(SourceSetModuleClassFileFinder::class.java)
  }
}
