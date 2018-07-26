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

import com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.level2.Library
import com.android.ide.common.gradle.model.level2.IdeDependencies
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto

data class NewVariant(
  override val name: String,
  override val displayName: String,
  override val mainArtifact: AndroidArtifact,
  override val androidTestArtifact: AndroidArtifact?,
  override val unitTestArtifact: JavaArtifact?,
  override val variantConfig: VariantConfig,
  override val testedTargetVariants: Collection<TestedTargetVariant>
) : Variant {
  constructor(oldSelectedVariant: OldVariant, oldAndroidProject: OldAndroidProject) : this(
    oldSelectedVariant,
    NewArtifactSourceProviderFactory(oldAndroidProject, oldSelectedVariant),
    findIsDebuggable(oldAndroidProject.buildTypes, oldSelectedVariant.buildType)!!
  )

  constructor(oldSelectedVariant: OldVariant,
              artifactSourceProviderFactory: NewArtifactSourceProviderFactory,
              isDebuggable: Boolean) : this(
    oldSelectedVariant.name,
    oldSelectedVariant.displayName,
    NewAndroidArtifact(oldSelectedVariant.mainArtifact, artifactSourceProviderFactory),
    oldSelectedVariant.getAndroidTestArtifact()?.toNew(artifactSourceProviderFactory),
    oldSelectedVariant.getUnitTestArtifact()?.toNew(artifactSourceProviderFactory),
    NewVariantConfig(oldSelectedVariant.mergedFlavor, isDebuggable),
    oldSelectedVariant.testedTargetVariants.map { NewTestedTargetVariant(it) }
  )

  constructor(proto: VariantProto.Variant, converter: PathConverter) : this(
    proto.name,
    proto.displayName,
    NewAndroidArtifact(proto.mainArtifact, converter),
    if (proto.hasAndroidTestArtifact()) NewAndroidArtifact(proto.androidTestArtifact, converter) else null,
    if (proto.hasUnitTestArtifact()) NewJavaArtifact(proto.unitTestArtifact, converter) else null,
    NewVariantConfig(proto.variantConfig, converter),
    proto.testedTargetVariantsList.map { NewTestedTargetVariant(it) }
  )
}

/**
 * Returns whether build type with the given [name] is debuggable.
 * Null if there are no such build type.
 */
private fun findIsDebuggable(buildTypes: Collection<BuildTypeContainer>, name: String): Boolean? {
  for (buildType in buildTypes) {
    if (buildType.buildType.name == name)
      return buildType.buildType.isDebuggable
  }
  return null
}

fun OldVariant.getAndroidTestArtifact(): OldAndroidArtifact? = extraAndroidArtifacts.firstOrNull { it.isTestArtifact() }
fun OldVariant.getUnitTestArtifact(): OldJavaArtifact? = extraJavaArtifacts.firstOrNull { it.isTestArtifact() }

fun OldBaseArtifact.isTestArtifact(): Boolean = listOf(ARTIFACT_UNIT_TEST, ARTIFACT_ANDROID_TEST).contains(name)

private val emptyIdeDependencies = object: IdeDependencies {
  override fun getAndroidLibraries(): Collection<Library> = listOf()
  override fun getJavaLibraries(): Collection<Library> = listOf()
  override fun getModuleDependencies(): Collection<Library> = listOf()
}

fun OldBaseArtifact.getLevel2Dependencies(): IdeDependencies {
  // if it's empty IdeDependenciesFactory tries to use old dependencies model which is not supported in the new models
  if (dependencyGraphs.compileDependencies.isEmpty()) {
    return emptyIdeDependencies
  }
  return IdeDependenciesFactory().create(this, GradleVersion.parse("3.0"))
}

