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

import com.android.builder.model.NativeAndroidProject
import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeFile
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.builder.model.NativeVariantAbi
import com.android.ide.common.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeDependenciesImpl
import com.android.ide.common.gradle.model.impl.IdeJavaLibraryImpl
import com.android.ide.common.gradle.model.impl.IdeModuleLibraryImpl
import com.android.ide.common.gradle.model.impl.ModelCache
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeAbiImpl
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeModuleImpl
import com.android.ide.common.gradle.model.impl.ndk.v2.IdeNativeVariantImpl
import com.android.ide.common.gradle.model.ndk.v2.NativeBuildSystem
import com.android.ide.common.gradle.model.stubs.AaptOptionsStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactOutputStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactStub
import com.android.ide.common.gradle.model.stubs.AndroidGradlePluginProjectFlagsStub
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub
import com.android.ide.common.gradle.model.stubs.ApiVersionStub
import com.android.ide.common.gradle.model.stubs.BuildTypeContainerStub
import com.android.ide.common.gradle.model.stubs.BuildTypeStub
import com.android.ide.common.gradle.model.stubs.ClassFieldStub
import com.android.ide.common.gradle.model.stubs.FilterDataStub
import com.android.ide.common.gradle.model.stubs.JavaArtifactStub
import com.android.ide.common.gradle.model.stubs.JavaCompileOptionsStub
import com.android.ide.common.gradle.model.stubs.LintOptionsStub
import com.android.ide.common.gradle.model.stubs.MavenCoordinatesStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorContainerStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorStub
import com.android.ide.common.gradle.model.stubs.SigningConfigStub
import com.android.ide.common.gradle.model.stubs.SourceProviderContainerStub
import com.android.ide.common.gradle.model.stubs.SourceProviderStub
import com.android.ide.common.gradle.model.stubs.TestOptionsStub
import com.android.ide.common.gradle.model.stubs.TestedTargetVariantStub
import com.android.ide.common.gradle.model.stubs.VariantStub
import com.android.ide.common.gradle.model.stubs.VectorDrawablesOptionsStub
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.model.java.GradleModuleVersionImpl
import com.android.tools.idea.gradle.model.java.JarLibraryDependency
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot
import com.android.tools.idea.gradle.model.java.JavaModuleDependency
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub
import com.intellij.serialization.ObjectSerializer
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.SkipNullAndEmptySerializationFilter
import com.intellij.serialization.WriteConfiguration
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.kapt.idea.KaptGradleModelImpl
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.RETURNS_SMART_NULLS
import org.mockito.Mockito.`when`
import java.io.File

/**
 * This test ensures that all model classes within sdk-common.gradle.model can be serialized and deserialized using Intellijs
 * serialization mechanisms for DataNodes. This is used to cache the results of sync when we load the project structure from cache.
 */
class ModelSerializationTest {
  private val modelCache = ModelCache.createForTesting()
  private val gradleVersion = GradleVersion.parse("3.2")

  /*
   * BEGIN IDE ONLY MODULES
   */

