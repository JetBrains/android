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
import com.android.tools.idea.rendering.classloading.ClassTransform
import com.android.tools.idea.rendering.classloading.combine
import com.android.utils.reflection.qualifiedName
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
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService
import org.jetbrains.android.uipreview.ModuleClassLoader.NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS
import org.jetbrains.android.uipreview.ModuleClassLoader.PROJECT_DEFAULT_TRANSFORMS
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.plugins.groovy.util.removeUserData
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.CancellationException
import java.util.function.Supplier

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
        && result.mode == ProjectSystemBuildManager.BuildMode.COMPILE) {
      ModuleManager.getInstance(project).modules.forEach { ModuleClassLoaderManager.get().clearCache(it) }
    }
  }

  override fun dispose() {}
}

/**
 * Key used to attach a shared class loader to a [Module]. The reference is held via a [SoftReference].
 */
private val SHARED_MODULE_CLASS_LOADER: Key<SoftReference<ModuleClassLoader>> = Key.create(::SHARED_MODULE_CLASS_LOADER.qualifiedName)

/**
 * Class providing the context for a ModuleClassLoader in which is being used.
 * Gradle class resolution depends only on [Module] but ASWB will also need the file to be able to
 * correctly resolve the classes.
 *
 * This should be a short living object since it retains a string reference to a [Module].
 */
class ModuleRenderContext private constructor(val module: Module, val fileProvider: Supplier<PsiFile?>) {
  val project: Project
    get() = module.project

  companion object {
    @JvmStatic
    fun forFile(module: Module, fileProvider: Supplier<PsiFile?>) = ModuleRenderContext(module, fileProvider)

    @JvmStatic
    fun forFile(file: PsiFile) = ModuleRenderContext(file.module!!) { file }

    /**
     * Always use one of the methods that can provide a file, only use this for testing.
     */
    @TestOnly
    @JvmStatic
    fun forModule(module: Module) = ModuleRenderContext(module) { null }
  }
}

/**
 * This is a wrapper around a class preloading [CompletableFuture] that allows for the proper disposal of the resources used.
 */
private class PreloadingTask(moduleClassLoader: ModuleClassLoader, classesToPreload: Collection<String>) : Disposable {
  private val module = WeakReference<Module>(moduleClassLoader.module)
  private val preloader = preload(moduleClassLoader, classesToPreload, getAppExecutorService())

  init {
    preloader.handle { _: Void, _: Throwable ->
      module.get()?.removeUserData(PRELOADING_TASK)
    }
    Disposer.register(moduleClassLoader, this)
  }

  fun stop() {
    try {
      preloader.cancel(false)
    } catch (ignore: CancellationException) { }
  }

  override fun dispose() {
    try {
      preloader.cancel(true)
    } catch (ignore: CancellationException) { }
    try {
      preloader.join()
    } catch (ignore: Exception) { }
  }
}

private val PRELOADING_TASK: Key<PreloadingTask> = Key.create(::PRELOADING_TASK.qualifiedName)

/**
 * A [ClassLoader] for the [Module] dependencies.
 */
class ModuleClassLoaderManager {
  // MutableSet is backed by the WeakHashMap in prod so we do not retain the holders
  private val holders: MutableMap<ModuleClassLoader, MutableSet<Any>> = IdentityHashMap()
  private var captureDiagnostics = false

  @TestOnly
  fun hasAllocatedSharedClassLoaders() = holders.isNotEmpty()

  /**
   * Returns a project class loader to use for rendering. May cache instances across render sessions.
   */
  @JvmOverloads
  @Synchronized
  fun getShared(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext, holder: Any,
                additionalProjectTransformation: ClassTransform = ClassTransform.identity,
                additionalNonProjectTransformation: ClassTransform = ClassTransform.identity,
                onNewModuleClassLoader: Runnable = Runnable {}): ModuleClassLoader {
    val module: Module = moduleRenderContext.module
    // If the shared ClassLoader is requested we have to stop preloading because otherwise it will slow down the normal loading flow
    module.removeUserData(PRELOADING_TASK)?.stop()
    var moduleClassLoader = module.getUserData(SHARED_MODULE_CLASS_LOADER)?.get()
    val combinedProjectTransformations: ClassTransform by lazy {
      combine(PROJECT_DEFAULT_TRANSFORMS, additionalProjectTransformation)
    }
    val combinedNonProjectTransformations: ClassTransform by lazy {
      combine(PROJECT_DEFAULT_TRANSFORMS, additionalNonProjectTransformation)
    }

    var oldClassLoader: ModuleClassLoader? = null
    if (moduleClassLoader != null) {
      val invalidate = when {
        parent != null && moduleClassLoader.parent != parent -> {
          LOG.debug("Parent has changed, discarding ModuleClassLoader")
          true
        }
        !moduleClassLoader.isUpToDate -> {
          LOG.debug("Files have changed, discarding ModuleClassLoader")
          true
        }
        !moduleClassLoader.areTransformationsUpToDate(combinedProjectTransformations, combinedNonProjectTransformations) -> {
          LOG.debug("Transformations have changed, discarding ModuleClassLoader")
          true
        }
        else -> {
          LOG.debug("ModuleClassLoader is up to date")
          false
        }
      }

      if (invalidate) {
        oldClassLoader = moduleClassLoader
        moduleClassLoader = null
      }
    }

    if (moduleClassLoader == null) {
      // Make sure the helper service is initialized
      moduleRenderContext.module.project.getService(ModuleClassLoaderProjectHelperService::class.java)
      LOG.debug { "Loading new class loader for module ${anonymize(module)}" }
      moduleClassLoader =
        ModuleClassLoader(parent, moduleRenderContext, combinedProjectTransformations, combinedNonProjectTransformations, createDiagnostics())
      module.putUserData(SHARED_MODULE_CLASS_LOADER, SoftReference(moduleClassLoader))
      oldClassLoader?.let { release(it, DUMMY_HOLDER) }
      onNewModuleClassLoader.run()
    }

    holders.computeIfAbsent(moduleClassLoader) { createHoldersSet() }.apply { add(holder) }
    return moduleClassLoader
  }

