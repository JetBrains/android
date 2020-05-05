/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.validator

import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.Validator.Result
import com.android.tools.adtui.validation.Validator.Severity
import com.android.tools.idea.npw.module.getModuleRoot
import com.android.tools.idea.ui.validation.validators.PathValidator
import com.google.common.base.CharMatcher.anyOf
import com.google.common.base.CharMatcher.inRange
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.annotations.SystemIndependent

/**
 * Validates the module name and its location.
 */
class ModuleValidator constructor (
  val project: Project
) : Validator<String> {
  private val projectPath: @SystemIndependent String = project.basePath!!
  private val pathValidator: PathValidator = PathValidator.createDefault("module location")
  private val ILLEGAL_CHAR_MATCHER =
    inRange('a', 'z').or(inRange('A', 'Z')).or(inRange('0', '9')).or(anyOf("_-: ")).negate()

  override fun validate(moduleGradlePath: String): Result {
    val illegalCharIdx = ILLEGAL_CHAR_MATCHER.indexIn(moduleGradlePath)
    return when {
      moduleGradlePath.isEmpty() -> Result(Severity.ERROR, message("android.wizard.validate.empty.module.name"))
      ModuleManager.getInstance(project).findModuleByName(moduleGradlePath) != null ->
        Result(Severity.ERROR, message("android.wizard.validate.module.already.exists", moduleGradlePath))
      illegalCharIdx >= 0 ->
        Result(Severity.ERROR, message("android.wizard.validate.module.illegal.character", moduleGradlePath[illegalCharIdx], moduleGradlePath))
      else -> pathValidator.validate(getModuleRoot(projectPath, moduleGradlePath))
    }
  }
}