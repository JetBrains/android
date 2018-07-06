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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.NativeLibrary
import com.android.builder.model.level2.DependencyGraphs
import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.AndroidArtifact
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.ClassField
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library.LegacyDependencyGraphsStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.stubs.DependenciesStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import java.io.File

open class LegacyAndroidArtifact(
  private val androidArtifact: AndroidArtifact,
  private val resValues: Map<String, ClassField>
) : OldAndroidArtifact, LegacyBaseArtifact(androidArtifact) {
  override fun isSigned(): Boolean = androidArtifact.isSigned
  override fun getSigningConfigName(): String? = androidArtifact.signingConfigName
  override fun getApplicationId(): String = androidArtifact.applicationId
  override fun getSourceGenTaskName(): String = androidArtifact.sourceGenTaskName
  override fun getAbiFilters(): Set<String>? = if (androidArtifact.abiFilters.isEmpty()) null else androidArtifact.abiFilters.toSet()
  override fun getInstantRun(): OldInstantRun = LegacyInstantRun(androidArtifact.instantRun)
  override fun getAdditionalRuntimeApks(): Collection<File> = androidArtifact.additionalRuntimeApks.toList()
  override fun getTestOptions(): OldTestOptions? = androidArtifact.testOptions?.toLegacy()
  override fun getResValues(): Map<String, OldClassField> = resValues.mapValues { it.value.toLegacy() }
  override fun getGeneratedResourceFolders(): Collection<File> = androidArtifact.mergedSourceProvider.generatedResourceFolders
  override fun getBundleTaskName(): String? = androidArtifact.bundleTaskName
  override fun getApkFromBundleTaskName(): String? = androidArtifact.apkFromBundleTaskName
  override fun getInstrumentedTestTaskName(): String? = androidArtifact.instrumentedTestTaskName
  override fun getNativeLibraries(): Collection<NativeLibrary> = listOf() // TODO support native libraries

  override fun getBuildConfigFields(): Map<String, OldClassField> = throw UnusedModelMethodException("getBuildConfigFields")
  @Deprecated("use post-build model", ReplaceWith("getVariantsBuildOutput()", "com.android.builder.model.ProjectBuildOutput"))
  override fun getOutputs(): Collection<AndroidArtifactOutput> = throw UnusedModelMethodException("getOutputs")

  override fun toString(): String = "LegacyAndroidArtifact{" +
                                    "isSigned=$isSigned," +
                                    "signingConfigNamer=$signingConfigName," +
                                    "applicationId=$applicationId," +
                                    "sourceGenTaskName=$sourceGenTaskName," +
                                    "abiFilters=$abiFilters," +
                                    "instantRun=$instantRun," +
                                    "additionalRuntimeApks=$additionalRuntimeApks," +
                                    "resValues=$resValues," +
                                    "generatedResourceFolders=$generatedResourceFolders," +
                                    "bundleTaskName=$bundleTaskName," +
                                    "apkFromBundleTaskName=$apkFromBundleTaskName," +
                                    "instrumentedTestTaskName=$instrumentedTestTaskName" +
                                    // TODO "nativeLibraries=$nativeLibraries," +
                                    "}"
}


class LegacyAndroidArtifactStub(private val androidArtifact: AndroidArtifact, resValues: Map<String, ClassField>)
  : LegacyAndroidArtifact(androidArtifact, resValues) {
  override fun getTestOptions(): OldTestOptions? = androidArtifact.testOptions?.toLegacyStub()
  override fun getDependencyGraphs(): DependencyGraphs = LegacyDependencyGraphsStub(
    androidArtifact.dependencies)
  override fun getDependencies(): OldDependencies = DependenciesStub()
  @Deprecated("use dependencies instead", ReplaceWith("getDependencies()"))
  override fun getCompileDependencies(): OldDependencies = dependencies

  override fun getBuildConfigFields(): Map<String, OldClassField> = mapOf()
}
