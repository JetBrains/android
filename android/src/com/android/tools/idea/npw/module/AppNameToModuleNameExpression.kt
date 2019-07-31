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

import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.expressions.Expression
import com.android.tools.idea.ui.validation.validators.PathValidator
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.util.Locale

/**
 * This Expression takes the Application name (eg: "My Application"), and returns a valid module name (eg: "myapplication").
 * It also makes sure that the module name is unique.
 * Further validation of the module name may be needed, if the name is used to create a directory
 * @see PathValidator
 */
data class AppNameToModuleNameExpression(
  private val project: Project?,
  private val applicationName: StringProperty,
  private val moduleParent: String?
) : Expression<String>(applicationName) {
  override fun get(): String {
    val moduleName = applicationName.get().toLowerCase(Locale.US).replace("\\s".toRegex(), "")

    val uniqueModuleName =
      if (moduleName.isUnique())
        moduleName
      else
        generateSequence(2, Int::inc).map { moduleName + it }.first { it.isUnique() }

    return if (moduleParent == null) uniqueModuleName else ":$moduleParent:$uniqueModuleName"
  }

  /**
   * True if the given module name is unique inside the given project or the project is null.
   */
  private fun String.isUnique(): Boolean =
    project == null || ModuleManager.getInstance(project)!!.modules.none { it!!.name.equals(this, ignoreCase = false) }
}
