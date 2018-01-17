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
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.dsl
import com.android.tools.idea.gradle.structure.model.meta.property
import java.io.File

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

  val storeFile by SigningConfigDescriptors.storeFile
  val storePassword by SigningConfigDescriptors.storePassword
  val storeType by SigningConfigDescriptors.storeType
  val keyAlias by SigningConfigDescriptors.keyAlias
  val keyPassword by SigningConfigDescriptors.keyPassword

  override fun getName(): String = name
  override fun getParent(): PsAndroidModule = super.getParent() as PsAndroidModule
  override fun isDeclared(): Boolean = parsedModel != null
  override fun getResolvedModel(): SigningConfig? = resolvedModel
  override fun getGradleModel(): AndroidModuleModel = parent.gradleModel

  object SigningConfigDescriptors : ModelDescriptor<PsSigningConfig, SigningConfig, SigningConfigModel> {
    override fun getResolved(model: PsSigningConfig): SigningConfig? = model.resolvedModel

    override fun getParsed(model: PsSigningConfig): SigningConfigModel? = model.parsedModel

    override fun setModified(model: PsSigningConfig) {
      model.isModified = true
    }

    val storeFile: ModelSimpleProperty<PsSigningConfig, File> = property(
        "Store File",
        getResolvedValue = { storeFile },
        getParsedValue = { File(storeFile().value()) },
        getParsedRawValue = { storeFile().dsl() },
        // TODO: Store project relative path if possible.
        setParsedValue = { setStoreFile(it.absolutePath) },
        clearParsedValue = { removeStoreFile() },
        parse = { parseFile(it) }
    )
    val storePassword: ModelSimpleProperty<PsSigningConfig, String> = property(
        "Store Password",
        getResolvedValue = { storePassword },
        // TODO: Properly handle other password types.
        getParsedValue = { storePassword().value()?.passwordText },
        getParsedRawValue = { storePassword().dsl() },
        setParsedValue = { setStorePassword(SigningConfigModel.SigningConfigPassword.Type.PLAIN_TEXT, it) },
        clearParsedValue = { removeStorePassword() },
        parse = { parseString(it) }
    )
    val storeType: ModelSimpleProperty<PsSigningConfig, String> = property(
        "Store Type",
        getResolvedValue = { storeType },
        // TODO: Properly handle other password types.
        getParsedValue = { storeType().value() },
        getParsedRawValue = { storeType().dsl() },
        setParsedValue = { setStoreType(it) },
        clearParsedValue = { removeStoreType() },
        parse = { parseString(it) }
    )
    val keyAlias: ModelSimpleProperty<PsSigningConfig, String> = property(
        "Key Alias",
        getResolvedValue = { keyAlias },
        getParsedValue = { keyAlias().value() },
        getParsedRawValue = { keyAlias().dsl() },
        setParsedValue = { setKeyAlias(it) },
        clearParsedValue = { removeKeyAlias() },
        parse = { parseString(it) }
    )
    val keyPassword: ModelSimpleProperty<PsSigningConfig, String> = property(
        "Key Password",
        getResolvedValue = { keyPassword },
        // TODO: Properly handle other password types.
        getParsedValue = { keyPassword().value()?.passwordText },
        getParsedRawValue = { keyPassword().dsl() },
        setParsedValue = { setKeyPassword(SigningConfigModel.SigningConfigPassword.Type.PLAIN_TEXT, it) },
        clearParsedValue = { removeKeyPassword() },
        parse = { parseString(it) }
    )
  }
}