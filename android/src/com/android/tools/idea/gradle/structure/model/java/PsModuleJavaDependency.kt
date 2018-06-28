/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.java

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedDependency

class PsDeclaredModuleJavaDependency(
  parent: PsJavaModule,
  override val parsedModel: ModuleDependencyModel
) : PsJavaDependency(parent), PsModuleDependency, PsDeclaredDependency {
  override val name: String get() = parsedModel.name()
  override val isDeclared: Boolean = true
  override val joinedConfigurationNames: String get() = parsedModel.configurationName()
  override fun toText(type: TextType): String = name
  override val gradlePath: String get() = parsedModel.path().forceString()
  override val configurationName: String get() = parsedModel.configurationName()
}

class PsResolvedModuleJavaDependency(
  parent: PsJavaModule,
  override val gradlePath: String,
  scope: String,
  private val targetModule: PsModule,
  override val declaredDependencies: List<PsDeclaredModuleJavaDependency>
  ) : PsJavaDependency(parent), PsModuleDependency, PsResolvedDependency {
  override val name: String get() = targetModule.name
  override val isDeclared: Boolean get() = declaredDependencies.isNotEmpty()
  override val joinedConfigurationNames: String = scope
  override fun toText(type: TextType): String = name
  override fun getParsedModels(): List<DependencyModel> = declaredDependencies.map { it.parsedModel }
}