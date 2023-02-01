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
package org.jetbrains.android.uipreview

import com.android.tools.idea.LogAnonymizerUtil.anonymize
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.rendering.classloading.ClassTransform
import com.android.tools.idea.rendering.classloading.combine
import com.android.utils.reflection.qualifiedName
import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService
import org.jetbrains.android.uipreview.StudioModuleClassLoader.NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS
import org.jetbrains.android.uipreview.StudioModuleClassLoader.PROJECT_DEFAULT_TRANSFORMS
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.groovy.util.removeUserData
import java.lang.ref.SoftReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

private val DUMMY_HOLDER = Any()

private fun throwIfNotUnitTest(e: Exception) = if (!ApplicationManager.getApplication().isUnitTestMode) {
  throw e
} else {
  Logger.getInstance(ModuleClassLoaderProjectHelperService::class.java).info(
    "ModuleClassLoaderProjectHelperService is disabled for unit testing since there is no ProjectSystemBuildManager")
}

/**
 * This helper service listens for builds and cleans the module cache after it finishes.
 */
@Service
private class ModuleClassLoaderProjectHelperService(val project: Project): ProjectSystemBuildManager.BuildListener, Disposable {
  init {
    try {
      ProjectSystemService.getInstance(project).projectSystem.getBuildManager().addBuildListener(this, this)
    }
    catch (e: IllegalStateException) {
      throwIfNotUnitTest(e)
    }
    catch (e: UnsupportedOperationException) {
      throwIfNotUnitTest(e)
    }
  }

  override fun beforeBuildCompleted(result: ProjectSystemBuildManager.BuildResult) {
    if (result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS
        && (result.mode == ProjectSystemBuildManager.BuildMode.COMPILE || result.mode == ProjectSystemBuildManager.BuildMode.ASSEMBLE)) {
      ModuleManager.getInstance(project).modules.forEach { StudioModuleClassLoaderManager.get().clearCache(it) }
    }
  }

  override fun dispose() {}
}

/**
 * This is a wrapper around a class preloading [CompletableFuture] that allows for the proper disposal of the resources used.
 */
class Preloader(
  moduleClassLoader: StudioModuleClassLoader,
  classesToPreload: Collection<String> = emptyList()) {
  private val classLoader = SoftReference(moduleClassLoader)
  private var isActive = AtomicBoolean(true)

  init {
    if (classesToPreload.isNotEmpty()) {
      preload(moduleClassLoader, {
        isActive.get() && Disposer.isDisposed(classLoader.get() ?: return@preload false)
       }, classesToPreload, getAppExecutorService())
    }
  }

  /**
   * Cancels the on-going preloading.
   */
  fun cancel() {
    isActive.set(false)
  }

  fun getClassLoader(): StudioModuleClassLoader? {
    cancel() // Stop preloading since we are going to use the class loader
    return classLoader.get()
  }

  /**
   * Checks if this [Preloader] loads classes for [cl] [ModuleClassLoader]. This allows for safe check without the need for share the
   * actual [classLoader] and prevent its use.
   */
  fun isLoadingFor(cl: StudioModuleClassLoader) = classLoader.get() == cl

  fun isForCompatible(parent: ClassLoader?, projectTransformations: ClassTransform, nonProjectTransformations: ClassTransform) =
    classLoader.get()?.isCompatible(parent, projectTransformations, nonProjectTransformations) == true

  /**
   * Returns the number of currently loaded classes for the underlying [StudioModuleClassLoader]. Intended to be used for debugging and
   * diagnostics.
   */
  fun getLoadedCount(): Int = classLoader.get()?.let { it.nonProjectLoadedClasses.size + it.projectLoadedClasses.size } ?: 0
}

private val PRELOADER: Key<Preloader> = Key.create(::PRELOADER.qualifiedName)
val HATCHERY: Key<ModuleClassLoaderHatchery> = Key.create(::HATCHERY.qualifiedName)

private fun calculateTransformationsUniqueId(projectClassesTransformationProvider: ClassTransform,
                                             nonProjectClassesTransformationProvider: ClassTransform): String? {
  return Hashing.goodFastHash(64).newHasher()
    .putString(projectClassesTransformationProvider.id, Charsets.UTF_8)
    .putString(nonProjectClassesTransformationProvider.id, Charsets.UTF_8)
    .hash()
    .toString()
}

fun StudioModuleClassLoader.areTransformationsUpToDate(projectClassesTransformationProvider: ClassTransform,
                                                                                                          nonProjectClassesTransformationProvider: ClassTransform): Boolean {
  return (calculateTransformationsUniqueId(this.projectClassesTransform, this.nonProjectClassesTransform)
    == calculateTransformationsUniqueId(projectClassesTransformationProvider, nonProjectClassesTransformationProvider))
}

/**
 * Checks if the [StudioModuleClassLoader] has the same transformations and parent [ClassLoader] making it compatible but not necessarily
 * up-to-date because it does not check the state of user project files. Compatibility means that the [StudioModuleClassLoader] can be used if it
 * did not load any classes from the user source code. This allows for pre-loading the classes from dependencies (which are usually more
 * stable than user code) and speeding up the preview update when user changes the source code (but not dependencies).
 */
