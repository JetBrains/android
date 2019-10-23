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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.getValue

class PsBuildScript(override val parent: PsProject) : PsChildModel() {
  override val descriptor by Descriptors
  override val name: String = "buildScript"
  override val isDeclared: Boolean = parent.isDeclared
  val parsedModel: BuildScriptModel? get() = parent.parsedModel.projectBuildModel?.buildscript()

  object Descriptors : ModelDescriptor<PsBuildScript, Nothing, BuildScriptModel> {
    override fun getResolved(model: PsBuildScript): Nothing? = null
    override fun getParsed(model: PsBuildScript): BuildScriptModel? = model.parsedModel
    override fun prepareForModification(model: PsBuildScript) = Unit
    override fun setModified(model: PsBuildScript) {
      model.isModified = true
    }
  }
}