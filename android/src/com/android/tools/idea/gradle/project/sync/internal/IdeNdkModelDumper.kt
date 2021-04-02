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

import com.android.ide.common.gradle.model.ndk.v1.IdeNativeAndroidProject
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeArtifact
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeFile
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeSettings
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeToolchain
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeVariantInfo
import com.android.ide.common.gradle.model.ndk.v2.IdeNativeAbi
import com.android.ide.common.gradle.model.ndk.v2.IdeNativeModule
import com.android.ide.common.gradle.model.ndk.v2.IdeNativeVariant
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.model.V1NdkModel
import com.android.tools.idea.gradle.project.model.V2NdkModel
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.io.File


fun ProjectDumper.dumpNdkIdeModel(project: Project) {
  nest(File(project.basePath!!), "PROJECT") {
    ModuleManager.getInstance(project).modules.sortedBy { it.name }.forEach { module ->
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
  prop("SelectedAbiName") { ndkModuleModel.selectedAbi }
  if (ndkModel is V2NdkModel) dump(ndkModel.nativeModule)
  if (ndkModel is V1NdkModel) dump(ndkModel.androidProject)
}

private fun ProjectDumper.dump(nativeModule: IdeNativeModule) {
  prop("Name") { nativeModule.name }
  prop("NativeBuildSystem") { nativeModule.nativeBuildSystem.toString() }
  prop("NdkVersion") {
    if (nativeModule.ndkVersion == nativeModule.defaultNdkVersion) "{DEFAULT_NDK_VERSION}" else nativeModule.ndkVersion
  }
  // This depends on the AGP version used for tests, which means the risk of having different values when ran on IDE or bazel.
  prop("DefaultNdkVersion") { "{DEFAULT_NDK_VERSION}" }
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

private fun ProjectDumper.dump(nativeAndroidProject: IdeNativeAndroidProject) {
  prop("ModelVersion") { nativeAndroidProject.modelVersion }
  prop("ApiVersion") { nativeAndroidProject.apiVersion.toString() }
  prop("Name") { nativeAndroidProject.name }
  prop("DefaultNdkVersion") { nativeAndroidProject.defaultNdkVersion }
  nativeAndroidProject.buildFiles.forEach { prop("BuildFiles") { it.path.toPrintablePath() } }
  nativeAndroidProject.buildSystems.forEach { prop("BuildSystems") { it } }
  if (nativeAndroidProject.variantInfos.isNotEmpty()) {
    head("VariantInfos")
      nest {
        nativeAndroidProject.variantInfos.forEach { (key, value) ->
          head(key)
          nest {
            dump(value)
          }
        }
      }
  }
  if (nativeAndroidProject.artifacts.isNotEmpty()) {
    nativeAndroidProject.artifacts.forEach {
      head("Artifacts")
        nest {
          dump(it)
        }
    }
  }
  if (nativeAndroidProject.toolChains.isNotEmpty()) {
    nativeAndroidProject.toolChains.forEach {
      head("ToolChains")
      nest {
        dump(it)
      }
    }
  }
  if (nativeAndroidProject.settings.isNotEmpty()) {
    nativeAndroidProject.settings.forEach {
      head("Settings")
      nest {
        dump(it)
      }
    }
  }
  if (nativeAndroidProject.fileExtensions.isNotEmpty()) {
    head("FileExtensions") // todo: check if we dont need to use printable path in the keys
      nest {
        nativeAndroidProject.fileExtensions.forEach { (key, value) ->
          prop(key) { value }
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

private fun ProjectDumper.dump(artifact: IdeNativeArtifact) {
  prop("Name") { artifact.name }
  prop("ToolChain") { artifact.toolChain }
  prop("GroupName") { artifact.groupName }
  prop("ABI") { artifact.abi }
  prop("TargetName") { artifact.targetName }
  prop("OutputFile") { artifact.outputFile?.path?.toPrintablePath() }
  if (artifact.sourceFiles.isNotEmpty()) {
    head("SourceFiles")
      nest {
        artifact.sourceFiles.forEach {
          head("SourceFile")
            nest {
              dump(it)
            }
        }
      }
  }
  artifact.exportedHeaders.forEach { prop("ExportedHeaders") { it.path.toPrintablePath() } }
}

private fun ProjectDumper.dump(nativeFile: IdeNativeFile) {
  prop("FilePath") { nativeFile.filePath.path.toPrintablePath() }
  prop("SettingsName") { nativeFile.settingsName }
  prop("WorkingDirectory") { nativeFile.workingDirectory?.path?.toPrintablePath() }
}

private fun ProjectDumper.dump(settings: IdeNativeSettings) {
  prop("Name") { settings.name }
  settings.compilerFlags.forEach { prop("CompilerFlags") { it } }
}

private fun ProjectDumper.dump(toolChain: IdeNativeToolchain) {
  prop("Name") { toolChain.name }
  prop("CCompilerExecutable") { toolChain.cCompilerExecutable?.path?.toPrintablePath() }
  prop("CPPCompilerExecutable") { toolChain.cppCompilerExecutable?.path?.toPrintablePath() }
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

private fun ProjectDumper.dump(nativeVariantInfo: IdeNativeVariantInfo) {
  nativeVariantInfo.abiNames.forEach { prop("AbiName") { it } }
  if (nativeVariantInfo.buildRootFolderMap.isNotEmpty()) {
    head("BuildRootFolderMap")
      nest {
        nativeVariantInfo.buildRootFolderMap.forEach { (key, value) -> prop(key) { value.path.toPrintablePath() } }
      }
  }

}
