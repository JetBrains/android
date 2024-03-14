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

import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.ModelCache
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V2NdkModelTest {
  val modelCache = ModelCache.createForTests(StudioFlags.GRADLE_SYNC_USE_V2_MODEL.get(), AgpVersion.parse("4.2.0-alpha02"))
  private val tempDir = Files.createTempDirectory("V2NdkModelTest").toFile()
  private val nativeModule = object : NativeModule {
    override val name: String = "moduleName"
    override val nativeBuildSystem: NativeBuildSystem = NativeBuildSystem.CMAKE
    override val ndkVersion: String = "21.1.12345"
    override val defaultNdkVersion: String = "20.0.12345"
    override val externalNativeBuildFile: File = tempDir.resolve("CMakeLists.txt")
    override val variants: List<NativeVariant> = listOf(
      object : NativeVariant {
        override val name: String = "debug"
        override val abis: List<NativeAbi> = listOf(
          object : NativeAbi {
            override val name: String = "x86"
            override val sourceFlagsFile: File = tempDir.resolve("some-build-dir/debug/x86/compile_commands.json")
            override val symbolFolderIndexFile: File = tempDir.resolve("some-build-dir/debug/x86/symbol_folder_index.txt")
            override val buildFileIndexFile: File = tempDir.resolve("some-build-dir/debug/x86/build_file_index.txt")
            override val additionalProjectFilesIndexFile: File = tempDir.resolve("some-build-dir/debug/x86/additional_project_files.txt")
          },
          object : NativeAbi {
            override val name: String = "arm64-v8a"
            override val sourceFlagsFile: File = tempDir.resolve("some-build-dir/debug/arm64-v8a/compile_commands.json")
            override val symbolFolderIndexFile: File = tempDir.resolve("some-build-dir/debug/arm64-v8a/symbol_folder_index.txt")
            override val buildFileIndexFile: File = tempDir.resolve("some-build-dir/debug/arm64-v8a/build_file_index.txt")
            override val additionalProjectFilesIndexFile: File = tempDir.resolve("some-build-dir/debug/arm64-v8a/additional_project_files.txt")
          }
        )
      },
      object : NativeVariant {
        override val name: String = "release"
        override val abis: List<NativeAbi> = listOf(
          object : NativeAbi {
            override val name: String = "x86"
            override val sourceFlagsFile: File = tempDir.resolve("some-build-dir/release/x86/compile_commands.json")
            override val symbolFolderIndexFile: File = tempDir.resolve("some-build-dir/release/x86/symbol_folder_index.txt")
            override val buildFileIndexFile: File = tempDir.resolve("some-build-dir/release/x86/build_file_index.txt")
            override val additionalProjectFilesIndexFile: File = tempDir.resolve("some-build-dir/release/x86/additional_project_files.txt")
          },
          object : NativeAbi {
            override val name: String = "arm64-v8a"
            override val sourceFlagsFile: File = tempDir.resolve("some-build-dir/release/arm64-v8a/compile_commands.json")
            override val symbolFolderIndexFile: File = tempDir.resolve("some-build-dir/release/arm64-v8a/symbol_folder_index.txt")
            override val buildFileIndexFile: File = tempDir.resolve("some-build-dir/release/arm64-v8a/build_file_index.txt")
            override val additionalProjectFilesIndexFile: File = tempDir.resolve("some-build-dir/release/arm64-v8a/additional_project_files.txt")
          }
        )
      }
    )
  }

  private val v2NdkModel = V2NdkModel("4.2.0-alpha02", modelCache.nativeModuleFrom(nativeModule))

  @Test
  fun `test accessors with no build information files`() {
    Truth.assertThat(v2NdkModel.features.isBuildSystemNameSupported).isTrue()
    Truth.assertThat(v2NdkModel.allVariantAbis).containsExactly(
      VariantAbi("debug", "arm64-v8a"),
      VariantAbi("debug", "x86"),
      VariantAbi("release", "arm64-v8a"),
      VariantAbi("release", "x86")
    ).inOrder()
    Truth.assertThat(v2NdkModel.syncedVariantAbis).isEmpty()
    Truth.assertThat(v2NdkModel.symbolFolders).containsExactly(
      VariantAbi("debug", "arm64-v8a"), emptySet<File>(),
      VariantAbi("debug", "x86"), emptySet<File>(),
      VariantAbi("release", "arm64-v8a"), emptySet<File>(),
      VariantAbi("release", "x86"), emptySet<File>()
    )
    Truth.assertThat(v2NdkModel.buildFiles).isEmpty()
    Truth.assertThat(v2NdkModel.buildSystems).containsExactly("cmake")
    Truth.assertThat(v2NdkModel.defaultNdkVersion).isEqualTo("20.0.12345")
  }

  @Test
  fun `test accessors with build information files`() {
    tempDir.resolve("some-build-dir/debug/x86/compile_commands.json").mkdirAndWriteText("content does not matter")
    tempDir.resolve("some-build-dir/debug/x86/symbol_folder_index.txt").mkdirAndWriteText(
      """
      /path/to/symbol_folder1
      /path/to/symbol_folder2
      """.trimIndent())
    tempDir.resolve("some-build-dir/debug/x86/build_file_index.txt").mkdirAndWriteText(
      """
      /path/to/build_file1
      /path/to/build_file2
      """.trimIndent())

    Truth.assertThat(v2NdkModel.syncedVariantAbis).containsExactly(VariantAbi("debug", "x86"))
    Truth.assertThat(v2NdkModel.symbolFolders).containsExactly(
      VariantAbi("debug", "arm64-v8a"), emptySet<File>(),
      VariantAbi("debug", "x86"), setOf(File("/path/to/symbol_folder1"), File("/path/to/symbol_folder2")),
      VariantAbi("release", "arm64-v8a"), emptySet<File>(),
      VariantAbi("release", "x86"), emptySet<File>()
    )
    Truth.assertThat(v2NdkModel.buildFiles).containsExactly(File("/path/to/build_file1"), File("/path/to/build_file2"))
  }

  private fun File.mkdirAndWriteText(s: String) {
    parentFile.mkdirs()
    writeText(s)
  }
}