  /**
   * Return a [ModuleClassLoader] for a [Module] to be used for rendering. Similar to [getShared] but guarantees that the returned
   * [ModuleClassLoader] is not shared and the caller has full ownership of it.
   */
  @JvmOverloads
  @Synchronized
  fun getPrivate(parent: ClassLoader?,
                 moduleRenderContext: ModuleRenderContext,
                 holder: Any,
                 additionalProjectTransformation: ClassTransform = ClassTransform.identity,
                 additionalNonProjectTransformation: ClassTransform = ClassTransform.identity): ModuleClassLoader {
    // Make sure the helper service is initialized
    moduleRenderContext.module.project.getService(ModuleClassLoaderProjectHelperService::class.java)

    return ModuleClassLoader(parent, moduleRenderContext,
                      combine(PROJECT_DEFAULT_TRANSFORMS, additionalProjectTransformation),
                      combine(NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS, additionalNonProjectTransformation),
                             createDiagnostics()).apply {
      holders[this] = createHoldersSet().apply { add(holder) }
    }
  }

  private fun createDiagnostics() = if (captureDiagnostics) ModuleClassLoadedDiagnosticsImpl() else NopModuleClassLoadedDiagnostics

  /**
   * Creates a [MutableMap] to be used as a storage of [ModuleClassLoader] holders. We would like the implementation to be different in
   * prod and in tests:
   *
   * In Prod, it should be a Set of WEAK references. So that in case we do not release the holder (due to some unexpected flow) it is not
   * retained by the [ModuleClassLoaderManager]
   *
   * In Tests, we would like it to be a Set of STRONG references. So that any unreleased references got caught by the LeakHunter.
   */
  private fun createHoldersSet(): MutableSet<Any> =
    if (ApplicationManager.getApplication().isUnitTestMode) {
      mutableSetOf()
    } else {
      Collections.newSetFromMap(WeakHashMap())
    }

  @Synchronized
  fun clearCache(module: Module) {
    module.removeUserData(SHARED_MODULE_CLASS_LOADER)?.get()?.let { Disposer.dispose(it) }
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
  private fun stopManagingIfNotHeld(moduleClassLoader: ModuleClassLoader): Boolean {
    if (holders[moduleClassLoader]?.isNotEmpty() == true) {
      return false
    }
    // If that was a shared ModuleClassLoader that is no longer used, we have to destroy the old one to free the resources, but we also
    // recreate a new one for faster load next time
    moduleClassLoader.module?.let { module ->
      if (Disposer.isDisposed(module)) {
        return@let
      }
      if (module.getUserData(SHARED_MODULE_CLASS_LOADER)?.get() != moduleClassLoader) {
        return@let
      }
      module.removeUserData(PRELOADING_TASK)?.let { Disposer.dispose(it) }
      module.removeUserData(SHARED_MODULE_CLASS_LOADER)
      val moduleContext = moduleClassLoader.moduleContext ?: return@let
      val newClassLoader = ModuleClassLoader(
        moduleClassLoader.parent,
        moduleContext,
        moduleClassLoader.projectClassesTransformationProvider,
        moduleClassLoader.nonProjectClassesTransformationProvider,
        createDiagnostics())
      module.putUserData(SHARED_MODULE_CLASS_LOADER, SoftReference(newClassLoader))
      moduleClassLoader.module?.putUserData(PRELOADING_TASK, PreloadingTask(newClassLoader, moduleClassLoader.loadedClasses))
    }
    return true
  }

  /**
   * Inform [ModuleClassLoaderManager] that [ModuleClassLoader] is not used anymore and therefore can be
   * disposed if no longer managed.
   */
  fun release(moduleClassLoader: ModuleClassLoader, holder: Any) {
    unHold(moduleClassLoader, holder)
    if (stopManagingIfNotHeld(moduleClassLoader)) {
      Disposer.dispose(moduleClassLoader)
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
    private val LOG = Logger.getInstance(ModuleClassLoaderManager::class.java)

    @JvmStatic
    fun get(): ModuleClassLoaderManager =
      ApplicationManager.getApplication().getService(ModuleClassLoaderManager::class.java)
  }
}