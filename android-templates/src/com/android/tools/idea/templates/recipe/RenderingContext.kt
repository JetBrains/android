/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates.recipe

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.TemplateData
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.File

data class RenderingContext(
  val project: Project,
  val module: Module?,
  val commandName: String,
  val templateData: TemplateData,
  val outputRoot: File = VfsUtilCore.virtualToIoFile(project.guessProjectDir() ?: project.baseDir),
  val moduleRoot: File?,
  val dryRun: Boolean,
  val showErrors: Boolean
) {
  val plugins = mutableListOf<String>()
  val classpathEntries = mutableListOf<String>()
  val dependencies: SetMultimap<String, String> = LinkedHashMultimap.create()
  val dependenciesPerSourceSet: SetMultimap<String, Pair<String, String>> = LinkedHashMultimap.create()
  val targetFiles = mutableListOf<File>()
  val filesToOpen = mutableListOf<File>()
  val warnings = mutableListOf<String>()

  val showWarnings: Boolean get() = showErrors && dryRun

  val moduleTemplateData = templateData as? ModuleTemplateData
  val projectTemplateData = moduleTemplateData?.projectTemplateData ?: templateData as ProjectTemplateData
}