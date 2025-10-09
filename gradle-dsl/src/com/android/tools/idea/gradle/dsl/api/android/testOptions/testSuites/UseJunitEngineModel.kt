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
package com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyCollectorDependencyModel

interface UseJunitEngineModel : GradleBlockModel {
  fun inputs(): ResolvedPropertyModel
  fun addInput(input: String): ResolvedPropertyModel
  fun includeEngines(): ResolvedPropertyModel
  fun addIncludeEngine(engine: String): ResolvedPropertyModel
  fun enginesDependencies(): List<DependencyCollectorDependencyModel>
  fun addEngineDependency(compactNotation: String)
  fun addEngineDependency(reference: ReferenceTo)
}
