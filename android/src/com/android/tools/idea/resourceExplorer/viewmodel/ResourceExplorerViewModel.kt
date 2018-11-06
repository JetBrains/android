/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.resources.ResourceType
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import java.awt.Dimension
import java.awt.Image
import java.util.concurrent.CompletableFuture

/**
 * Interface for the view model of [com.android.tools.idea.resourceExplorer.view.ResourceExplorerView]
 *
 * The production implementation is [ProjectResourcesBrowserViewModel].
 */
interface ResourceExplorerViewModel {
  /**
   * callback called when the resource model has change. This happens when the facet is changed.
   */
  var resourceChangedCallback: (() -> Unit)?

  /**
   * The index in [resourceTypes] of the resource type being used.
   */
  var resourceTypeIndex: Int

  /**
   * The available resource types
   */
  val resourceTypes: Array<ResourceType>

  /**
   * Returns a preview of the [DesignAsset].
   */
  fun getDrawablePreview(dimension: Dimension, designAsset: DesignAsset): CompletableFuture<out Image?>

  /**
   * Returns a list of [ResourceSection] with one section per namespace, the first section being the
   * one containing the resource of the current module.
   */
  fun getResourcesLists(): List<ResourceSection>
}