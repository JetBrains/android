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

/**
 * A build Variant.
 *
 * This is the combination of a Build Type and 0+ Product Flavors (exactly one for each existing Flavor Dimension).
 *
 * The output of a Variant contains 1-3 items:
 * - [mainArtifact] (always present)
 * - [androidTestArtifact]
 * - [unitTestArtifact]
 */
interface Variant {
  /** The name of the variant. It is made up of the build type and flavors (if applicable) */
  val name: String
  /** The display name for the variant. It is made up of the build type and flavors (if applicable). */
  val displayName: String
  /** The main artifact for this variant. */
  val mainArtifact: AndroidArtifact
  /** The instrumented test artifact for this variant. */
  val androidTestArtifact: AndroidArtifact?
  /** The java unit test artifact for this variant. */
  val unitTestArtifact: JavaArtifact?
  /**
   * The result of the merge of all the flavors, selected build type (only for [VariantConfig.isDebuggable] and of the main default config.
   * If no flavors are defined then this is assembled from the default config and selected build type only.
   */
  val variantConfig: VariantConfig
  /**
   * The list of target projects and the variants that this variant is testing.
   * This is specified for the test only variants (ones using the test plugin).
   */
  val testedTargetVariants: Collection<TestedTargetVariant>

  fun toProto(converter: PathConverter): VariantProto.Variant {
    val minimalProto = VariantProto.Variant.newBuilder()
      .setName(name)
      .setDisplayName(displayName)
      .setMainArtifact(mainArtifact.toProto(converter))
      .setVariantConfig(variantConfig.toProto(converter))
      .addAllTestedTargetVariants(testedTargetVariants.map { it.toProto() })

    androidTestArtifact?.let { minimalProto.androidTestArtifact = androidTestArtifact!!.toProto(converter) }
    unitTestArtifact?.let { minimalProto.unitTestArtifact = unitTestArtifact!!.toProto(converter) }

    return minimalProto.build()!!
  }
}
