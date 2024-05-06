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
package com.android.tools.idea.editors.manifest

import com.android.ide.common.blame.SourceFilePosition
import com.android.manifmerger.Actions
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import java.io.File

interface ManifestPanelToken<P: AndroidProjectSystem> : Token {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<ManifestPanelToken<AndroidProjectSystem>>("com.android.tools.idea.editors.manifest.manifestPanelToken")
  }

  fun getExternalAndroidLibraryDisplayName(library: ExternalAndroidLibrary): String

  /**
   * Return true if this record is handled by the Project System.
   */
  fun recordLocationReference(record: Actions.Record, files: MutableSet<File>): Boolean

  fun handleReferencedFiles(
    referenced: Set<File>,
    sortedFiles: MutableList<ManifestFileWithMetadata>,
    sortedOtherFiles: MutableList<ManifestFileWithMetadata>,
    metadataForFileCreator: (SourceFilePosition) -> ManifestFileWithMetadata
  )

  fun getMetadataForRecord(
    record: Actions.Record,
    metadataForFileCreator: (SourceFilePosition) -> ManifestFileWithMetadata
  ): ManifestFileWithMetadata?

  fun createMetadataForFile(
    file: File?,
    module: Module
  ): ManifestFileWithMetadata?

  fun generateMinSdkSettingRunnable(
    module: Module,
    minSdk: Int
  ): Runnable?
}