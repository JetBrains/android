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
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.res.AssetRepositoryImpl
import com.android.tools.idea.res.ResourceIdManager
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.AndroidPlatform
import org.jetbrains.android.sdk.getInstance

/** Studio-specific [RenderModelModule] constructed from [AndroidFacet]. */
class AndroidFacetRenderModelModule(private val facet: AndroidFacet) : RenderModelModule {
  override val ideaModule: Module
    get() = facet.module
  override var assetRepository: AssetRepository? = AssetRepositoryImpl(facet)
    private set
  override val resourceRepositoryManager: StudioResourceRepositoryManager
    get() = StudioResourceRepositoryManager.getInstance(facet)
  override val info: AndroidModuleInfo
    get() = StudioAndroidModuleInfo.getInstance(facet)
  override val androidPlatform: AndroidPlatform?
    get() = getInstance(ideaModule)
  override val resourceIdManager: ResourceIdManager
    get() = ResourceIdManager.get(ideaModule)

  override fun dispose() {
    assetRepository = null
  }
}