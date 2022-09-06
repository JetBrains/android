/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.model.impl.IdeAaptOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryDependencyImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl
import com.android.tools.idea.gradle.model.impl.IdeBasicVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeBuildTypeImpl
import com.android.tools.idea.gradle.model.impl.IdeClassFieldImpl
import com.android.tools.idea.gradle.model.impl.IdeDependenciesCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeFilterDataImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdePreResolvedModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.model.impl.IdeSigningConfigImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeSourceProviderImpl
import com.android.tools.idea.gradle.model.impl.IdeTestOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeTestedTargetVariantImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantCoreImpl
import com.android.tools.idea.gradle.model.impl.IdeVectorDrawablesOptionsImpl
import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeArtifactImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeFileImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeSettingsImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeToolchainImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.tools.idea.gradle.model.impl.ndk.v2.IdeNativeVariantImpl
import com.android.tools.idea.gradle.model.ndk.v2.NativeBuildSystem
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.truth.Truth
import com.intellij.serialization.ObjectSerializer
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.SkipNullAndEmptySerializationFilter
import com.intellij.serialization.WriteConfiguration
import org.junit.Test
import java.io.File
import java.io.Serializable

/**
 * This test ensures that all model classes within sdk-common.gradle.model can be serialized and deserialized using Intellijs
 * serialization mechanisms for DataNodes. This is used to cache the results of sync when we load the project structure from cache.
 */
class ModelSerializationTest : AndroidGradleTestCase() {

  /*
   * BEGIN IDE ONLY MODULES
   */

  @Test
  fun testGradleModuleModel() = assertSerializable(disableEqualsCheck = true) {
    val gradleProject = GradleProjectStub(
      "someName",
      "somePath",
      File("/some/fake/root/dir"),
      File("/some/fake/project/file"),
      "task1",
      "task2"
    )
    GradleModuleModel(
      "testName",
      gradleProject,
      listOf("plugin1", "plugin2"),
      null,
      "4.1.10",
      "3.6.0-dev"
    )
  }

  @Test
  fun testAndroidModuleModel() = assertSerializable {
    setupTestProjectFromAndroidModel(
      project,
      Projects.getBaseDirPath(project),
      true,
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        ":moduleName", null, "3.6.0", "debug", AndroidProjectBuilder())
    )

    val module = project.gradleModule(":moduleName")
    Truth.assertThat(module).isNotNull()