fun StudioModuleClassLoader.isCompatible(
  parent: ClassLoader?,
  projectTransformations: ClassTransform,
  nonProjectTransformations: ClassTransform) = when {
  !this.isCompatibleParentClassLoader(parent) -> {
    StudioModuleClassLoaderManager.LOG.debug("Parent has changed, discarding ModuleClassLoader")
    false
  }
  !this.areTransformationsUpToDate(projectTransformations, nonProjectTransformations) -> {
    StudioModuleClassLoaderManager.LOG.debug("Transformations have changed, discarding ModuleClassLoader")
    false
  }
  !this.areDependenciesUpToDate() -> {
    StudioModuleClassLoaderManager.LOG.debug("Files have changed, discarding ModuleClassLoader")
    false
  }
  else -> {
    StudioModuleClassLoaderManager.LOG.debug("ModuleClassLoader is up to date")
    true
  }
}

private fun <T> UserDataHolder.getOrCreate(key: Key<T>, factory: () -> T): T {
  getUserData(key)?.let {
    return it
  }
  return factory().also {
    putUserData(key, it)
  }
}

private fun UserDataHolder.getOrCreateHatchery() = getOrCreate(HATCHERY) { ModuleClassLoaderHatchery() }

/**
 * A [ClassLoader] for the [Module] dependencies.
 */
class StudioModuleClassLoaderManager : ModuleClassLoaderManager {
  // MutableSet is backed by the WeakHashMap in prod so we do not retain the holders
  private val holders: MutableMap<StudioModuleClassLoader, MutableSet<Any>> = IdentityHashMap()
  private var captureDiagnostics = false

  @TestOnly
  fun hasAllocatedSharedClassLoaders() = holders.isNotEmpty()

  /**
   * Returns a project class loader to use for rendering. May cache instances across render sessions.
   */
  @Synchronized
  override fun getShared(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext, holder: Any,
                         additionalProjectTransformation: ClassTransform,
                         additionalNonProjectTransformation: ClassTransform,
                         onNewModuleClassLoader: Runnable): StudioModuleClassLoader {
    val module: Module = moduleRenderContext.module
    var moduleClassLoader = module.getUserData(PRELOADER)?.getClassLoader()
    val combinedProjectTransformations: ClassTransform by lazy {
      combine(PROJECT_DEFAULT_TRANSFORMS, additionalProjectTransformation)
    }
    val combinedNonProjectTransformations: ClassTransform by lazy {
      combine(NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS, additionalNonProjectTransformation)
    }

    var oldClassLoader: StudioModuleClassLoader? = null
    if (moduleClassLoader != null) {
      val invalidate =
        moduleClassLoader.isDisposed ||
        !moduleClassLoader.isCompatible(parent, combinedProjectTransformations, combinedNonProjectTransformations) ||
        !moduleClassLoader.isUserCodeUpToDate

      if (invalidate) {
        oldClassLoader = moduleClassLoader
        moduleClassLoader = null
      }
    }

    if (moduleClassLoader == null) {
      // Make sure the helper service is initialized
      moduleRenderContext.module.project.getService(ModuleClassLoaderProjectHelperService::class.java)
      LOG.debug { "Loading new class loader for module ${anonymize(module)}" }
      val preloadedClassLoader: StudioModuleClassLoader? =
        moduleRenderContext.module.getOrCreateHatchery().requestClassLoader(
          parent, combinedProjectTransformations, combinedNonProjectTransformations)
      moduleClassLoader = preloadedClassLoader ?: StudioModuleClassLoader(parent,
                                                                          moduleRenderContext,
                                                                          combinedProjectTransformations,
                                                                          combinedNonProjectTransformations,
                                                                          createDiagnostics())
      module.putUserData(PRELOADER, Preloader(moduleClassLoader))
      onNewModuleClassLoader.run()
    }

    oldClassLoader?.let { release(it, DUMMY_HOLDER) }
    holders.computeIfAbsent(moduleClassLoader) { createHoldersSet() }.apply { add(holder) }
    return moduleClassLoader
  }

  @Synchronized
  override fun getShared(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext, holder: Any) =
    getShared(parent, moduleRenderContext, holder, ClassTransform.identity, ClassTransform.identity) { }

  /**
   * Return a [StudioModuleClassLoader] for a [Module] to be used for rendering. Similar to [getShared] but guarantees that the returned
   * [StudioModuleClassLoader] is not shared and the caller has full ownership of it.
   */
  @Synchronized
  override fun getPrivate(parent: ClassLoader?,
                          moduleRenderContext: ModuleRenderContext,
                          holder: Any,
                          additionalProjectTransformation: ClassTransform,
                          additionalNonProjectTransformation: ClassTransform): StudioModuleClassLoader {
    // Make sure the helper service is initialized
    moduleRenderContext.module.project.getService(ModuleClassLoaderProjectHelperService::class.java)

    val combinedProjectTransformations = combine(PROJECT_DEFAULT_TRANSFORMS, additionalProjectTransformation)
    val combinedNonProjectTransformations = combine(NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS, additionalNonProjectTransformation)
    val preloadedClassLoader: StudioModuleClassLoader? =
      moduleRenderContext.module.getOrCreateHatchery().requestClassLoader(
        parent, combinedProjectTransformations, combinedNonProjectTransformations)
    return (preloadedClassLoader ?: StudioModuleClassLoader(parent, moduleRenderContext,
                                                            combinedProjectTransformations,
                                                            combinedNonProjectTransformations,
                                                            createDiagnostics())).apply {
      holders[this] = createHoldersSet().apply { add(holder) }
    }
  }

