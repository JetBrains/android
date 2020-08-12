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

import com.android.ide.common.gradle.model.IdeSigningConfig
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.helpers.matchFiles
import com.android.tools.idea.gradle.structure.model.helpers.parseFile
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.SimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.asFile
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.meta.property
import com.android.tools.idea.gradle.structure.model.meta.withFileSelectionRoot
import icons.StudioIcons.Misc.SIGNING_CONFIG
import java.io.File
import javax.swing.Icon

class PsSigningConfig(
  override val parent: PsAndroidModule,
  private val renamed: (String, String) -> Unit
) : PsChildModel() {
  override val descriptor by SigningConfigDescriptors
  var resolvedModel: IdeSigningConfig? = null ; private set
  private var parsedModel: SigningConfigModel? = null

  internal fun init(
    resolvedModel: IdeSigningConfig?,
    parsedModel: SigningConfigModel?
  ) {
    this.resolvedModel = resolvedModel
    this.parsedModel = parsedModel
  }

  override val name get() = resolvedModel?.name ?: parsedModel?.name() ?: ""

  var storeFile by SigningConfigDescriptors.storeFile
  var storePassword by SigningConfigDescriptors.storePassword
  var keyAlias by SigningConfigDescriptors.keyAlias
  var keyPassword by SigningConfigDescriptors.keyPassword

  override val isDeclared: Boolean get() = parsedModel != null
  override val icon: Icon = SIGNING_CONFIG

  fun ensureDeclared() {
    if (parsedModel == null) {
      parsedModel = parent.parsedModel!!.android().addSigningConfig(name)
      parent.isModified = true
    }
  }

  fun rename(newName: String, renameReferences: Boolean = false) {
    ensureDeclared()
    val oldName = name
    parsedModel!!.rename(newName, renameReferences)
    renamed(oldName, newName)
  }

  object SigningConfigDescriptors : ModelDescriptor<PsSigningConfig, IdeSigningConfig, SigningConfigModel> {
    override fun getResolved(model: PsSigningConfig): IdeSigningConfig? = model.resolvedModel

    override fun getParsed(model: PsSigningConfig): SigningConfigModel? = model.parsedModel

    override fun prepareForModification(model: PsSigningConfig) {
      model.ensureDeclared()
    }

    override fun setModified(model: PsSigningConfig) {
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
      .withFileSelectionRoot(browseRoot = { null }, resolveRoot = { null })

    val storePassword: SimpleProperty<PsSigningConfig, String> = property(
      "Store Password",
      resolvedValueGetter = { storePassword },
      parsedPropertyGetter = { storePassword().resolve() },
      // TODO: Properly handle other password types.
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

    override val properties: Collection<ModelProperty<PsSigningConfig, *, *, *>> =
      listOf(storeFile, storePassword, keyAlias, keyPassword)
  }
}
