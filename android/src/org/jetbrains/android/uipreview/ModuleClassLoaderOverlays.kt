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
import com.android.tools.idea.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.delete
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
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
class ModuleClassLoaderOverlays private constructor(private val maxNumOverlays: Int = 10) :
  PersistentStateComponent<ModuleClassLoaderOverlays.State>, ModificationTracker {

  @Tag("module-class-overlay-paths")
  class State(@XCollection(propertyElementName = "paths", style = XCollection.Style.v2) val paths: List<String> = listOf())

  private val modificationTracker = SimpleModificationTracker()
  private var overlayClassLoader: DelegatingClassLoader.Loader? = null

  /**
   * A [DelegatingClassLoader.Loader] that finds classes in the current overlay.
   */
  val classLoaderLoader: DelegatingClassLoader.Loader = object : DelegatingClassLoader.Loader {
    override fun loadClass(fqcn: String): ByteArray? = overlayClassLoader?.loadClass(fqcn)
  }

  private val overlayPaths = ArrayDeque<Path>(10)

  constructor(module: Module): this()

  @Synchronized
  fun invalidateOverlayPaths() {
    logger.debug("invalidateOverlayPaths")
    overlayPaths.clear()
    overlayClassLoader = null
  }

  @Synchronized
  private fun reloadClassLoader() {
    overlayClassLoader = ClassLoaderLoader(buildClassLoaderForOverlayPath(overlayPaths))
    modificationTracker.incModificationCount()
  }

  @Synchronized
  fun pushOverlayPath(path: Path) {
    if (overlayPaths.size == maxNumOverlays) {
      AppExecutorUtil.getAppExecutorService().submit {
        overlayPaths.removeLast().apply {
          logger.debug("Removing overlay $path")
          delete(true)
        }
      }
    }

    logger.debug("Added new overlay $path")
    overlayPaths.addFirst(path)

    reloadClassLoader()
  }

  override fun getModificationCount(): Long = modificationTracker.modificationCount

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