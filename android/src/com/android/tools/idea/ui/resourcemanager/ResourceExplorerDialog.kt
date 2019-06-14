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
package com.android.tools.idea.ui.resourcemanager

import com.android.tools.idea.ui.resourcecommon.ResourcePickerDialog
import com.android.tools.idea.ui.resourcemanager.explorer.ResourceExplorerView
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import org.jetbrains.android.facet.AndroidFacet

/** A [ResourceExplorer] used in a dialog for resource picking. */
class ResourceExplorerDialog(facet: AndroidFacet) : ResourcePickerDialog(facet.module.project), ResourceExplorerView.SelectionListener {

  private val resourceExplorerPanel = ResourceExplorer.createResourcePicker(facet)
  private var pickedResourceName: String? = null

  init {
    init()
    doValidate()
  }

  override fun createCenterPanel() = resourceExplorerPanel

  override fun onDesignAssetSetSelected(resourceAssetSet: ResourceAssetSet?) {
    pickedResourceName = (resourceAssetSet?.getHighestDensityAsset() as Asset).resourceItem.referenceToSelf.resourceUrl.toString()
  }

  override val resourceName: String?
    get() = pickedResourceName
}