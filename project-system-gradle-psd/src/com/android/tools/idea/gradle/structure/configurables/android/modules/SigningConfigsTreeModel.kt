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
package com.android.tools.idea.gradle.structure.configurables.android.modules

import com.android.tools.idea.gradle.structure.configurables.NamedContainerConfigurableBase
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsSigningConfig
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer

class SigningConfigsConfigurable(
  val module: PsAndroidModule,
  val context: PsContext
) : NamedContainerConfigurableBase<PsSigningConfig>("Signing Configs") {
  override fun getChildrenModels(): Collection<PsSigningConfig> = module.signingConfigs
  override fun createChildConfigurable(model: PsSigningConfig): NamedConfigurable<PsSigningConfig> =
    SigningConfigConfigurable(model, context).also { Disposer.register(this, it) }
  override fun onChange(disposable: Disposable, listener: () -> Unit) = module.signingConfigs.onChange(disposable, listener)
  override fun dispose() = Unit
}
