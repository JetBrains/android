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
package com.android.tools.idea.npw.module.recipes

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

fun RecipeExecutor.addKotlinPlugins()  {
  applyPlugin("kotlin-android")
  applyPlugin("kotlin-android-extensions")
}

fun RecipeExecutor.addKotlinDependencies(generateKotlin: Boolean) {
  if (generateKotlin) {
    addDependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:\$kotlin_version\"")
  }
}

fun RecipeExecutor.setKotlinVersion(kotlinVersion: String) {
    setExtVar("kotlin_version", kotlinVersion)
    addClasspathDependency("org.jetbrains.kotlin:kotlin-gradle-plugin:\$kotlin_version")
}

fun RecipeExecutor.addKotlinToBaseProject(language: Language, kotlinVersion: String, isNewProject: Boolean = false) {
  if (!isNewProject && language == Language.Kotlin) {
    setKotlinVersion(kotlinVersion)
  }
}

// TODO(qumeric): The two functions above, addKotlinPlugins and addKotlinDependencies, are duplicating the work of addAllKotlinDependencies,
//                when creating a new module (isNewModule == true).
fun RecipeExecutor.addAllKotlinDependencies(data: ModuleTemplateData) {
  val projectData = data.projectTemplateData
  if (!data.isNew && projectData.language == Language.Kotlin) {
    applyPlugin("kotlin-android")
    applyPlugin("kotlin-android-extensions")
    if (!hasDependency("org.jetbrains.kotlin:kotlin-stdlib")) {
      addDependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:\$kotlin_version")
      setKotlinVersion(projectData.kotlinVersion)
    }
  }
}