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
package org.jetbrains.android.uipreview

import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.rendering.classloading.ClassLoaderOverlays
import com.android.tools.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.delete
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference
import java.nio.file.Path
import java.nio.file.Paths

private fun buildClassLoaderForOverlayPath(overlays: List<Path>) = UrlClassLoader.build()
  .files(overlays)
  .get()

/**
 * Component that keeps a list of current paths that have class overlays.
 */
@State(
  name = "ModuleClassLoaderOverlays",
  storages = [(Storage(StoragePathMacros.MODULE_FILE))],
)
class ModuleClassLoaderOverlays private constructor(module: Module, private val maxNumOverlays: Int) :
  PersistentStateComponent<ModuleClassLoaderOverlays.State>, ClassLoaderOverlays {

  /**
   * Handles project level notifications of updates in the [ModuleClassLoaderOverlays].
   */
  @Service(Service.Level.PROJECT)
  class NotificationManager {
    private val modificationTracker: SimpleModificationTracker = SimpleModificationTracker()
    private val _modificationFlow: MutableStateFlow<Long> = MutableStateFlow(modificationTracker.modificationCount)
    val modificationFlow: StateFlow<Long>
      get() = _modificationFlow

    /**
     * Notifies that a modification has happened to one of the [ModuleClassLoaderOverlays]s in the project.
     */
    internal fun fireModification() {
      modificationTracker.incModificationCount()
      _modificationFlow.value = modificationTracker.modificationCount
    }

    companion object {
      fun getInstance(project: Project): NotificationManager =
        project.getService(NotificationManager::class.java)
    }
  }

  @Tag("module-class-overlay-paths")
  class State(@XCollection(propertyElementName = "paths", style = XCollection.Style.v2) val paths: List<String> = listOf())

  private val moduleReference: WeakReference<Module> = WeakReference<Module>(module)
  private val _modificationTracker = SimpleModificationTracker()
  private var overlayClassLoader: DelegatingClassLoader.Loader? = null

  val modificationTracker: ModificationTracker = _modificationTracker

  /**
   * A [DelegatingClassLoader.Loader] that finds classes in the current overlay.
   */
  override val classLoaderLoader: DelegatingClassLoader.Loader = object : DelegatingClassLoader.Loader {
    override fun loadClass(fqcn: String): ByteArray? {
      val loader = synchronized(this@ModuleClassLoaderOverlays) {
        overlayClassLoader
      } ?: return null

      return loader.loadClass(fqcn)
    }
  }

  private val overlayPaths = ArrayDeque<Path>(10)

  constructor(module: Module): this(module, 10)

  @Synchronized
  fun invalidateOverlayPaths() {
    logger.debug("invalidateOverlayPaths")
    overlayPaths.clear()
    overlayClassLoader = null
    _modificationTracker.incModificationCount()
    moduleReference.get()?.project?.let { project ->
      NotificationManager.getInstance(project).fireModification()
    } ?: logger.warn("Module was disposed but ModuleClassLoaderOverlay is still referenced")
  }

  @Synchronized
  private fun reloadClassLoader() {
    overlayClassLoader = ClassLoaderLoader(buildClassLoaderForOverlayPath(overlayPaths))
    _modificationTracker.incModificationCount()
    moduleReference.get()?.project?.let { project ->
      NotificationManager.getInstance(project).fireModification()
    } ?: logger.warn("Module was disposed but ModuleClassLoaderOverlay is still referenced")
  }

  @Synchronized
  fun pushOverlayPath(path: Path) {
    if (overlayPaths.size == maxNumOverlays) {
      val removedPath = overlayPaths.removeLast()
      logger.debug("Removing overlay $removedPath")
      AppExecutorUtil.getAppExecutorService().submit {
        logger.debug("Deleting overlay from disk $removedPath")
        removedPath.delete(true)
      }
    }

    logger.debug("Added new overlay $path")
    overlayPaths.addFirst(path)

    reloadClassLoader()
  }

  override val modificationStamp: Long
    get() = _modificationTracker.modificationCount

  override fun getState(): State = State(paths = synchronized(this) { overlayPaths.map { it.toString() } })

  override fun loadState(state: State) {
    logger.debug("loadState (${state.paths.size} paths)")
    try {
      synchronized(this) {
        overlayPaths.clear()
        overlayPaths.addAll(state.paths.map { Paths.get(it) })

        reloadClassLoader()
      }
    } catch (e: Throwable) {
      logger.warn(e)
    }
  }

  companion object {
    private val logger = Logger.getInstance(ModuleClassLoaderOverlays::class.java)

    @JvmStatic
    fun getInstance(module: Module): ModuleClassLoaderOverlays =
      module.getHolderModule().getService(ModuleClassLoaderOverlays::class.java)
  }
}