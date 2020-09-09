/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import java.awt.event.ActionEvent
import java.io.File

/**
 * Show the contents of the AVD on disk
 */
class ShowAvdOnDiskAction(avdInfoProvider: AvdInfoProvider) : AvdUiAction(
  avdInfoProvider, "Show on Disk", "Open the location of this AVD's data files", AllIcons.Actions.Menu_open
) {
  override fun actionPerformed(e: ActionEvent) {
    val info = avdInfo ?: return
    val dataFolder = File(info.dataFolderPath)
    RevealFileAction.openDirectory(dataFolder)
  }

  override fun isEnabled(): Boolean = avdInfo != null
}