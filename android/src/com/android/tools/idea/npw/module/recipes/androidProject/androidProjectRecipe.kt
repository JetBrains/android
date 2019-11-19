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
package com.android.tools.idea.npw.module.recipes.androidProject

import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import java.io.File

private fun resource(path: String) = File("templates/project", path)

fun RecipeExecutor.androidProjectRecipe(
  data: ProjectTemplateData,
  appTitle: String,
  language: Language,
  addAndroidXSupport: Boolean,
  makeIgnore: Boolean = true
) {
  val topOut = data.rootDir
  save(
    androidProjectBuildGradle(language == Language.KOTLIN, data.kotlinVersion, false, data.gradlePluginVersion),
    topOut.resolve("build.gradle")
  )

  if (makeIgnore) {
    copy(resource("project_ignore"), topOut.resolve(".gitignore"))
  }

  save(androidProjectGradleSettings(appTitle), topOut.resolve("settings.gradle"))
  save(androidProjectGradleProperties(addAndroidXSupport), topOut.resolve("gradle.properties"))
  copy(resource("wrapper"), topOut)

  if (data.sdkDir != null) {
    save(androidProjectLocalProperties(data.sdkDir), topOut.resolve("local.properties"))
  }
}
