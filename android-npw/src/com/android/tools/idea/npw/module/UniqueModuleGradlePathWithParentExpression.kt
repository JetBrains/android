/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.tools.adtui.validation.Validator
import com.android.tools.idea.npw.validator.ModuleValidator
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.expressions.Expression
import com.android.tools.idea.ui.validation.validators.PathValidator
import com.intellij.openapi.project.Project
import java.util.Locale

/**
 * This Expression takes the Application name (eg: "My Application"), and returns a valid module name which may be an absolute Gradle path
 * if [moduleParent] is provided. (eg: "myapplication", ":libs:myapplication") etc. If application name is already a rooted Gradle path
 * [moduleParent] is ignored.
 *
 * It also makes sure that the module name is unique.
 * Further validation of the module name may be needed, if the name is used to create a directory
 * @see PathValidator
 */
data class UniqueModuleGradlePathWithParentExpression(
  private val project: Project,
  private val applicationName: StringProperty,
  private val moduleParent: String
) : Expression<String>(applicationName) {
  override fun get(): String {
    val moduleValidator = ModuleValidator(project)

    fun isUnique(name: String): Boolean = moduleValidator.validate(name).severity != Validator.Severity.ERROR
    fun String.toGradleProjectName() = lowercase(Locale.US).replace("\\s".toRegex(), "")

    val parentModulePrefix = if (moduleParent.endsWith(":")) moduleParent else "$moduleParent:"
    val convertedName = applicationName.get().toGradleProjectName()
    val moduleGradlePath = if (convertedName.startsWith(":")) convertedName else parentModulePrefix + convertedName
    val uniqueModuleGradlePath =
      if (isUnique(moduleGradlePath)) moduleGradlePath
      else (2..1000).asSequence().map { "$moduleGradlePath$it" }.firstOrNull { isUnique(it) } ?: moduleGradlePath
    return if (parentModulePrefix == ":") uniqueModuleGradlePath.removePrefix(":") else uniqueModuleGradlePath
  }
}
