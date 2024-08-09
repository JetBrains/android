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

package com.android.tools.idea.backup

import com.android.tools.idea.backup.BackupBundle.message
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileTypes.FileType
import icons.StudioIcons

/** A [FileType] for an Android backup file */
internal object BackupFileType : FileType {
  private const val EXT = "backup"

  val FILE_CHOOSER_DESCRIPTOR: FileChooserDescriptor =
    FileChooserDescriptor(true, false, true, true, false, false)
      .withTitle(message("backup.choose.restore.file.dialog.title"))
      .withFileFilter { it.name.endsWith(".$EXT") }

  val FILE_SAVER_DESCRIPTOR: FileSaverDescriptor =
    FileSaverDescriptor(
      message("backup.choose.backup.file.dialog.title"),
      "",
      BackupFileType.defaultExtension,
    )

  override fun getName() = "Android Backup File"

  override fun getDescription() = "Android backup file"

  override fun getDefaultExtension() = EXT

  // TODO(b/348406593): Replace with dedicated icon
  override fun getIcon() = StudioIcons.Shell.Menu.DATABASE_INSPECTOR

  override fun isBinary() = true
}
