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

import com.android.builder.model.ProductFlavor
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.ApiVersion
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.ClassField
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.VariantConfig
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toNew
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import java.io.File

data class NewVariantConfig(
  override val name: String,
  override val resValues: Map<String, ClassField>,
  override val proguardFiles: Collection<File>,
  override val consumerProguardFiles: Collection<File>,
  override val manifestPlaceholders: Map<String, String>,
  override val isDebuggable: Boolean,
  override val applicationId: String?,
  override val versionCode: Int?,
  override val versionName: String?,
  override val minSdkVersion: ApiVersion?,
  override val targetSdkVersion: ApiVersion?,
  override val resourceConfigurations: Collection<String>
) : VariantConfig {
  constructor(mergedFlavor: ProductFlavor, isDebuggable: Boolean) : this(
    mergedFlavor.name,
    mergedFlavor.resValues.mapValues {
      NewClassField(it.value)
    },
    mergedFlavor.proguardFiles,
    mergedFlavor.consumerProguardFiles,
    mergedFlavor.manifestPlaceholders.mapValues { it.value.toString() },
    isDebuggable,
    mergedFlavor.applicationId,
    mergedFlavor.versionCode,
    mergedFlavor.versionName,
    mergedFlavor.minSdkVersion?.toNew(),
    mergedFlavor.targetSdkVersion?.toNew(),
    mergedFlavor.resourceConfigurations
  )

  constructor(proto: VariantProto.VariantConfig, converter: PathConverter) : this(
    proto.name,
    proto.resValuesMap.mapValues { NewClassField(it.value) },
    proto.proguardFilesList.map { converter.fileFromProto(it) },
    proto.consumerProguardFilesList.map { converter.fileFromProto(it) },
    proto.manifestPlaceholdersMap,
    proto.debuggable,
    if (proto.hasApplicationId()) proto.applicationId else null,
    if (proto.hasVersionCode()) proto.versionCode else null,
    if (proto.hasVersionName()) proto.versionName else null,
    if (proto.hasMinSdkVersion()) NewApiVersion(proto.minSdkVersion) else null,
    if (proto.hasTargetSdkVersion()) NewApiVersion(proto.targetSdkVersion) else null,
    proto.resourceConfigurationsList
  )
}