    (GradleAndroidModel.get(module!!)!!.data as GradleAndroidModelDataImpl)
  }

  @Test
  fun testV1NdkModel() = assertSerializable {
    V1NdkModel(
      IdeNativeAndroidProjectImpl(
        "3.6.0", "moduleName", listOf(), mapOf(), listOf(), listOf(), listOf(), mapOf(), listOf(), "21.0.0", "21.0.0", 12),
      listOf(IdeNativeVariantAbiImpl(listOf(), listOf(), listOf(), listOf(), mapOf(), "variantName", "abi")))
  }

  @Test
  fun testV2NdkModel() = assertSerializable {
    V2NdkModel(
      "agpVersion",
      IdeNativeModuleImpl("name", emptyList(), NativeBuildSystem.CMAKE, "ndkVersion", "defaultNdkVersion", File("externalNativeBuildFile")))
  }

  @Test
  fun testNdkModuleModel() = assertSerializable {
    NdkModuleModel(
      "moduleName",
      File("some/path"),
      "debug",
      "x86",
      IdeNativeAndroidProjectImpl(
        "3.6.0", "moduleName", listOf(), mapOf(), listOf(), listOf(), listOf(), mapOf(), listOf(), "21.0.0", "21.0.0", 12),
      listOf()
    )
  }

  /*
   * END IDE ONLY MODULES
   * BEGIN LEVEL TWO DEPENDENCY MODELS
   */

  @Test
  fun testLevel2AndroidLibrary() = assertSerializable {
    IdeAndroidLibraryImpl.create(
      "artifactAddress",
      "name",
      File("folder"),
      "manifest",
      listOf("compileJarFiles"),
      listOf("runtimeJarFiles"),
      "resFolder",
      File("resStaticLibrary"),
      "assetsFolder",
      "jniFolder",
      "aidlFolder",
      "renderscriptFolder",
      "prouardRules",
      "lintJar",
      "externalAnnotations",
      "publicResources",
      File("artifactFile"),
      "symbolFile",
      deduplicate = { this }
    )
  }

  @Test
  fun testLevel2AndroidLibraryDependency() = assertSerializable {
    IdeAndroidLibraryDependencyImpl(
      IdeAndroidLibraryImpl.create(
        "artifactAddress",
        "name",
        File("folder"),
        "manifest",
        listOf("compileJarFiles"),
        listOf("runtimeJarFiles"),
        "resFolder",
        File("resStaticLibrary"),
        "assetsFolder",
        "jniFolder",
        "aidlFolder",
        "renderscriptFolder",
        "prouardRules",
        "lintJar",
        "externalAnnotations",
        "publicResources",
        File("artifactFile"),
        "symbolFile",
        deduplicate = { this }
      )
    )
  }

  @Test
  fun testLevel2JavaLibrary() = Truth.assertThat(IdeJavaLibraryImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testLevel2ModuleLibrary() = Truth.assertThat(IdePreResolvedModuleLibraryImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testDependencyCores() = Truth.assertThat(IdeDependenciesCoreImpl::class.java).isAssignableTo(Serializable::class.java)

  /*
   * END LEVEL2 DEPENDENCY MODELS
   * BEGIN OTHER SHARED (IDE+LINT) MODELS
   */

  @Test
  fun testAaptOptions() = Truth.assertThat(IdeAaptOptionsImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testAndroidArtifactCore() = Truth.assertThat(IdeAndroidArtifactCoreImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testAndroidArtifactOutput() = Truth.assertThat(IdeAndroidArtifactOutputImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testAndroidProject() = Truth.assertThat(IdeAndroidProjectImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testApiVersion() = Truth.assertThat(IdeApiVersionImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testBuildType() = Truth.assertThat(IdeBuildTypeImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testFilterData() = Truth.assertThat(IdeFilterDataImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testJavaArtifactCore() = Truth.assertThat(IdeJavaArtifactCoreImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testJavaCompileOptions() = Truth.assertThat(IdeJavaCompileOptionsImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testLintOptions() = Truth.assertThat(IdeLintOptionsImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testAndroidGradlePluginProjectFlags() = Truth.assertThat(IdeAndroidGradlePluginProjectFlagsImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testNativeAndroidProjectImpl() = Truth.assertThat(IdeNativeAndroidProjectImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testNativeArtifact() = Truth.assertThat(IdeNativeArtifactImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testNativeFile() = Truth.assertThat(IdeNativeFileImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testNativeSettings() = Truth.assertThat(IdeNativeSettingsImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testNativeToolchain() = Truth.assertThat(IdeNativeToolchainImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testNativeAbi() = Truth.assertThat(IdeNativeAbiImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testNativeVariant() = Truth.assertThat(IdeNativeVariantImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testProductFlavor() = Truth.assertThat(IdeProductFlavorImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testProductFlavorContainer() = Truth.assertThat(IdeProductFlavorContainerImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testSigningConfig() = Truth.assertThat(IdeSigningConfigImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testSourceProviderContainer() = Truth.assertThat(IdeSourceProviderImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testTestedTargetVariant() = Truth.assertThat(IdeTestedTargetVariantImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testTestOptions() = Truth.assertThat(IdeTestOptionsImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testBasicVariant() = Truth.assertThat(IdeBasicVariantImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testVariantCore() = Truth.assertThat(IdeVariantCoreImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testVectorDrawablesOptions() = Truth.assertThat(IdeVectorDrawablesOptionsImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testViewBindingOptions() = Truth.assertThat(IdeViewBindingOptionsImpl::class.java).isAssignableTo(Serializable::class.java)

  @Test
  fun testSerializableIdeBuildTypeContainer() {
    Truth.assertThat(IdeBuildTypeContainerImpl::class.java).isAssignableTo(Serializable::class.java)
  }

  @Test
  fun testSerializableIdeClassField() {
    Truth.assertThat(IdeClassFieldImpl::class.java).isAssignableTo(Serializable::class.java)
  }

  @Test
  fun testSerializableIdeSourceProviderContainer() {
    Truth.assertThat(IdeSourceProviderContainerImpl::class.java).isAssignableTo(Serializable::class.java)
  }

  @Test
  fun testGradleVersion() = assertSerializable {
    GradleVersion.parse("4.1.10")
  }


  /*
   * END OTHER SHARED (IDE + LINT) MODELS
   * BEGIN MISC TESTS
   */

  @Test
  fun testMap() = assertSerializable {
    TestData(1, mapOf<String, Any>("1" to 1))
  }

  /*
   * END MISC TESTS
   */

  data class TestData @JvmOverloads constructor(val v: Any? = null, val map: Map<String, Any>? = null)

  private inline fun <reified T : Any> assertSerializable(disableEqualsCheck: Boolean = false, factory: () -> T) {
    val value = factory()
    val configuration = WriteConfiguration(
      allowAnySubTypes = true,
      binary = false,
      filter = SkipNullAndEmptySerializationFilter
    )
    val bytes = ObjectSerializer.instance.writeAsBytes(value, configuration)
    val o = ObjectSerializer.instance.read(T::class.java, bytes, ReadConfiguration(allowAnySubTypes = true))
    val bytes2 = ObjectSerializer.instance.writeAsBytes(o, configuration)
    assertEquals(String(bytes), String(bytes2))
    if (!disableEqualsCheck) {
      assertEquals(value, o)
    }
  }
}
