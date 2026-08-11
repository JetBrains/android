/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine.api

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.io.File

/** Extension point for post-processing newly rendered projects (e.g. setting API keys, secrets, or custom project metadata). */
interface TemplatePostProcessor {
  companion object {
    val EP_NAME = ExtensionPointName.create<TemplatePostProcessor>("com.android.templatePostProcessor")
  }

  /** Unique ID matching postProcessors declared in template definitions. */
  val id: String

  /** Invoked after TemplateEngine finishes rendering template files to targetDir. */
  fun onProjectCreated(project: Project?, targetDir: File, parameters: Map<String, Any>)
}
