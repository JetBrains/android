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
import com.android.tools.idea.gradle.dsl.model.BlockModelBuilder
import com.android.tools.idea.gradle.dsl.model.BlockModelProvider
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.android.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription

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