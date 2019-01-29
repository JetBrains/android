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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto

/** The base information for all generated artifacts */
interface BaseArtifact {
  /** The name of the artifact. */
  val name: String
  /** The name of the task used to compile the code. */
  val compileTaskName: String
  /** The name of the task used to generate the artifact output(s). */
  val assembleTaskName: String
  /** All dependencies of the artifact. */
  val dependencies: Dependencies
  val mergedSourceProvider: ArtifactSourceProvider
  /**
   * Names of tasks that need to be run when setting up the IDE project.
   *
   * All things which IDE needs to know (e.g. generated source files) should be in place after run of these tasks.
   */
  val ideSetupTaskNames: Collection<String>
}

// This is declared outside of the class because member function toProto is declared in BaseArtifact's children
// but proto does not support inheritance so it cannot be overloaded.
fun BaseArtifact.toProto(converter: PathConverter) = VariantProto.BaseArtifact.newBuilder()
  .setName(name)
  .setCompileTaskName(compileTaskName)
  .setAssembleTaskName(assembleTaskName)
  .setDependencies(dependencies.toProto())
  .addAllIdeSetupTaskName(ideSetupTaskNames)
  .setMergedSourceProvider(mergedSourceProvider.toProto(converter))
  .build()!!
