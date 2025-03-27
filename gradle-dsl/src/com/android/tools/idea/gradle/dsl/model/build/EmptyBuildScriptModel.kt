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
package com.android.tools.idea.gradle.dsl.model.build

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.model.dependencies.EmptyDependenciesModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.EmptyExtModelImpl
import com.android.tools.idea.gradle.dsl.model.repositories.EmptyRepositoriesModelImpl
import com.android.tools.idea.gradle.dsl.parser.elements.EmptyGradleBlockModel

class EmptyBuildScriptModel: EmptyGradleBlockModel(), BuildScriptModel {
  override fun dependencies(): DependenciesModel {
    return EmptyDependenciesModelImpl()
  }

  override fun repositories(): RepositoriesModel {
    return EmptyRepositoriesModelImpl()
  }

  override fun removeRepositoriesBlocks() {
    throw UnsupportedOperationException("Call is not supported for Declarative")
  }

  override fun ext(): ExtModel {
    return EmptyExtModelImpl()
  }

}