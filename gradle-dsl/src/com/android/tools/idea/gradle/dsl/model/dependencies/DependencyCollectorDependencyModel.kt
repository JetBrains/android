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
package com.android.tools.idea.gradle.dsl.model.dependencies

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement

/**
 * Represents a dependency added via the Gradle [DependencyCollector] interface.
 *
 * @property dslElement The [GradleDslElement] representing the dependency in the DSL.
 * @property strategy The [NotationStrategy] used to extract information from the dependency.
 * @property isVersionCatalogDependency True if this dependency is defined in a version catalog, false otherwise.
 */
class DependencyCollectorDependencyModel(
  val dslElement: GradleDslElement,
  val strategy: NotationStrategy,
  val isVersionCatalogDependency: Boolean,
) {
  /** Returns an [ArtifactDependencySpec] representing the dependency. */
  fun getSpec(): ArtifactDependencySpec {
    return ArtifactDependencySpecImpl(
      strategy.name().toString(),
      strategy.group().toString(),
      strategy.version().toString(),
      strategy.classifier().toString(),
      strategy.extension().toString(),
    )
  }

  /** Returns the compact notation of the dependency. */
  fun compactNotation(): String {
    return getSpec().compactNotation()
  }
}
