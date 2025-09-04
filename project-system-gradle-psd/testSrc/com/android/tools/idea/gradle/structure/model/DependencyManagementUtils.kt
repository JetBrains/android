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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue

fun <T> PsDeclaredDependencyCollection<*, T, *, *>.findLibraryDependency(
  compactNotation: String,
  configuration: String? = null
): List<T>?
  where T : PsDeclaredDependency,
        T : PsLibraryDependency =
  PsArtifactDependencySpec.create(compactNotation)?.let { spec ->
    findLibraryDependencies(
      spec.group,
      spec.name
    )
      .filter { it.spec.version == spec.version && it.configurationName == (configuration ?: it.configurationName) }
      .let { it.ifEmpty { null } }
  }

fun <T> PsResolvedDependencyCollection<*, *, T, *, *>.findLibraryDependency(compactNotation: String): List<T>?
  where T : PsResolvedDependency,
        T : PsLibraryDependency =
  PsArtifactDependencySpec.create(compactNotation)?.let { spec ->
    findLibraryDependencies(
      spec.group,
      spec.name
    )
      .filter { it.spec.version == spec.version }
      .let { it.ifEmpty { null } }
  }

fun List<PsResolvedDependency>?.testMatchingScopes(): List<String> =
  orEmpty().map { resolvedDependency -> resolvedDependency.getParsedModels().joinToString(":") { it.configurationName() } }

fun List<PsDeclaredDependency>?.testDeclaredScopes(): List<String> = orEmpty().map { it.parsedModel.configurationName() }

fun List<PsModel>?.testDeclared(): List<Boolean> = orEmpty().map { it.isDeclared }
fun List<PsResolvedLibraryDependency>?.testHasPromotedVersion(): List<Boolean> = orEmpty().map { it.hasPromotedVersion() }
fun <T : Any> T.asParsed() = ParsedValue.Set.Parsed(this, DslText.Literal)
