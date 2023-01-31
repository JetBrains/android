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
@file:JvmName("MigrateToAndroidxUtil")

package org.jetbrains.android.refactoring

import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag
import com.android.support.AndroidxName
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.lang.properties.IProperty
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules

const val USE_ANDROIDX_PROPERTY = "android.useAndroidX"
const val ENABLE_JETIFIER_PROPERTY = "android.enableJetifier"

fun Project.setAndroidxProperties(value: String = "true") {
  // Add gradle properties to enable the androidx handling
  getProjectProperties(true)?.let {
    it.findPropertyByKey(USE_ANDROIDX_PROPERTY)?.setValue(value) ?: it.addProperty(USE_ANDROIDX_PROPERTY, value)
    it.findPropertyByKey(ENABLE_JETIFIER_PROPERTY)?.setValue(value) ?: it.addProperty(ENABLE_JETIFIER_PROPERTY, value)
  }
}

fun Project.disableJetifier(runAfterDisabling: (IProperty?) -> Unit) {
  getProjectProperties(true)?.findPropertyByKey(ENABLE_JETIFIER_PROPERTY).let {
    it?.setValue("false")
    runAfterDisabling(it)
  }
}

/**
 * Returns the value of [USE_ANDROIDX_PROPERTY], or `null` if this info is not available (e.g., if the modules are not Android modules and
 * the property is not specified in the `gradle.properties` file).
 */
fun Project.useAndroidX(): Boolean? {
  return modules.firstNotNullOfOrNull { it.getModuleSystem().useAndroidX } ?: runReadAction {
    getProjectProperties()?.findPropertyByKey(USE_ANDROIDX_PROPERTY)?.value?.toBooleanStrictOrNull()
  }
}

/** Returns the value of [USE_ANDROIDX_PROPERTY]. */
fun Project.isAndroidx(): Boolean = this.useAndroidX() ?: BooleanFlag.USE_ANDROID_X.legacyDefault

/**
 * Checks that the "enableJetifier" property is set to true
 */
fun Project.isEnableJetifier(): Boolean = runReadAction {
  getProjectProperties()?.findPropertyByKey(ENABLE_JETIFIER_PROPERTY)?.value?.toBoolean() ?: false
}

/**
 * Returns the actual name of an [AndroidxName] class to be used in a given [Project], based on the AndroidX properties set by the project.
 */
fun AndroidxName.getNameInProject(project: Project): String = runReadAction {
  if (project.isAndroidx()) newName() else oldName()
}
