// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors

import com.android.tools.idea.gradle.structure.configurables.NamedContainerConfigurableBase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsFlavorDimension
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer

class ProductFlavorsConfigurable(
  val module: PsAndroidModule
) : NamedContainerConfigurableBase<PsFlavorDimension>("Flavor Dimensions") {
  override fun getChildrenModels(): Collection<PsFlavorDimension> = module.flavorDimensions
  override fun createChildConfigurable(model: PsFlavorDimension): NamedConfigurable<PsFlavorDimension> =
    FlavorDimensionConfigurable(module, model).also { Disposer.register(this, it) }
  override fun onChange(disposable: Disposable, listener: () -> Unit) = module.flavorDimensions.onChange(disposable, listener)
  override fun dispose() = Unit
}
