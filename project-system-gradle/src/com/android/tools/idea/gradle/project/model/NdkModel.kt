/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeModule
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeAbi
import com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem
import com.android.ide.common.repository.AgpVersion
import com.intellij.serialization.PropertyMapping
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.collections.component1
import kotlin.collections.component2

interface INdkModel {
  val features: NdkModelFeatures
  val allVariantAbis: Collection<VariantAbi>
  val syncedVariantAbis: Collection<VariantAbi>
  val symbolFolders: Map<VariantAbi, Set<File>>
  val buildFiles: Collection<File>
  val buildSystems: Collection<String>
  val defaultNdkVersion: String
  val ndkVersion: String
  val needsAbiSyncBeforeRun: Boolean
}

sealed class NdkModel : INdkModel

/**
 * Native information fetched from Android Gradle Plugin about this module using V2 API.
 *
 * Regardless of whether single varaint sync or full sync is used, the model contains the exact same [NativeModule] that contains an
 * overview of the native module.
 *
 * synced variant and ABIs would have the [NativeAbi.compileCommandsJsonFile], [NativeAbi.symbolFolderIndexFile], and
 * [NativeAbi.buildFileIndexFile] generated, containing detailed build information.
 */
data class V2NdkModel @PropertyMapping("agpVersion", "nativeModule") constructor(
  private val /* `val` declaration needed for serialization */ agpVersion: String,
  val nativeModule: IdeNativeModule
) : NdkModel() {

  @Transient
  override val features: NdkModelFeatures = NdkModelFeatures(AgpVersion.tryParse(agpVersion))

  @Transient
  val abiByVariantAbi: Map<VariantAbi, IdeNativeAbi> = nativeModule.variants.flatMap { variant ->
    variant.abis.map { abi ->
      VariantAbi(variant.name, abi.name) to abi
    }
  }.toMap()

  @Transient
  override val allVariantAbis: Collection<VariantAbi> = LinkedHashSet(abiByVariantAbi.keys.sortedBy { it.displayName })

  override val syncedVariantAbis: Collection<VariantAbi>
    get() = LinkedHashSet(
      abiByVariantAbi.entries.filter { (_, abi) -> abi.sourceFlagsFile.exists() }
        .map { (variantAbi, _) -> variantAbi }
        .sortedBy { it.displayName }
    )

  override val symbolFolders: Map<VariantAbi, Set<File>>
    get() = abiByVariantAbi.mapValues { (_, abi) ->
      abi.symbolFolderIndexFile.readIndexFile()
    }

  override val buildFiles: Collection<File> get() = abiByVariantAbi.values.flatMap { it.buildFileIndexFile.readIndexFile() }.toSet()

  @Transient
  override val buildSystems: Collection<String> = listOf(
    when (nativeModule.nativeBuildSystem) {
      NativeBuildSystem.CMAKE -> "cmake"
      NativeBuildSystem.NDK_BUILD -> "ndkBuild"
      NativeBuildSystem.NINJA -> "ninja"
    })

  @Transient
  override val defaultNdkVersion: String = nativeModule.defaultNdkVersion

  @Transient
  override val ndkVersion: String = nativeModule.ndkVersion

  override val needsAbiSyncBeforeRun: Boolean get() = false
}

private fun File.readIndexFile(): Set<File> = when {
  this.isFile -> this.readLines(StandardCharsets.UTF_8).map { File(it) }.toSet()
  else -> emptySet()
}
