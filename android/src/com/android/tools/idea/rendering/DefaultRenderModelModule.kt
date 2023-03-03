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
import com.android.tools.idea.res.ResourceIdManager
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.openapi.module.Module
import com.android.tools.sdk.AndroidPlatform

/**
 * Almost trivial, delegating implementation of the [RenderModelModule] interface. The only complication is freeing reference to the
 * [assetRepository] keeping the current behavior in [RenderTask].
 */
class DefaultRenderModelModule(
  override val ideaModule: Module,
  assets: AssetRepository?,
  override val resourceRepositoryManager: ResourceRepositoryManager,
  override val info: AndroidModuleInfo,
  override val androidPlatform: AndroidPlatform?,
  override val resourceIdManager: ResourceIdManager
) : RenderModelModule {
  override var assetRepository: AssetRepository? = assets
    private set

  override fun dispose() {
    assetRepository = null
  }
}