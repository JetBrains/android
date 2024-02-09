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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * This is extension point to provide additional models for gradle DSL elements in build script
 * files. For example Studio implements this interface to provide `android`
 * [com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement] model.
 *
 * Such approach allows to keep DSL models along with Gradle plugins and plug them in once in use
 *
 * See [GradleBlockModelMap]
 */
@ApiStatus.Experimental
interface BlockModelProvider<ParentModel : GradleDslModel, ParentDsl : GradlePropertiesDslElement> {

  companion object {
    @JvmField
    val EP: ExtensionPointName<BlockModelProvider<*, *>> = ExtensionPointName.create("com.android.tools.idea.gradle.dsl.blockModelProvider")
  }

  val parentClass: Class<ParentModel>

  val parentDslClass: Class<ParentDsl>

  fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, ParentDsl>>

  fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>>
}


@ApiStatus.Experimental
interface BlockModelBuilder<M : GradleDslModel, P : GradlePropertiesDslElement> {
  fun modelClass(): Class<M>
  fun create(parent: P): M
}

