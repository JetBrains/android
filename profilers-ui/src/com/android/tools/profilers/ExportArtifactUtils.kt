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
package com.android.tools.profilers

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.function.Consumer

/**
 * This class contains utility methods to aid in exporting a {@link SessionArtifact} to a file.
 */
object ExportArtifactUtils {

  /**
   * Opens an export file dialog and prompts user to export/save artifact as a file to disk.
   */
  @JvmStatic
  fun exportArtifact(exportableName: String,
                     exportExtension: String,
                     exportAction: Consumer<OutputStream>,
                     ideProfilerComponents: IdeProfilerComponents,
                     ideServices: IdeProfilerServices) {
    ideProfilerComponents.createExportDialog().open(
      { "Export As" },
      { exportableName },
      { exportExtension },
      { file: File -> ideServices.saveFile(file, { outputStream: FileOutputStream -> exportAction.accept(outputStream) }, null) }
    )
  }
}