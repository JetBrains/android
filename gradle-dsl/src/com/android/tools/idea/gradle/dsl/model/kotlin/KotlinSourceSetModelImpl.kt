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
package com.android.tools.idea.gradle.dsl.model.kotlin

import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.kotlin.KotlinSourceSetModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.dependencies.ScriptDependenciesModelImpl
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement

class KotlinSourceSetModelImpl(
  dslElement: KotlinSourceSetDslElement
) : GradleDslBlockModel(dslElement), KotlinSourceSetModel {

  override fun name(): String = myDslElement.name


  override fun dependencies(): DependenciesModel {
    val dependenciesDslElement = myDslElement.ensurePropertyElement(DependenciesDslElement.DEPENDENCIES)
    return ScriptDependenciesModelImpl(dependenciesDslElement)
  }

  override fun removeDependencies() {
    DependenciesDslElement.DEPENDENCIES.name?.let {
      myDslElement.removeProperty(it)
    }
  }
}