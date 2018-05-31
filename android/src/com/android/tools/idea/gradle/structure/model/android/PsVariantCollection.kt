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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.PsModelCollection
import java.util.function.Consumer

internal class PsVariantCollection(parent: PsAndroidModule) : PsModelCollection<PsVariant> {
  private val variantsByName = mutableMapOf<String, PsVariant>()

  init {
    parent.gradleModel.androidProject.forEachVariant { ideVariant ->
      val productFlavors = mutableListOf<String>()
      for (productFlavorName in ideVariant.productFlavors) {
        val productFlavor = parent.findProductFlavor(productFlavorName)
        if (productFlavor != null) {
          productFlavors.add(productFlavor.name)
        }
        else {
          // TODO handle case when product flavor is not found.
        }
      }
      val buildType = ideVariant.buildType

      val variant = PsVariant(parent, ideVariant.name, buildType, productFlavors, ideVariant)
      variantsByName[ideVariant.name] = variant
    }
  }

  fun findElement(name: String): PsVariant? {
    return variantsByName[name]
  }

  override fun forEach(consumer: Consumer<PsVariant>) {
    variantsByName.values.forEach(consumer)
  }
}
