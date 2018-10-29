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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType

/**
 * ViewModel for [com.android.tools.idea.resourceExplorer.view.FileImportRow]
 */
class FileImportRowViewModel(
  val resourceFolderType: ResourceFolderType,
  val qualifierViewModel: QualifierConfigurationViewModel = QualifierConfigurationViewModel()) {

  // TODO get value from actual file
  var updateCallback: (() -> Unit)? = null
  var fileDimension: String = "64x64dp"
  var fileName: String = "File Name"
  var qualifiers: String = ""
  var fileSize: String = "8 KB"

  init {
    configurationUpdated(qualifierViewModel.applyConfiguration())
    qualifierViewModel.onConfigurationUpdated = this::configurationUpdated
  }

  private fun configurationUpdated(folderConfiguration: FolderConfiguration) {
    qualifiers = folderConfiguration.getFolderName(resourceFolderType)
    updateCallback?.invoke()
  }
}