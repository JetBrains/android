/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.android.model.android

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleDeclarativeBuildModel
import com.android.tools.idea.gradle.dsl.android.api.android.AndroidDeclarativeModel
import com.android.tools.idea.gradle.dsl.android.api.android.AndroidModel
import com.android.tools.idea.gradle.dsl.android.api.android.KmpAndroidModel
import com.android.tools.idea.gradle.dsl.android.api.android.KmpAndroidModel.Companion.KMP_ANDROID_MINIMUM_AGP_VERSION
import com.android.tools.idea.gradle.dsl.api.kotlin.KotlinModel
import com.android.tools.idea.gradle.dsl.model.BlockModelBuilder
import com.android.tools.idea.gradle.dsl.model.BlockModelProvider
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.android.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.android.parser.android.KmpAndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint

class AndroidBlockModelProvider : BlockModelProvider<GradleBuildModel, GradleBuildFile> {
  override val parentClass = GradleBuildModel::class.java
  override val parentDslClass = GradleBuildFile::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, GradleBuildFile>> = when (kind) {
    GradleDslNameConverter.Kind.DECLARATIVE -> emptyList()
    else -> listOf(object : BlockModelBuilder<AndroidModel, GradleBuildFile> {
      override fun modelClass() = AndroidModel::class.java
      override fun create(parent: GradleBuildFile) = AndroidModelImpl(parent.ensurePropertyElement(AndroidDslElement.ANDROID))
    })
  }

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> = when (kind) {
    GradleDslNameConverter.Kind.DECLARATIVE -> emptyMap()
    else -> mapOf("android" to AndroidDslElement.ANDROID)
  }
}

fun GradleBuildModel.android() = when {
  this is GradleDeclarativeBuildModel -> getModel(AndroidDeclarativeModel::class.java)
  else -> getModel(AndroidModel::class.java)
}

class KmpAndroidBlockModelProvider : BlockModelProvider<KotlinModel, KotlinDslElement> {
  override val parentClass = KotlinModel::class.java
  override val parentDslClass = KotlinDslElement::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind):
    List<BlockModelBuilder<*, KotlinDslElement>> = listOf(
    object : BlockModelBuilder<KmpAndroidModel, KotlinDslElement> {
      override fun modelClass() = KmpAndroidModel::class.java
      override fun create(parent: KotlinDslElement) : KmpAndroidModel {
        // If block already exists, use it.
        parent.getPropertyElement(KmpAndroidDslElement.KMP_ANDROID_LIBRARY)?.let {
          return KmpAndroidModelImpl(it)
        }
        parent.getPropertyElement(KmpAndroidDslElement.KMP_ANDROID)?.let {
          return KmpAndroidModelImpl(it)
        }

        // Neither exists. We need to decide which one to create by default.
        val agpVersionConstraint = VersionConstraint.agpFrom(KMP_ANDROID_MINIMUM_AGP_VERSION)
        val agpVersion = parent.dslFile.context.agpVersion

        val agpCompatibility = agpVersionConstraint.isOkWith(agpVersion)

        val description = if (agpCompatibility) {
          KmpAndroidDslElement.KMP_ANDROID
        } else {
          KmpAndroidDslElement.KMP_ANDROID_LIBRARY
        }

        return KmpAndroidModelImpl(parent.ensurePropertyElement(description))
      }
    })

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> {
    return mapOf(
      "android" to KmpAndroidDslElement.KMP_ANDROID,
      "androidLibrary" to KmpAndroidDslElement.KMP_ANDROID_LIBRARY,
      )
  }
}

fun KotlinModel.android() = getModel(KmpAndroidModel::class.java)