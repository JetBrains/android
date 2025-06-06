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

import com.android.backup.BackupProgressListener.Step
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.util.progress.SequentialProgressReporter
import java.nio.file.Path
import kotlin.io.path.pathString

internal fun Path.isValid(): Boolean {
  val fileSystem = VirtualFileManager.getInstance().getFileSystem(LocalFileSystem.PROTOCOL)
  val names = (0 until nameCount).map { getName(it).pathString }
  return names.all { fileSystem.isValidName(it) && it.isNotBlank() }
}

internal fun Project.findModule(applicationId: String) =
  getProjectSystem().findModulesWithApplicationId(applicationId).firstOrNull()

internal fun Project.findHolderModule(applicationId: String) =
  findModule(applicationId)?.getModuleSystem()?.getHolderModule()

fun SequentialProgressReporter.onStep(step: Step) {
  nextStep(step.step * 100 / step.totalSteps, step.text)
}
