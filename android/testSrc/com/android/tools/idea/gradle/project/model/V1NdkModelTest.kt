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

import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeArtifactImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeSettingsImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeToolchainImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantInfoImpl
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import com.intellij.serialization.ObjectSerializer
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.SkipNullAndEmptySerializationFilter
import com.intellij.serialization.WriteConfiguration
import junit.framework.TestCase
import org.apache.commons.lang.builder.EqualsBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

@RunWith(JUnit4::class)
class V1NdkModelTest {

  @Mock
  private val mockNativeSettings1 = mock(NativeSettings::class.java).apply {
    `when`(name).thenReturn("nativeSettings1")
  }

  @Mock
  private val mockNativeSettings2 = mock(NativeSettings::class.java).apply {
    `when`(name).thenReturn("nativeSettings2")
  }

  @Mock
  private val mockNativeToolchain1 = mock(NativeToolchain::class.java).apply {
    `when`(name).thenReturn("toolchain1")
  }

  @Mock
  private val mockNativeToolchain2 = mock(NativeToolchain::class.java).apply {
    `when`(name).thenReturn("toolchain2")
  }

  private val soFolder = FileUtil.createTempDirectory("V1NdkModelTest", null)
  private val x86SoFolder = soFolder.resolve("x86").apply {
    mkdirs()
  }

  private val x86SoFile = x86SoFolder.resolve("lib.so").apply {
    writeText("whatever")
  }

  private val arm64V8aSoFolder = soFolder.resolve("arm64-v8a").apply {
    mkdirs()
  }
  private val arm64V8aSoFile = arm64V8aSoFolder.resolve("lib.so").apply {
    writeText("whatever")
  }

  private val mockDebugX86Artifact =
    IdeNativeArtifactImpl("artifact1", "toolchain1", "debug", emptyList(), emptyList(), x86SoFile, "x86", "target1")

  private val mockDebugArm64Artifact =
    IdeNativeArtifactImpl("artifact2", "toolchain2", "debug", emptyList(), emptyList(), arm64V8aSoFile, "arm64-v8a", "target2")

  private val fullSyncV1NdkModel = V1NdkModel(
    IdeNativeAndroidProjectImpl(
      "4.2.0-alpha02",
      "moduleName",
      listOf(File("buildFile1"), File("buildFile2")),
      mapOf(
        "debug" to IdeNativeVariantInfoImpl(listOf("x86", "arm64-v8a"), emptyMap()),
        "release" to IdeNativeVariantInfoImpl(listOf("x86", "arm64-v8a"), emptyMap())
      ),
      listOf(mockDebugX86Artifact, mockDebugArm64Artifact),
      listOf(IdeNativeToolchainImpl(
        mockNativeToolchain1.name,
        mockNativeToolchain1.cCompilerExecutable,
        mockNativeToolchain1.cppCompilerExecutable
      ), IdeNativeToolchainImpl(
        mockNativeToolchain2.name,
        mockNativeToolchain2.cCompilerExecutable,
        mockNativeToolchain2.cppCompilerExecutable
      )),
      listOf(IdeNativeSettingsImpl(mockNativeSettings1.name, mockNativeSettings1.compilerFlags),
             IdeNativeSettingsImpl(mockNativeSettings2.name, mockNativeSettings2.compilerFlags)),
      emptyMap(),
      listOf("cmake"),
      "21.1.12345",
      12),
    emptyList()
  )

  private val singleVariantSyncV1NdkModel = V1NdkModel(
    IdeNativeAndroidProjectImpl(
      "4.2.0-alpha02",
      "moduleName",
      listOf(File("buildFile1"), File("buildFile2")),
      mapOf(
        "debug" to IdeNativeVariantInfoImpl(listOf("x86", "arm64-v8a"), emptyMap()),
        "release" to IdeNativeVariantInfoImpl(listOf("x86", "arm64-v8a"), emptyMap())
      ),
      emptyList(),
      emptyList(),
      emptyList(),
      emptyMap(),
      listOf("cmake"),
      "21.1.12345",
      12),
    listOf(
      IdeNativeVariantAbiImpl(
        emptyList(),
        listOf(mockDebugX86Artifact),
        listOf(
          IdeNativeToolchainImpl(
            mockNativeToolchain1.name,
            mockNativeToolchain1.cCompilerExecutable,
            mockNativeToolchain1.cppCompilerExecutable
          )),
        listOf(IdeNativeSettingsImpl(mockNativeSettings1.name, mockNativeSettings1.compilerFlags)),
        emptyMap(),
        "debug",
        "x86"
      )
    )
  )

