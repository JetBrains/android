/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.ide.common.rendering.api.AssetRepository
import com.android.tools.idea.model.MergedManifestException
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.StudioAssetFileOpener
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.module.AndroidModuleInfo
import com.android.tools.module.ModuleDependencies
import com.android.tools.module.ModuleKey
import com.android.tools.idea.module.ModuleKeyManager
import com.android.tools.idea.res.StudioResourceIdManager
import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.RenderTask
import com.android.tools.rendering.api.EnvironmentContext
import com.android.tools.rendering.api.RenderModelManifest
import com.android.tools.rendering.api.RenderModelModule
import com.android.tools.rendering.api.RenderModelModuleLoggingId
import com.android.tools.res.AssetRepositoryBase
import com.android.tools.res.ids.ResourceIdManager
import com.android.tools.sdk.AndroidPlatform
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.getInstance
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Studio-specific [RenderModelModule] constructed from [AndroidFacet]. */
class AndroidFacetRenderModelModule(private val facet: AndroidFacet) : RenderModelModule {
  private val LOG = Logger.getInstance(AndroidFacetRenderModelModule::class.java)
  private val _isDisposed = AtomicBoolean(false)
  init {
    if (!Disposer.tryRegister(facet, this)) {
      _isDisposed.set(true)
    }
  }

  override fun getIdeaModule(): Module = facet.module
  override var assetRepository: AssetRepository? = AssetRepositoryBase(
    StudioAssetFileOpener(facet))
    private set
  override val manifest: RenderModelManifest?
    get() {
      try {
        return RenderMergedManifest(MergedManifestManager.getMergedManifest(facet.module).get(1, TimeUnit.SECONDS))
      }
      catch (e: InterruptedException) {
        throw ProcessCanceledException(e)
      }
      catch (e: TimeoutException) {
        LOG.warn(e);
      }
      catch (e: ExecutionException) {
        when (val cause = e.cause) {
          is ProcessCanceledException -> throw cause
          is MergedManifestException -> LOG.warn(e)
          else -> LOG.error(e)
        }
      }

      return null
    }
  override val resourceRepositoryManager: StudioResourceRepositoryManager
    get() = StudioResourceRepositoryManager.getInstance(facet)
  override val info: AndroidModuleInfo?
    get() = if (facet.isDisposed) null else StudioAndroidModuleInfo.getInstance(facet)
  override val androidPlatform: AndroidPlatform?
    get() = getInstance(facet.module)
  override val resourceIdManager: ResourceIdManager
    get() = StudioResourceIdManager.get(facet.module)
  override val moduleKey: ModuleKey
    get() = ModuleKeyManager.getKey(facet.module)
  override val resourcePackage: String?
    get() = facet.module.getModuleSystem().getPackageName()
  override val dependencies: ModuleDependencies = facet.getModuleSystem().moduleDependencies
  override val project: Project
    get() = facet.module.project
  override val isDisposed: Boolean
    get() = _isDisposed.get()

  override fun dispose() {
    _isDisposed.set(true)
    assetRepository = null
  }

  override val name: String
    get() = nameFromFacet(facet)
  override val environment: EnvironmentContext = StudioEnvironmentContext(facet.module)
  override fun createModuleRenderContext(weakRenderTask: WeakReference<RenderTask>): ModuleRenderContext {
    return StudioModuleRenderContext.forFile(facet.module) {
      weakRenderTask.get()?.xmlFile?.get()
    }
  }

  companion object {
    private fun nameFromFacet(facet: AndroidFacet): String = facet.module.name
    fun loggingId(facet: AndroidFacet): RenderModelModuleLoggingId {
      return object : RenderModelModuleLoggingId {
        override val isDisposed: Boolean get() = facet.isDisposed || facet.module.isDisposed
        override val name: String get() = nameFromFacet(facet)
      }
    }
  }
}
