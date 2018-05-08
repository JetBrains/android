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
import com.android.tools.idea.gradle.structure.model.helpers.parseFile
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.meta.*
import java.io.File

class PsSigningConfig(
  override val parent: PsAndroidModule,
  override val resolvedModel: SigningConfig?,
  private val parsedModel: SigningConfigModel?
) : PsChildModel(parent), PsAndroidModel {

  override val name = when {
    resolvedModel != null -> resolvedModel.name
    parsedModel != null -> parsedModel.name()
    else -> ""
  }

  var storeFile by SigningConfigDescriptors.storeFile
  var storePassword by SigningConfigDescriptors.storePassword
  var storeType by SigningConfigDescriptors.storeType
  var keyAlias by SigningConfigDescriptors.keyAlias
  var keyPassword by SigningConfigDescriptors.keyPassword

  override val isDeclared: Boolean get() = parsedModel != null
  override val gradleModel: AndroidModuleModel = parent.gradleModel

  object SigningConfigDescriptors : ModelDescriptor<PsSigningConfig, SigningConfig, SigningConfigModel> {
    override fun getResolved(model: PsSigningConfig): SigningConfig? = model.resolvedModel

    override fun getParsed(model: PsSigningConfig): SigningConfigModel? = model.parsedModel

    override fun setModified(model: PsSigningConfig) {
      model.isModified = true
    }

    val storeFile: SimpleProperty<PsSigningConfig, File> = property(
      "Store File",
      getResolvedValue = { storeFile },
      getParsedProperty = { storeFile() },
      getter = { asFile() },
      // TODO: Store project relative path if possible.
      setter = { setValue(it.absolutePath) },
      parse = ::parseFile
    )

    val storePassword: SimpleProperty<PsSigningConfig, String> = property(
      "Store Password",
      getResolvedValue = { storePassword },
      getParsedProperty = { storePassword().resolve() },
      // TODO: Properly handle other password types.
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )

    val storeType: SimpleProperty<PsSigningConfig, String> = property(
      "Store Type",
      getResolvedValue = { storeType },
      // TODO: Properly handle other password types.
      getParsedProperty = { storeType() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )

    val keyAlias: SimpleProperty<PsSigningConfig, String> = property(
      "Key Alias",
      getResolvedValue = { keyAlias },
      getParsedProperty = { keyAlias() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )

    val keyPassword: SimpleProperty<PsSigningConfig, String> = property(
      "Key Password",
      getResolvedValue = { keyPassword },
      getParsedProperty = { keyPassword().resolve() },
      // TODO: Properly handle other password types.
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )
  }
}
