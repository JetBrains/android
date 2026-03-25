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

import com.android.SdkConstants.FN_GRADLE_PROPERTIES
import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import java.io.File

private fun resource(path: String) = File("templates/project", path)

fun RecipeExecutor.androidProjectRecipe(data: ProjectTemplateData, appTitle: String, language: Language, makeIgnore: Boolean = true) {
  val topOut = data.rootDir
  val dslLanguage = data.dslLanguage

  // Declarative project do not require top level build.gradle files.
  if (dslLanguage.isKts || dslLanguage.isGroovy) {
    save(androidProjectBuildGradle(), topOut.resolve(dslLanguage.buildFileName))
  }

  if (makeIgnore) {
    copy(resource("project_ignore"), topOut.resolve(".gitignore"))
  }

  val settingsFile = topOut.resolve(dslLanguage.settingsFileName)

  save(androidProjectGradleSettings(appTitle, data.gradleVersion, data.agpVersion, data.additionalMavenRepos, dslLanguage), settingsFile)
  save(
    androidProjectGradleProperties(data.agpVersion, language == Language.Kotlin, dslLanguage, data.overridePathCheck),
    topOut.resolve(FN_GRADLE_PROPERTIES),
  )
  save(androidProjectLocalProperties(data.sdkDir), topOut.resolve(FN_LOCAL_PROPERTIES))
  copy(resource("wrapper"), topOut)
}
