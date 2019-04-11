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
package com.android.tools.idea.ui.resourcemanager.viewmodel

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.resources.Density
import com.android.resources.LayoutDirection
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.CollectionParam
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import org.junit.Test

class FileImportRowViewModelTest {

  @Test
  fun qualifiersConfigured() {
    val file = MockVirtualFile("asset.png", "")
    val densityQualifier = DensityQualifier(Density.MEDIUM)
    val asset = DesignAsset(file, listOf(densityQualifier), ResourceType.DRAWABLE)
    val viewModel = FileImportRowViewModel(asset, ResourceFolderType.DRAWABLE) {}
    val qualifierViewModel = viewModel.qualifierViewModel
    val directionQualifier = qualifierViewModel.getAvailableQualifiers().first { it is LayoutDirectionQualifier }
    val qualifierConfiguration = qualifierViewModel.selectQualifier(directionQualifier)!!
    val collectionParam = qualifierConfiguration.parameters.first() as CollectionParam<LayoutDirection>
    collectionParam.paramValue = LayoutDirection.RTL
    qualifierViewModel.applyConfiguration()
    assertThat(asset.qualifiers.map { it.longDisplayValue }).containsExactly("Right To Left", "Medium Density")
  }
}