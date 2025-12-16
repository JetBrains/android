/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.internal

import com.android.SdkConstants
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeAbi
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeModule
import com.android.tools.idea.gradle.model.ndk.v2.IdeNativeVariant
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.model.V2NdkModel
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File


fun ProjectDumper.dumpNdkIdeModel(project: Project) {
  nest(File(project.basePath!!), "PROJECT") {
    ModuleManager.getInstance(project).modules.sortModules().forEach { module ->
      head("MODULE") { module.name }
      nest {
        NdkModuleModel.get(module)?.let { it ->
          dumpNdkModuleModel(it)
        }
      }
    }
  }
}

fun ProjectDumper.dumpNdkModuleModel(ndkModuleModel: NdkModuleModel) {
  val ndkModel = ndkModuleModel.ndkModel
  prop("SelectedVariantName") { ndkModuleModel.selectedVariant }
  prop("SelectedAbiName") { ndkModuleModel.selectedAbi }
  if (ndkModel is V2NdkModel) dump(ndkModel.nativeModule)
}

private fun ProjectDumper.dump(nativeModule: IdeNativeModule) {
  prop("Name") { nativeModule.name }
  prop("NativeBuildSystem") { nativeModule.nativeBuildSystem.toString() }
  prop("NdkVersion") {
    when (nativeModule.ndkVersion) {
      nativeModule.defaultNdkVersion, SdkConstants.NDK_DEFAULT_VERSION -> "<DEFAULT_NDK_VERSION>"
      else -> nativeModule.ndkVersion
    }
  }
  prop("ExternalNativeBuildFile") { nativeModule.externalNativeBuildFile.path.toPrintablePath() }
  if (nativeModule.variants.isNotEmpty()) {
    head("Variants")
      nest  {
        nativeModule.variants.forEach {
          dump(it)
        }
      }
  }
}

private fun ProjectDumper.dump(nativeAbi: IdeNativeAbi, variantName: String? = null) {
  head("NativeAbi")
    nest {
      prop("Name") { nativeAbi.name }
      prop("SourceFlagsFile") { nativeAbi.sourceFlagsFile.normalizeCxxPath(variantName).toPrintablePath() }
      prop("SymbolFolderIndexFile") { nativeAbi.symbolFolderIndexFile.normalizeCxxPath(variantName).toPrintablePath() }
      prop("BuildFileIndexFile") { nativeAbi.buildFileIndexFile.normalizeCxxPath(variantName).toPrintablePath() }
      prop("AdditionalProjectFilesIndexFile") {
        nativeAbi.additionalProjectFilesIndexFile?.normalizeCxxPath(variantName)?.toPrintablePath()
      }
    }
}

private fun ProjectDumper.dump(nativeVariant: IdeNativeVariant) {
  head("NativeVariant")
    nest {
      prop("Name") { nativeVariant.name }
    }
  if (nativeVariant.abis.isNotEmpty()) {
    head("ABIs")
      nest {
        nativeVariant.abis.forEach {
          dump(it, nativeVariant.name)
        }
      }
  }
}

