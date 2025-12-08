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
package com.android.tools.idea.gradle.dsl.model.android

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.model.GradleDeclarativeBuildModelProvider
import com.android.tools.idea.gradle.dsl.api.android.AndroidDeclarativeModel
import com.android.tools.idea.gradle.dsl.api.android.AndroidSoftwareTypesModel
import com.android.tools.idea.gradle.dsl.model.BlockModelBuilder
import com.android.tools.idea.gradle.dsl.model.BlockModelProvider
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.api.GradleDeclarativeBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleDeclarativeSettingsModel
import com.android.tools.idea.gradle.dsl.model.GradleDeclarativeSettingsModelProvider
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.android.tools.idea.gradle.dsl.parser.settings.DefaultsDslElement

class AndroidDeclarativeBlockModelProvider : BlockModelProvider<GradleBuildModel, GradleBuildFile> {
  override val parentClass = GradleBuildModel::class.java
  override val parentDslClass = GradleBuildFile::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, GradleBuildFile>> = when (kind) {
    GradleDslNameConverter.Kind.DECLARATIVE -> listOf(object : BlockModelBuilder<AndroidDeclarativeModel, GradleBuildFile> {
      override fun modelClass() = AndroidDeclarativeModel::class.java
      override fun create(parent: GradleBuildFile) = declarativeBuilder(parent)
    })
    else -> emptyList()
  }

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> = when (kind) {
    GradleDslNameConverter.Kind.DECLARATIVE -> mapOf(
      "androidApp" to AndroidDslElement.ANDROID_APP,
      "androidLibrary" to AndroidDslElement.ANDROID_LIBRARY
    )
    else -> emptyMap()
  }

  private fun declarativeBuilder(file: GradleBuildFile): AndroidDeclarativeModel {
    file.getPropertyElement(AndroidDslElement.ANDROID_APP)?.let { element ->
      return AndroidDeclarativeModelImpl(element)
    }
    file.getPropertyElement(AndroidDslElement.ANDROID_LIBRARY)?.let { element ->
      return AndroidDeclarativeModelImpl(element)
    }
    throw IllegalStateException("Cannot create android[App|Library] dsl element")
  }
}

class AndroidGradleDeclarativeBuildModelProvider : GradleDeclarativeBuildModelProvider {
  override fun createModel(dslFile: GradleBuildFile): GradleDeclarativeBuildModel? = if (dslFile.parser.kind == GradleDslNameConverter.Kind.DECLARATIVE) AndroidGradleDeclarativeBuildModelImpl(
    dslFile)
  else null
}

class AndroidGradleDeclarativeSettingsModelProvider : GradleDeclarativeSettingsModelProvider {
  override fun createModel(dslFile: GradleSettingsFile): GradleDeclarativeSettingsModel? =
    if (dslFile.parser.kind == GradleDslNameConverter.Kind.DECLARATIVE) AndroidGradleDeclarativeSettingsModelImpl(dslFile)
    else null
}

class AndroidDefaultDslModelBlockProvider : BlockModelProvider<AndroidSoftwareTypesModel, DefaultsDslElement>{
  override val parentClass: Class<AndroidSoftwareTypesModel> = AndroidSoftwareTypesModel::class.java
  override val parentDslClass: Class<DefaultsDslElement> = DefaultsDslElement::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, DefaultsDslElement>> {
    // The blocks defined in the elementsMap (e.g 'androidApp') are not represented by their own
    // specific dsl model hence no model builders are required here.
    return emptyList()
  }
  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> = when (kind) {
    GradleDslNameConverter.Kind.DECLARATIVE -> mapOf(
      "androidApp" to AndroidDslElement.ANDROID_APP,
      "androidLibrary" to AndroidDslElement.ANDROID_LIBRARY
    )
    else -> emptyMap()
  }
}
