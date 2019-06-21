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

import com.android.ide.common.resources.ResourceItem
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.ui.resourcecommon.ResourcePickerDialog
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import javax.swing.BorderFactory

/** A [ResourceExplorer] used in a dialog for resource picking. */
class ResourceExplorerDialog(facet: AndroidFacet) : ResourcePickerDialog(facet.module.project) {

  @TestOnly // TODO: consider getting this in a better way.
  val resourceExplorerPanel = ResourceExplorer.createResourcePicker(facet, this::updatePickedResourceName)

  private var pickedResourceName: String? = null

  init {
    init()
    doValidate()
  }

  override fun createCenterPanel() = resourceExplorerPanel.apply {
    border = BorderFactory.createMatteBorder(0, 0, JBUI.scale(1), 0, AdtUiUtils.DEFAULT_BORDER_COLOR)
  }

  override fun dispose() {
    super.dispose()
    Disposer.dispose(resourceExplorerPanel)
  }

  override val resourceName: String?
    get() = pickedResourceName

  private fun updatePickedResourceName(resource: ResourceItem) {
    pickedResourceName = resource.referenceToSelf.resourceUrl.toString()
    doOKAction()
  }
}