/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.SigningConfig
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.model.PsChildModel

class PsSigningConfig(
    parent: PsAndroidModule,
    private val resolvedModel: SigningConfig?,
    private val parsedModel: SigningConfigModel?
) : PsChildModel(parent), PsAndroidModel {

  private var name = when {
    resolvedModel != null -> resolvedModel.name
    parsedModel != null -> parsedModel.name()
    else -> ""
  }

  override fun getName(): String = name
  override fun getParent(): PsAndroidModule = super.getParent() as PsAndroidModule
  override fun isDeclared(): Boolean = parsedModel != null
  override fun getResolvedModel(): SigningConfig? = resolvedModel
  override fun getGradleModel(): AndroidModuleModel = parent.gradleModel
}