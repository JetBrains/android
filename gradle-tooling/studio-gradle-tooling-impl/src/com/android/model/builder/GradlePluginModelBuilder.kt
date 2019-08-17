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
package com.android.model.builder

import com.android.model.GradlePluginModel
import com.android.model.impl.GradlePluginModelImpl
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder

class GradlePluginModelBuilder : ToolingModelBuilder {

  override fun canBuild(modelName: String): Boolean {
    return modelName == GradlePluginModel::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any? {
    val hasNoVariants = checkIfGradleProjectIsMissingVariants(project)

    return GradlePluginModelImpl(project.plugins.map { it.javaClass.name }, hasNoVariants)
  }

  private fun checkIfGradleProjectIsMissingVariants(project: Project): Boolean {
    val androidExtension = project.extensions.findByName("android") ?: return false
    // Use reflection to obtain the list of variants by attempting to call the methods.
    // It is possible to have both feature variants and library variants present on the FeatureExtension
    val applicationVariants = getVariantsFromClass(androidExtension, "getApplicationVariants")
    if (applicationVariants != null) {
      return applicationVariants.isEmpty()
    }

    val libraryVariants = getVariantsFromClass(androidExtension, "getLibraryVariants")
    val featureVariants = getVariantsFromClass(androidExtension, "getFeatureVariants")

    val hasEmptyLibraryVariants = libraryVariants != null && libraryVariants.isEmpty()

    return if (featureVariants == null) hasEmptyLibraryVariants else hasEmptyLibraryVariants && featureVariants.isEmpty()
  }

  private fun getVariantsFromClass(obj: Any, methodName: String): Any? = try {
    obj::class.java.getMethod(methodName).invoke(obj)
  }
  catch (e: NoSuchMethodException) {
    null
  }

  private fun Any.isEmpty(): Boolean = this::class.java.getMethod("isEmpty").invoke(this) as Boolean
}
