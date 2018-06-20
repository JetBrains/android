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
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.helpers.matchFiles
import com.android.tools.idea.gradle.structure.model.helpers.parseFile
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.meta.*
import java.io.File

class PsSigningConfig(
  override val parent: PsAndroidModule
) : PsChildModel() {

  var resolvedModel: SigningConfig? = null ; private set
  private var parsedModel: SigningConfigModel? = null

  internal fun init(resolvedModel: SigningConfig?,
                    parsedModel: SigningConfigModel?) {
    this.resolvedModel = resolvedModel
    this.parsedModel = parsedModel
  }

  override val name get() = resolvedModel?.name ?:    parsedModel?.name() ?: ""

  var storeFile by SigningConfigDescriptors.storeFile
  var storePassword by SigningConfigDescriptors.storePassword
  var keyAlias by SigningConfigDescriptors.keyAlias
  var keyPassword by SigningConfigDescriptors.keyPassword

  override val isDeclared: Boolean get() = parsedModel != null

  fun ensureDeclared() {
    if (parsedModel == null) {
      parsedModel = parent.parsedModel!!.android()!!.addSigningConfig(name)
      parent.isModified = true
    }
  }

  object SigningConfigDescriptors : ModelDescriptor<PsSigningConfig, SigningConfig, SigningConfigModel> {
    override fun getResolved(model: PsSigningConfig): SigningConfig? = model.resolvedModel

    override fun getParsed(model: PsSigningConfig): SigningConfigModel? = model.parsedModel

    override fun setModified(model: PsSigningConfig) {
      model.ensureDeclared()
      model.isModified = true
    }

    val storeFile: SimpleProperty<PsSigningConfig, File> = property(
      "Store File",
      resolvedValueGetter = { storeFile },
      parsedPropertyGetter = { storeFile() },
      getter = { asFile() },
      // TODO: Store project relative path if possible.
      setter = { setValue(it.toString()) },
      parser = ::parseFile,
      matcher = { model, parsedValue, resolvedValue -> matchFiles(model.parent.resolvedModel?.rootDirPath, parsedValue, resolvedValue) }
    )

    val storePassword: SimpleProperty<PsSigningConfig, String> = property(
      "Store Password",
      resolvedValueGetter = { storePassword },
      parsedPropertyGetter = { storePassword().resolve() },
      // TODO: Properly handle other password types.
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val storeType: SimpleProperty<PsSigningConfig, String> = property(
      "Store Type",
      resolvedValueGetter = { storeType },
      // TODO: Properly handle other password types.
      parsedPropertyGetter = { storeType() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val keyAlias: SimpleProperty<PsSigningConfig, String> = property(
      "Key Alias",
      resolvedValueGetter = { keyAlias },
      parsedPropertyGetter = { keyAlias() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val keyPassword: SimpleProperty<PsSigningConfig, String> = property(
      "Key Password",
      // TODO(b/70501607): uiProperty(PsSigningConfig.SigningConfigDescriptors.storeType, ::simplePropertyEditor),
      resolvedValueGetter = { null },
      parsedPropertyGetter = { keyPassword().resolve() },
      // TODO: Properly handle other password types.
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )
  }
}
