/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.crashlytics

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.crashlytics.CrashlyticsModel
import com.android.tools.idea.gradle.dsl.api.crashlytics.FirebaseCrashlyticsModel
import com.android.tools.idea.gradle.dsl.model.BlockModelBuilder
import com.android.tools.idea.gradle.dsl.model.BlockModelProvider
import com.android.tools.idea.gradle.dsl.model.GradleBlockModelMap
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypeDslElement
import com.android.tools.idea.gradle.dsl.parser.crashlytics.CrashlyticsDslElement.CRASHLYTICS
import com.android.tools.idea.gradle.dsl.parser.crashlytics.FirebaseCrashlyticsDslElement.FIREBASE_CRASHLYTICS
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription

fun GradleBuildModel.crashlytics() = getModel(CrashlyticsModel::class.java)

class CrashlyticsBlockModelProvider : BlockModelProvider<GradleBuildModel, GradleDslFile> {
  override val parentClass = GradleBuildModel::class.java
  override val parentDslClass = GradleDslFile::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, GradleDslFile>> = listOf(
    object : BlockModelBuilder<CrashlyticsModel, GradleDslFile> {
      override fun modelClass() = CrashlyticsModel::class.java
      override fun create(parent: GradleDslFile) = CrashlyticsModelImpl(parent.ensurePropertyElement(CRASHLYTICS))
    }
  )

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> =
    mapOf("crashlytics" to CRASHLYTICS)
}

fun BuildTypeModel.firebaseCrashlytics() = getModel(FirebaseCrashlyticsModel::class.java)

class FirebaseCrashlyticsBlockModelProvider : BlockModelProvider<BuildTypeModel, BuildTypeDslElement> {
  override val parentClass = BuildTypeModel::class.java
  override val parentDslClass = BuildTypeDslElement::class.java

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, BuildTypeDslElement>> = listOf(
    object : BlockModelBuilder<FirebaseCrashlyticsModel, BuildTypeDslElement> {
      override fun modelClass() = FirebaseCrashlyticsModel::class.java
      override fun create(parent: BuildTypeDslElement) = FirebaseCrashlyticsModelImpl(parent.ensurePropertyElement(FIREBASE_CRASHLYTICS))
    }
  )

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> =
    mapOf("firebaseCrashlytics" to FIREBASE_CRASHLYTICS)
}