  @Synchronized
  override fun getPrivate(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext, holder: Any) =
    getPrivate(parent, moduleRenderContext, holder, ClassTransform.identity, ClassTransform.identity)

  @VisibleForTesting
  fun createCopy(mcl: StudioModuleClassLoader): StudioModuleClassLoader? = mcl.copy(createDiagnostics())

  private fun createDiagnostics() = if (captureDiagnostics) ModuleClassLoadedDiagnosticsImpl() else NopModuleClassLoadedDiagnostics

  /**
   * Creates a [MutableMap] to be used as a storage of [ModuleClassLoader] holders. We would like the implementation to be different in
   * prod and in tests:
   *
   * In Prod, it should be a Set of WEAK references. So that in case we do not release the holder (due to some unexpected flow) it is not
   * retained by the [StudioModuleClassLoaderManager]
   *
   * In Tests, we would like it to be a Set of STRONG references. So that any unreleased references got caught by the LeakHunter.
   */
  private fun createHoldersSet(): MutableSet<Any> =
    if (ApplicationManager.getApplication().isUnitTestMode) {
      mutableSetOf()
    }
    else {
      Collections.newSetFromMap(WeakHashMap())
    }

  @Synchronized
  fun clearCache(module: Module) {
    holders.keys.toList().filter { it.module?.getHolderModule() == module.getHolderModule() }.forEach {
      holders.remove(it)
    }
    setOf(module.getHolderModule(), module).forEach { mdl ->
      mdl.removeUserData(PRELOADER)?.getClassLoader()?.let { Disposer.dispose(it) }
      mdl.getUserData(HATCHERY)?.destroy()
    }
  }

  @Synchronized
  private fun unHold(moduleClassLoader: ModuleClassLoader, holder: Any) {
    holders[moduleClassLoader]?.let {
      it.remove(holder)
      if (it.isEmpty()) {
        holders.remove(moduleClassLoader)
      }
    }
  }

  @Synchronized
  private fun stopManagingIfNotHeld(moduleClassLoader: StudioModuleClassLoader): Boolean {
    if (holders[moduleClassLoader]?.isNotEmpty() == true) {
      return false
    }
    // If that was a shared ModuleClassLoader that is no longer used, we have to destroy the old one to free the resources, but we also
    // recreate a new one for faster load next time
    moduleClassLoader.module?.let { module ->
      if (module.isDisposed) {
        return@let
      }
      if (module.getUserData(PRELOADER)?.isLoadingFor(moduleClassLoader) != true) {
        if (holders.isNotEmpty()) {
          module.getOrCreateHatchery().incubateIfNeeded(moduleClassLoader, ::createCopy)
        }
      }
      else {
        module.removeUserData(PRELOADER)?.cancel()
        val newClassLoader = createCopy(moduleClassLoader) ?: return@let
        // We first load dependencies classes and then project classes since the latter reference the former and not vice versa
        val classesToLoad = moduleClassLoader.nonProjectLoadedClasses + moduleClassLoader.projectLoadedClasses
        module.putUserData(PRELOADER, Preloader(newClassLoader, classesToLoad))
      }
      if (holders.isEmpty()) { // If there are no more users of ModuleClassLoader destroy the hatchery to free the resources
        module.getUserData(HATCHERY)?.destroy()
      }
    }
    return true
  }

  /**
   * Inform [StudioModuleClassLoaderManager] that [ModuleClassLoader] is not used anymore and therefore can be
   * disposed if no longer managed.
   */
  override fun release(moduleClassLoader: ModuleClassLoader, holder: Any) {
    if (moduleClassLoader is StudioModuleClassLoader) {
      unHold(moduleClassLoader, holder)
      if (stopManagingIfNotHeld(moduleClassLoader)) {
        Disposer.dispose(moduleClassLoader)
      }
    }
  }


  /**
   * If set to true, any class loaders instantiated after this call will record diagnostics about the load
   * time and load counts.
   */
  @TestOnly
  @Synchronized
  fun setCaptureClassLoadingDiagnostics(enabled: Boolean) {
    captureDiagnostics = enabled
  }

  companion object {
    @JvmStatic
    val LOG = Logger.getInstance(StudioModuleClassLoaderManager::class.java)

    @JvmStatic
    fun get(): StudioModuleClassLoaderManager =
      ApplicationManager.getApplication().getService(StudioModuleClassLoaderManager::class.java)
  }
}