  @Test
  fun gradleModuleModel() = assertSerializable(disableEqualsCheck = true) {
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
      "3.6.0-dev",
      KaptGradleModelImpl(true, File("some/path"), listOf()))
  }

  @Test
  fun androidModuleModel() = assertSerializable(disableEqualsCheck = true) {
    val variants = listOf(VariantStub())
    val androidProject = modelCache.androidProjectFrom(AndroidProjectStub("3.6.0"))
    AndroidModuleModel.create(
      "moduleName",
      File("some/file/path"),
      androidProject,
      variants.map { modelCache.variantFrom(
        androidProject,
        it,
        GradleVersion.tryParseAndroidGradlePluginVersion(androidProject.modelVersion)
      ) },
      "variantName"
    )
  }

  @Test
  fun v1NdkModel() = assertSerializable {
    V1NdkModel(modelCache.nativeAndroidProjectFrom(Mockito.mock(NativeAndroidProject::class.java, RETURNS_SMART_NULLS)),
               listOf(modelCache.nativeVariantAbiFrom(Mockito.mock(NativeVariantAbi::class.java, RETURNS_SMART_NULLS))))
  }

  @Test
  fun v2NdkModel() = assertSerializable {
    V2NdkModel(
      "agpVersion",
      IdeNativeModuleImpl("name", emptyList(), NativeBuildSystem.CMAKE, "ndkVersion", "defaultNdkVersion", File("externalNativeBuildFile")))
  }

  @Test
  fun ndkModuleModel() = assertSerializable {
    NdkModuleModel(
      "moduleName",
      File("some/path"),
      modelCache.nativeAndroidProjectFrom(Mockito.mock(NativeAndroidProject::class.java, RETURNS_SMART_NULLS)),
      listOf()
    )
  }

  @Test
  fun gradleModuleVersionImpl() = assertSerializable {
    GradleModuleVersionImpl("group", "name", "version")
  }

  @Test
  fun jarLibraryDependency() = assertSerializable {
    JarLibraryDependency("name", null, null, null, null, null, false)
  }

  @Test
  fun javaModuleContentRoot() = assertSerializable {
    JavaModuleContentRoot(File("rootDir"), listOf(), listOf(), listOf(), listOf(), listOf(), listOf(), listOf())
  }

  @Test
  fun javaModuleDependency() = assertSerializable {
    JavaModuleDependency("moduleName", "moduleId", null, false)
  }

  /*
   * END IDE ONLY MODULES
   * BEGIN LEVEL TWO DEPENDENCY MODELS
   */

  @Test
  fun level2AndroidLibrary() = assertSerializable {
    IdeAndroidLibraryImpl(
      "artifactAddress",
      File("folder"),
      "manifest",
      "jarFile",
      "compileJarFile",
      "resFolder",
      File("resStaticLibrary"),
      "assetsFolder",
      listOf("localJars"),
      "jniFolder",
      "aidlFolder",
      "renderscriptFolder",
      "prouardRules",
      "lintJar",
      "externalAnnotations",
      "publicResources",
      File("artifactFile"),
      "symbolFile",
      true
    )
  }

  @Test
  fun level2JavaLibrary() = assertSerializable {
    IdeJavaLibraryImpl("artifactAddress", File("artifactFile"), true)
  }

  @Test
  fun level2ModuleLibrary() = assertSerializable {
    IdeModuleLibraryImpl("projectPath", "artifactAddress", "buildId")
  }

  @Test
  fun level2Dependencies() = assertSerializable {
    // We use a local one to avoid changing the global one that is used for other tests.
    modelCache.dependenciesFrom(AndroidArtifactStub()) as IdeDependenciesImpl
  }

  /*
   * END LEVEL2 DEPENDENCY MODELS
   * BEGIN OTHER SHARED (IDE+LINT) MODELS
   */

  @Test
  fun aaptOptions() = assertSerializable {
    modelCache.aaptOptionsFrom(AaptOptionsStub())
  }

  @Test
  fun androidArtifact() = assertSerializable {
    modelCache.androidArtifactFrom(AndroidArtifactStub(), gradleVersion)
  }

  @Test
  fun androidArtifactOutput() = assertSerializable {
    modelCache.androidArtifactOutputFrom(AndroidArtifactOutputStub())
  }

  @Test
  fun androidProject() = assertSerializable {
    modelCache.androidProjectFrom(AndroidProjectStub("3.6.0"))
  }

  @Test
  fun apiVersion() = assertSerializable {
    modelCache.apiVersionFrom(ApiVersionStub())
  }

  @Test
  fun buildType() = assertSerializable {
    modelCache.buildTypeFrom(BuildTypeStub())
  }

  @Test
  fun buildTypeContainer() = assertSerializable {
    modelCache.buildTypeContainerFrom(BuildTypeContainerStub())
  }

  @Test
  fun classField() = assertSerializable {
    modelCache.classFieldFrom(ClassFieldStub())
  }

  @Test
  fun filterData() = assertSerializable {
    modelCache.filterDataFrom(FilterDataStub())
  }

  @Test
  fun javaArtifact() = assertSerializable {
    modelCache.javaArtifactFrom(JavaArtifactStub())
  }

  @Test
  fun javaCompileOptions() = assertSerializable {
    modelCache.javaCompileOptionsFrom(JavaCompileOptionsStub())
  }

  @Test
  fun lintOptions() = assertSerializable {
    modelCache.lintOptionsFrom(LintOptionsStub(), gradleVersion)
  }

  @Test
  fun androidGradlePluginProjectFlags() = assertSerializable {
    modelCache.androidGradlePluginProjectFlagsFrom(AndroidGradlePluginProjectFlagsStub())
  }

  @Test
  fun mavenCoordinates() = assertSerializable {
    modelCache.mavenCoordinatesFrom(MavenCoordinatesStub())
  }

  @Test
  fun nativeAndroidProjectImpl() = assertSerializable {
    modelCache.nativeAndroidProjectFrom(Mockito.mock(NativeAndroidProject::class.java, RETURNS_SMART_NULLS))
  }

  @Test
  fun nativeArtifact() = assertSerializable {
    val artifact = Mockito.mock(NativeArtifact::class.java, RETURNS_SMART_NULLS)
    `when`(artifact.outputFile).thenReturn(File("outputFile"))
    modelCache.nativeArtifactFrom(artifact)
  }

  @Test
  fun nativeFile() = assertSerializable {
    val file = Mockito.mock(NativeFile::class.java, RETURNS_SMART_NULLS)
    `when`(file.filePath).thenReturn(File("filePath"))
    `when`(file.settingsName).thenReturn("settingName")
    `when`(file.workingDirectory).thenReturn(File("workingDirectory"))
    modelCache.nativeFileFrom(file)
  }

  @Test
  fun nativeSettings() = assertSerializable {
    modelCache.nativeSettingsFrom(Mockito.mock(NativeSettings::class.java, RETURNS_SMART_NULLS))
  }

  @Test
  fun nativeToolchain() = assertSerializable {
    val toolchain = Mockito.mock(NativeToolchain::class.java, RETURNS_SMART_NULLS)
    `when`(toolchain.name).thenReturn("name")
    `when`(toolchain.cCompilerExecutable).thenReturn(File("c-compilier.exe"))
    `when`(toolchain.cppCompilerExecutable).thenReturn(File("cpp-compilier.exe"))
    modelCache.nativeToolchainFrom(toolchain)
  }

  @Test
  fun nativeVariantAbi() = assertSerializable {
    modelCache.nativeVariantAbiFrom(Mockito.mock(NativeVariantAbi::class.java, RETURNS_SMART_NULLS))
  }

  @Test
  fun nativeAbi() = assertSerializable {
    IdeNativeAbiImpl("name", File("sourceFlagsFile"), File("sourceFolderIndexFile"), File("buildFileIndexFile"), File("additionalProjectFiles"))
  }

  @Test
  fun nativeModule() = assertSerializable {
    IdeNativeModuleImpl("name", emptyList(), NativeBuildSystem.CMAKE, "ndkVersion", "defaultNdkVersion", File("externalNativeBuildFile"))
  }

  @Test
  fun nativeVariant() = assertSerializable {
    IdeNativeVariantImpl("name", emptyList())
  }

  @Test
  fun productFlavor() = assertSerializable {
    modelCache.productFlavorFrom(ProductFlavorStub())
  }

  @Test
  fun productFlavorContainer() = assertSerializable {
    modelCache.productFlavorContainerFrom(ProductFlavorContainerStub())
  }

  @Test
  fun signingConfig() = assertSerializable {
    modelCache.signingConfigFrom(SigningConfigStub())
  }

  @Test
  fun sourceProvider() = assertSerializable {
    modelCache.sourceProviderFrom(SourceProviderStub())
  }

  @Test
  fun sourceProviderContainer() = assertSerializable {
    modelCache.sourceProviderContainerFrom(SourceProviderContainerStub())
  }

  @Test
  fun testedTargetVariant() = assertSerializable {
    modelCache.testedTargetVariantFrom(TestedTargetVariantStub())
  }

  @Test
  fun testOptions() = assertSerializable {
    modelCache.testOptionsFrom(TestOptionsStub())
  }

  @Test
  fun variant() = assertSerializable {
    val androidProject = modelCache.androidProjectFrom(AndroidProjectStub("3.6.0"))
    modelCache.variantFrom(androidProject, VariantStub(), gradleVersion)
  }

  @Test
  fun vectorDrawablesOptions() = assertSerializable {
    modelCache.vectorDrawablesOptionsFrom(VectorDrawablesOptionsStub())
  }

  @Test
  fun viewBindingOptions() = assertSerializable {
    modelCache.viewBindingOptionsFrom(ViewBindingOptionsStub())
  }

  @Test
  fun gradleVersion() = assertSerializable {
    GradleVersion.parse("4.1.10")
  }


  /*
   * END OTHER SHARED (IDE + LINT) MODELS
   * BEGIN MISC TESTS
   */

  @Test
  fun map() = assertSerializable {
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
