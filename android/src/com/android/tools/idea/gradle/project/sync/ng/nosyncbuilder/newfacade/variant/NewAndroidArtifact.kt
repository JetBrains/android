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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldAndroidArtifact
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toNew
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import java.io.File

data class NewAndroidArtifact(
  override val sourceGenTaskName: String,
  override val signingConfigName: String?,
  override val isSigned: Boolean,
  override val applicationId: String,
  override val abiFilters: Collection<String>,
  override val instantRun: InstantRun,
  override val additionalRuntimeApks: Collection<File>,
  override val testOptions: TestOptions?,
  // Inheritance is not used because Kotlin does not support open data classes.
  // BaseArtifact values:
  override val name: String,
  override val compileTaskName: String,
  override val assembleTaskName: String,
  override val dependencies: Dependencies,
  override val mergedSourceProvider: ArtifactSourceProvider,
  override val ideSetupTaskNames: Collection<String>,
  override val instrumentedTestTaskName: String?,
  override val bundleTaskName: String?,
  override val apkFromBundleTaskName: String?
) : AndroidArtifact {
  constructor(oldAndroidArtifact: OldAndroidArtifact, artifactSourceProviderFactory: NewArtifactSourceProviderFactory) : this(
    oldAndroidArtifact.sourceGenTaskName,
    oldAndroidArtifact.signingConfigName,
    oldAndroidArtifact.isSigned,
    oldAndroidArtifact.applicationId,
    oldAndroidArtifact.abiFilters.orEmpty(),
    NewInstantRun(oldAndroidArtifact.instantRun),
    oldAndroidArtifact.additionalRuntimeApks,
    oldAndroidArtifact.testOptions?.toNew(),
    oldAndroidArtifact.name,
    oldAndroidArtifact.compileTaskName,
    oldAndroidArtifact.assembleTaskName,
    NewDependencies(oldAndroidArtifact.getLevel2Dependencies(), oldAndroidArtifact.nativeLibraries.orEmpty()),
    artifactSourceProviderFactory.build(oldAndroidArtifact),
    oldAndroidArtifact.ideSetupTaskNames.toList(),
    oldAndroidArtifact.instrumentedTestTaskName,
    oldAndroidArtifact.bundleTaskName,
    oldAndroidArtifact.apkFromBundleTaskName
  )

  constructor(proto: VariantProto.AndroidArtifact, converter: PathConverter) : this(
    proto.sourceGenTaskName,
    if (proto.hasSigningConfigName()) proto.signingConfigName else null,
    proto.signed,
    proto.applicationId,
    proto.abiFiltersList,
    NewInstantRun(proto.instantRun, converter),
    proto.additionalRuntimeApksList.map { converter.fileFromProto(it) },
    if (proto.hasTestOptions()) NewTestOptions(proto.testOptions) else null,
    proto.baseArtifact.name,
    proto.baseArtifact.compileTaskName,
    proto.baseArtifact.assembleTaskName,
    NewDependencies(proto.baseArtifact.dependencies),
    NewArtifactSourceProvider(proto.baseArtifact.mergedSourceProvider, converter),
    proto.baseArtifact.ideSetupTaskNameList,
    if (proto.hasInstrumentedTestTaskName()) proto.instrumentedTestTaskName else null,
    if (proto.hasBundleTaskName()) proto.bundleTaskName else null,
    if (proto.hasApkFromBundleTaskName()) proto.apkFromBundleTaskName else null
  )
}
