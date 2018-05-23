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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant

import com.android.ide.common.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.ArtifactSourceProvider
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Dependencies
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.JavaArtifact
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import java.io.File

data class NewJavaArtifact(
  // Inheritance is not used because Kotlin does not support open data classes.
  override val name: String,
  override val compileTaskName: String,
  override val assembleTaskName: String,
  override val dependencies: Dependencies,
  override val mergedSourceProvider: ArtifactSourceProvider,
  override val ideSetupTaskNames: Collection<String>,
  // ^ BaseArtifact values ^
  override val mockablePlatformJar: File?
) : JavaArtifact {
  constructor(oldJavaArtifact: IdeJavaArtifact, artifactSourceProviderFactory: NewArtifactSourceProviderFactory) : this(
    oldJavaArtifact.name,
    oldJavaArtifact.compileTaskName,
    oldJavaArtifact.assembleTaskName,
    NewDependencies(oldJavaArtifact.level2Dependencies, listOf()),
    artifactSourceProviderFactory.build(oldJavaArtifact),
    oldJavaArtifact.ideSetupTaskNames.toList(),
    oldJavaArtifact.mockablePlatformJar
  )

  constructor(proto: VariantProto.JavaArtifact, converter: PathConverter) : this(
    proto.baseArtifact.name,
    proto.baseArtifact.compileTaskName,
    proto.baseArtifact.assembleTaskName,
    NewDependencies(proto.baseArtifact.dependencies),
    NewArtifactSourceProvider(proto.baseArtifact.mergedSourceProvider, converter),
    proto.baseArtifact.ideSetupTaskNameList,
    null // null because it's not cached
  )
}
