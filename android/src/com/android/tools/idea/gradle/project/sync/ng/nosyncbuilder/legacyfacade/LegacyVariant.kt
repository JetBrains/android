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

import com.android.builder.model.ProductFlavor
import com.android.builder.model.TestedTargetVariant
import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Variant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldAndroidArtifact
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldJavaArtifact
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldVariant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toLegacy

open class LegacyVariant(private val variant: Variant) : OldVariant {
  override fun getName(): String = variant.name
  override fun getDisplayName(): String = variant.displayName
  override fun getMainArtifact(): OldAndroidArtifact = LegacyAndroidArtifact(variant.mainArtifact, variant.variantConfig.resValues)
  override fun getExtraAndroidArtifacts(): Collection<OldAndroidArtifact> {
    return listOfNotNull(variant.androidTestArtifact?.toLegacy(variant.variantConfig.resValues))
  }

  override fun getExtraJavaArtifacts(): Collection<OldJavaArtifact> = listOfNotNull(variant.unitTestArtifact?.toLegacy())
  override fun getTestedTargetVariants(): Collection<TestedTargetVariant> {
    return variant.testedTargetVariants.map { LegacyTestedTargetVariant(it) }
  }

  override fun getMergedFlavor(): ProductFlavor = LegacyProductFlavor(variant.variantConfig)

  override fun getBuildType(): String = throw UnusedModelMethodException("getBuildType")
  override fun getProductFlavors(): List<String> = throw UnusedModelMethodException("getProductFlavors")

  override fun toString(): String = "LegacyBaseArtifact{" +
                                    "name=$name," +
                                    "displayName=$displayName," +
                                    "mainArtifact=$mainArtifact," +
                                    "extraAndroidArtifacts=$extraAndroidArtifacts," +
                                    "extraJavaArtifacts=$extraJavaArtifacts," +
                                    "testedTargetVariants=$testedTargetVariants," +
                                    "mergedFlavor=$mergedFlavor" +
                                    "}"
}

