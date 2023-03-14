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
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.model.MergedManifestException
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.AssetRepositoryImpl
import com.android.tools.idea.res.ResourceIdManager
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.sdk.AndroidPlatform
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.getInstance
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

  override val ideaModule: Module
    get() = facet.module
  override var assetRepository: AssetRepository? = AssetRepositoryImpl(facet)
    private set
  override val manifest: RenderModelManifest?
    get() {
      try {
        return RenderMergedManifest(MergedManifestManager.getMergedManifest(ideaModule).get(1, TimeUnit.SECONDS))
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
  override val info: AndroidModuleInfo
    get() = StudioAndroidModuleInfo.getInstance(facet)
  override val androidPlatform: AndroidPlatform?
    get() = getInstance(ideaModule)
  override val resourceIdManager: ResourceIdManager
    get() = ResourceIdManager.get(ideaModule)
  override val moduleKey: Any
    get() = ideaModule
  override val resourcePackage: String?
    get() = ideaModule.getModuleSystem().getPackageName()
  override val dependencies: RenderDependencyInfo = StudioRenderDependencyInfo(ideaModule)
  override val project: Project
    get() = ideaModule.project
  override val isDisposed: Boolean
    get() = _isDisposed.get()

  override fun dispose() {
    _isDisposed.set(true)
    assetRepository = null
  }

  override val name: String
    get() = facet.module.name
  override val environment: EnvironmentContext = StudioEnvironmentContext(facet.module.project)
}