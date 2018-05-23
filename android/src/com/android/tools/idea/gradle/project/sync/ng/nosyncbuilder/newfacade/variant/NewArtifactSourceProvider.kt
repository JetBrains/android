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

import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProvider
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.AndroidSourceSet
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.ArtifactSourceProvider
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import java.io.File

data class NewArtifactSourceProvider(
  override val variantSourceSet: AndroidSourceSet?,
  override val buildTypeSourceSet: AndroidSourceSet,
  override val multiFlavorSourceSet: AndroidSourceSet?,
  override val singleFlavorSourceSets: Collection<AndroidSourceSet>,
  override val defaultSourceSet: AndroidSourceSet,
  override val classesFolder: File,
  override val additionalClassesFolders: Collection<File>,
  override val javaResourcesFolder: File,
  override val generatedSourceFolders: Collection<File>,
  override val generatedResourceFolders: Collection<File>
) : ArtifactSourceProvider {
  constructor(
    oldBaseArtifact: OldBaseArtifact,
    buildTypeSourceProvider: SourceProvider,
    productFlavorSourceProviders: Collection<SourceProvider>,
    defaultSourceProvider: SourceProvider
  ) : this(
    oldBaseArtifact.variantSourceProvider?.toAndroidSourceSet(),
    buildTypeSourceProvider.toAndroidSourceSet(),
    oldBaseArtifact.multiFlavorSourceProvider?.toAndroidSourceSet(),
    productFlavorSourceProviders.map { it.toAndroidSourceSet() },
    defaultSourceProvider.toAndroidSourceSet(),
    oldBaseArtifact.classesFolder,
    oldBaseArtifact.additionalClassesFolders,
    oldBaseArtifact.javaResourcesFolder,
    oldBaseArtifact.generatedSourceFolders,
    (oldBaseArtifact as? OldAndroidArtifact)?.generatedResourceFolders.orEmpty()
  )

  constructor(proto: VariantProto.ArtifactSourceProvider, converter: PathConverter) : this(
    if (proto.hasVariantSourceSet()) NewAndroidSourceSet(proto.variantSourceSet, converter) else null,
    NewAndroidSourceSet(proto.buildTypeSourceSet, converter),
    if (proto.hasMultiFlavorSourceSet()) NewAndroidSourceSet(proto.multiFlavorSourceSet, converter) else null,
    proto.singleFlavorSourceSetsList.map { NewAndroidSourceSet(it, converter) },
    NewAndroidSourceSet(proto.defaultSourceSet, converter),
    converter.fileFromProto(proto.classesFolder),
    proto.additionalClassesFoldersList.map { converter.fileFromProto(it) },
    converter.fileFromProto(proto.javaResourcesFolder),
    proto.generatedSourceFoldersList.map { converter.fileFromProto(it) },
    proto.generatedResourceFoldersList.map { converter.fileFromProto(it) }
  )
}

class NewArtifactSourceProviderFactory(oldAndroidProject: OldAndroidProject, oldVariant: OldVariant) {
  private val buildTypes: Collection<BuildTypeContainer> = oldAndroidProject.buildTypes
  private val productFlavors: Collection<ProductFlavorContainer> = oldAndroidProject.productFlavors
  private val defaultConfig = oldAndroidProject.defaultConfig
  private val buildTypeName = oldVariant.buildType
  private val productFlavorNames = oldVariant.productFlavors

  private val buildTypeSourceProvider = buildTypes.find { it.buildType.name == buildTypeName }!!.sourceProvider
  private val productFlavorSourceProviders = productFlavors
    .filter { it.productFlavor.name in productFlavorNames }
    .map { it.sourceProvider }

  fun build(oldBaseArtifact: OldBaseArtifact) = NewArtifactSourceProvider(
    oldBaseArtifact,
    buildTypeSourceProvider,
    productFlavorSourceProviders,
    defaultConfig.sourceProvider
  )
}
