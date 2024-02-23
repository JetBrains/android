/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog.localavd

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import java.awt.Component
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

/** An injected context for miscellaneous functionality that Compose shouldn't handle */
internal class Context
internal constructor(
  private val parent: Component,
  private val project: Project?,
  private val fileSystem: FileSystem = FileSystems.getDefault(),
) {
  internal fun chooseFile(onFileChosen: (Path) -> Unit) {
    val file =
      FileChooser.chooseFile(
        FileChooserDescriptorFactory.createSingleFileDescriptor(),
        parent,
        project,
        null,
      )

    if (file != null) {
      onFileChosen(file.toNioPath())
    }
  }

  internal fun getPath(path: String): Path = fileSystem.getPath(path)
}
