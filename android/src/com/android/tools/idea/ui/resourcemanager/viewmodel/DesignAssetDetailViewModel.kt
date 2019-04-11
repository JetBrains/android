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
package com.android.tools.idea.ui.resourcemanager.viewmodel

import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.plugin.DesignAssetRendererManager
import com.intellij.openapi.module.Module
import java.awt.Dimension
import java.awt.Image
import java.util.concurrent.CompletableFuture

class DesignAssetDetailViewModel(val module: Module) {

  /**
   * Fetch the image representing the [asset] and returns a [CompletableFuture] that will
   * returns the fetched image.
   */
  fun fetchAssetImage(
    asset: DesignAsset,
    dimension: Dimension
  ): CompletableFuture<out Image?> {
    return DesignAssetRendererManager.getInstance()
      .getViewer(asset.file)
      .getImage(asset.file, module, dimension)
  }
}