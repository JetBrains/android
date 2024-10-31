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

package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.LogcatBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbAwareAction

private val MAPPING_FILE_EXTENSIONS = setOf("txt", "map", "pgmap")

// TODO(b/366026739): Replace with final icon & text
internal class SetProguardMappingAction :
  DumbAwareAction(
    LogcatBundle.message("logcat.proguard.mapping.action.name"),
    null,
    AllIcons.General.InspectionsEye,
  ) {
  override fun getActionUpdateThread() = BGT

  override fun actionPerformed(e: AnActionEvent) {
    val logcatPresenter = e.getLogcatPresenter() ?: return
    val descriptor =
      FileChooserDescriptor(true, false, true, true, false, false)
        .withTitle(LogcatBundle.message("logcat.proguard.mapping.action.chooser.title"))
        .withFileFilter {
          it.name.substringAfterLast('.') in MAPPING_FILE_EXTENSIONS
        }
    val path =
      FileChooserFactory.getInstance()
        .createFileChooser(descriptor, e.project, null)
        .choose(e.project)
        .firstOrNull()
        ?.toNioPath()
        ?.normalize() ?: return
    logcatPresenter.setProguardMapping(path)
  }
}
