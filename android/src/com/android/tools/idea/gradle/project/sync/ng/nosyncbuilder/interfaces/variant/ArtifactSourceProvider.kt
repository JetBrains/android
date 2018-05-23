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
import java.io.File

/**
 * Contains all of artifact-specific source/resource folders.
 *
 * Source sets should be resolved in the following order:
 * build variant > build type > multiple product flavor > all single product flavors > default
 * */
interface ArtifactSourceProvider {
  /** Specific to the variant. May be null if there is no flavors as the "variant" is equal to the build type. */
  val variantSourceSet: AndroidSourceSet?
  /** Specific to the build type. */
  val buildTypeSourceSet: AndroidSourceSet
  /** Specific to the flavor combination.
   *
   * For instance if there are 2 dimensions, then this would be Flavor1Flavor2, and would be
   * common to all variant using these two flavors and any of the build type.
   *
   * This may be null if there is less than 2 flavors.
   */
  val multiFlavorSourceSet: AndroidSourceSet?
  val singleFlavorSourceSets: Collection<AndroidSourceSet>
  val defaultSourceSet: AndroidSourceSet
  val classesFolder: File
  val additionalClassesFolders: Collection<File>
  val javaResourcesFolder: File
  val generatedSourceFolders: Collection<File>
  val generatedResourceFolders: Collection<File>

  fun toProto(converter: PathConverter): VariantProto.ArtifactSourceProvider {
    val minimalProto = VariantProto.ArtifactSourceProvider.newBuilder()
      .setBuildTypeSourceSet(buildTypeSourceSet.toProto(converter))
      .addAllSingleFlavorSourceSets(singleFlavorSourceSets.map { it.toProto(converter) })
      .setDefaultSourceSet(defaultSourceSet.toProto(converter))
      .setClassesFolder(converter.fileToProto(classesFolder))
      .addAllAdditionalClassesFolders(additionalClassesFolders.map { converter.fileToProto(it) })
      .setJavaResourcesFolder(converter.fileToProto(javaResourcesFolder))
      .addAllGeneratedSourceFolders(generatedSourceFolders.map { converter.fileToProto(it) })
      .addAllGeneratedResourceFolders(generatedResourceFolders.map { converter.fileToProto(it) })

    variantSourceSet?.let { minimalProto.setVariantSourceSet(variantSourceSet!!.toProto(converter)) }
    multiFlavorSourceSet?.let { minimalProto.setMultiFlavorSourceSet(multiFlavorSourceSet!!.toProto(converter))}

    return minimalProto.build()!!
  }
}
