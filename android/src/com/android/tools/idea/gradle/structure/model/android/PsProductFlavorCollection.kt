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

import com.android.builder.model.ProductFlavor
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import java.util.function.Consumer

internal class PsProductFlavorCollection(private val parent: PsAndroidModule) : PsModelCollection<PsProductFlavor> {
  private val productFlavorsByName = mutableMapOf<String, PsProductFlavor>()

  init {
    val productFlavorsFromGradle = mutableMapOf<String, ProductFlavor>()
    for (container in parent.gradleModel.androidProject.productFlavors) {
      val productFlavor = container.productFlavor
      productFlavorsFromGradle[productFlavor.name] = productFlavor
    }

    val parsedModel = parent.parsedModel
    if (parsedModel != null) {
      val android = parsedModel.android()
      if (android != null) {
        val parsedProductFlavors = android.productFlavors()
        for (parsedProductFlavor in parsedProductFlavors) {
          val name = parsedProductFlavor.name()
          val fromGradle = productFlavorsFromGradle.remove(name)

          val model = PsProductFlavor(parent, fromGradle, parsedProductFlavor)
          productFlavorsByName[name] = model
        }
      }
    }

    if (!productFlavorsFromGradle.isEmpty()) {
      for (productFlavor in productFlavorsFromGradle.values) {
        val model = PsProductFlavor(parent, productFlavor, null)
        productFlavorsByName[productFlavor.name] = model
      }
    }
  }

  override fun forEach(consumer: Consumer<PsProductFlavor>) {
    productFlavorsByName.values.forEach(consumer)
  }

  fun findElement(name: String): PsProductFlavor? {
    return productFlavorsByName[name]
  }

  fun addNew(name: String): PsProductFlavor {
    assert(parent.parsedModel != null)
    val androidModel = parent.parsedModel!!.android()!!
    androidModel.addProductFlavor(name)
    val productFlavors = androidModel.productFlavors()
    val model = PsProductFlavor(parent, null, productFlavors.single { it.name() == name })
    productFlavorsByName[name] = model
    parent.isModified = true
    return model
  }

  fun remove(name: String) {
    assert(parent.parsedModel != null)
    val androidModel = parent.parsedModel!!.android()!!
    androidModel.removeProductFlavor(name)
    productFlavorsByName.remove(name)
    parent.isModified = true
  }
}
