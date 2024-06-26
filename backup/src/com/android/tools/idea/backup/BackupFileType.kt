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

import com.intellij.openapi.fileTypes.FileType
import icons.StudioIcons

/** A [FileType] for an Android backup file */
internal object BackupFileType : FileType {
  override fun getName() = "Android Backup File"

  override fun getDescription() = "Android backup file"

  override fun getDefaultExtension() = "backup"

  // TODO(b/348406593): Replace with dedicated icon
  override fun getIcon() = StudioIcons.Shell.Menu.DATABASE_INSPECTOR

  override fun isBinary() = true
}
