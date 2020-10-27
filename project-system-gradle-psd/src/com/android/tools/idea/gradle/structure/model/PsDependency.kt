/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel

interface PsDeclaredDependency : PsBaseDependency {
  val parsedModel: DependencyModel
  val configurationName: String
  /**
   * @return a key which can be used to look up this (or semantically-equivalent) dependency using
   * - [PsDependencyCollection.findModuleDependencies]
   * - [PsDependencyCollection.findJarDependencies]
   * - [PsDependencyCollection.findLibraryDependencies]
   */
  fun toKey(): String
}

interface PsResolvedDependency : PsBaseDependency {
  val declaredDependencies: List<PsDeclaredDependency>
  fun getParsedModels(): List<DependencyModel> = declaredDependencies.map { it.parsedModel }
  override val joinedConfigurationNames: String get() = declaredDependencies.joinToString(separator = ", ") { it.configurationName }
}