  @Test
  fun `full sync - test accessors`() {
    // Accessors declared in INdkModel
    Truth.assertThat(fullSyncV1NdkModel.features.isGroupNameSupported).isTrue()
    Truth.assertThat(fullSyncV1NdkModel.allVariantAbis).containsExactly(
      VariantAbi("debug", "arm64-v8a"),
      VariantAbi("debug", "x86"),
      VariantAbi("release", "arm64-v8a"),
      VariantAbi("release", "x86")
    ).inOrder()
    Truth.assertThat(fullSyncV1NdkModel.syncedVariantAbis).containsExactly(
      VariantAbi("debug", "arm64-v8a"),
      VariantAbi("debug", "x86")
    )
    Truth.assertThat(fullSyncV1NdkModel.symbolFolders).containsExactly(
      VariantAbi("debug", "x86"), setOf(x86SoFolder),
      VariantAbi("debug", "arm64-v8a"), setOf(arm64V8aSoFolder)
    )
    Truth.assertThat(fullSyncV1NdkModel.buildFiles).containsExactly(File("buildFile1"), File("buildFile2"))
    Truth.assertThat(fullSyncV1NdkModel.buildSystems).containsExactly("cmake")
    Truth.assertThat(fullSyncV1NdkModel.defaultNdkVersion).isEqualTo("21.1.12345")

    // Accessors only available from V1NdkModel
    Truth.assertThat(fullSyncV1NdkModel.getNdkVariant(VariantAbi("debug", "x86"))!!.artifacts)
      .containsExactly(mockDebugX86Artifact)
    Truth.assertThat(fullSyncV1NdkModel.getNdkVariant(VariantAbi("debug", "arm64-v8a"))!!.artifacts)
      .containsExactly(mockDebugArm64Artifact)
  }

  @Test
  fun `single variant sync - test accessors`() {
    // Accessors declared in INdkModel
    Truth.assertThat(singleVariantSyncV1NdkModel.features.isGroupNameSupported).isTrue()
    Truth.assertThat(singleVariantSyncV1NdkModel.allVariantAbis).containsExactly(
      VariantAbi("debug", "arm64-v8a"),
      VariantAbi("debug", "x86"),
      VariantAbi("release", "arm64-v8a"),
      VariantAbi("release", "x86")
    ).inOrder()
    Truth.assertThat(singleVariantSyncV1NdkModel.syncedVariantAbis).containsExactly(
      VariantAbi("debug", "x86")
    )
    Truth.assertThat(singleVariantSyncV1NdkModel.symbolFolders).containsExactly(
      VariantAbi("debug", "x86"), setOf(x86SoFolder)
    )
    Truth.assertThat(singleVariantSyncV1NdkModel.buildFiles).containsExactly(File("buildFile1"), File("buildFile2"))
    Truth.assertThat(singleVariantSyncV1NdkModel.buildSystems).containsExactly("cmake")
    Truth.assertThat(singleVariantSyncV1NdkModel.defaultNdkVersion).isEqualTo("21.1.12345")

    // Accessors only available from V1NdkModel
    Truth.assertThat(singleVariantSyncV1NdkModel.getNdkVariant(VariantAbi("debug", "x86"))!!.artifacts).containsExactly(
      mockDebugX86Artifact)
  }

  @Test
  fun `symbolFolders should reflect changes in filesystem`() {
    Truth.assertThat(fullSyncV1NdkModel.symbolFolders).containsExactly(
      VariantAbi("debug", "x86"), setOf(x86SoFolder),
      VariantAbi("debug", "arm64-v8a"), setOf(arm64V8aSoFolder)
    )
    arm64V8aSoFolder.deleteRecursively()
    Truth.assertThat(fullSyncV1NdkModel.symbolFolders).containsExactly(
      VariantAbi("debug", "x86"), setOf(x86SoFolder),
      VariantAbi("debug", "arm64-v8a"), emptySet<File>()
    )
  }

  @Test
  fun `test serialization`() {
    assertSerializable(fullSyncV1NdkModel)
    assertSerializable(singleVariantSyncV1NdkModel)
  }

  companion object {
    inline fun <reified T : Any> assertSerializable(value: T): T {
      val configuration = WriteConfiguration(
        allowAnySubTypes = true,
        binary = false,
        filter = SkipNullAndEmptySerializationFilter
      )
      val bytes = ObjectSerializer.instance.writeAsBytes(value, configuration)
      val deserialized = ObjectSerializer.instance.read(T::class.java, bytes, ReadConfiguration(allowAnySubTypes = true))
      val bytes2 = ObjectSerializer.instance.writeAsBytes(deserialized, configuration)
      TestCase.assertEquals(String(bytes), String(bytes2))
      EqualsBuilder.reflectionEquals(value, deserialized)
      return deserialized
    }
  }
}