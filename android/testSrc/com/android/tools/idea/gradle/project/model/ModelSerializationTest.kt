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

import com.android.builder.model.Dependencies
import com.android.ide.common.gradle.model.IdeAaptOptions
import com.android.ide.common.gradle.model.IdeAndroidArtifactImpl
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput
import com.android.ide.common.gradle.model.IdeAndroidLibrary
import com.android.ide.common.gradle.model.IdeAndroidProjectImpl
import com.android.ide.common.gradle.model.IdeApiVersion
import com.android.ide.common.gradle.model.IdeBuildType
import com.android.ide.common.gradle.model.IdeBuildTypeContainer
import com.android.ide.common.gradle.model.IdeClassField
import com.android.ide.common.gradle.model.IdeDependenciesImpl
import com.android.ide.common.gradle.model.IdeDependencyGraphs
import com.android.ide.common.gradle.model.IdeFilterData
import com.android.ide.common.gradle.model.IdeGraphItem
import com.android.ide.common.gradle.model.IdeInstantRun
import com.android.ide.common.gradle.model.IdeJavaArtifact
import com.android.ide.common.gradle.model.IdeJavaCompileOptions
import com.android.ide.common.gradle.model.IdeJavaLibrary
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.gradle.model.IdeMavenCoordinates
import com.android.ide.common.gradle.model.IdeNativeAndroidProjectImpl
import com.android.ide.common.gradle.model.IdeNativeArtifact
import com.android.ide.common.gradle.model.IdeNativeFile
import com.android.ide.common.gradle.model.IdeNativeLibrary
import com.android.ide.common.gradle.model.IdeNativeSettings
import com.android.ide.common.gradle.model.IdeNativeToolchain
import com.android.ide.common.gradle.model.IdeNativeVariantAbi
import com.android.ide.common.gradle.model.IdeOutputFile
import com.android.ide.common.gradle.model.IdeProductFlavor
import com.android.ide.common.gradle.model.IdeProductFlavorContainer
import com.android.ide.common.gradle.model.IdeProjectIdentifierImpl
import com.android.ide.common.gradle.model.IdeProjectSyncIssues
import com.android.ide.common.gradle.model.IdeSigningConfig
import com.android.ide.common.gradle.model.IdeSourceProvider
import com.android.ide.common.gradle.model.IdeSourceProviderContainer
import com.android.ide.common.gradle.model.IdeSyncIssue
import com.android.ide.common.gradle.model.IdeTestOptions
import com.android.ide.common.gradle.model.IdeTestedTargetVariant
import com.android.ide.common.gradle.model.IdeVariantImpl
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions
import com.android.ide.common.gradle.model.IdeViewBindingOptions
import com.android.ide.common.gradle.model.ModelCache
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.ide.common.gradle.model.level2.IdeLibraryFactory
import com.android.ide.common.gradle.model.level2.IdeModuleLibrary
import com.android.ide.common.gradle.model.stubs.AaptOptionsStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactOutputStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactStub
import com.android.ide.common.gradle.model.stubs.AndroidLibraryStub
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub
import com.android.ide.common.gradle.model.stubs.ApiVersionStub
import com.android.ide.common.gradle.model.stubs.BuildTypeContainerStub
import com.android.ide.common.gradle.model.stubs.BuildTypeStub
import com.android.ide.common.gradle.model.stubs.ClassFieldStub
import com.android.ide.common.gradle.model.stubs.DependenciesStub
import com.android.ide.common.gradle.model.stubs.DependencyGraphsStub
import com.android.ide.common.gradle.model.stubs.FilterDataStub
import com.android.ide.common.gradle.model.stubs.GlobalLibraryMapStub
import com.android.ide.common.gradle.model.stubs.GraphItemStub
import com.android.ide.common.gradle.model.stubs.InstantRunStub
import com.android.ide.common.gradle.model.stubs.JavaArtifactStub
import com.android.ide.common.gradle.model.stubs.JavaCompileOptionsStub
import com.android.ide.common.gradle.model.stubs.JavaLibraryStub
import com.android.ide.common.gradle.model.stubs.LintOptionsStub
import com.android.ide.common.gradle.model.stubs.MavenCoordinatesStub
import com.android.ide.common.gradle.model.stubs.NativeAndroidProjectStub
import com.android.ide.common.gradle.model.stubs.NativeArtifactStub
import com.android.ide.common.gradle.model.stubs.NativeFileStub
import com.android.ide.common.gradle.model.stubs.NativeLibraryStub
import com.android.ide.common.gradle.model.stubs.NativeSettingsStub
import com.android.ide.common.gradle.model.stubs.NativeToolchainStub
import com.android.ide.common.gradle.model.stubs.NativeVariantAbiStub
import com.android.ide.common.gradle.model.stubs.OutputFileStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorContainerStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorStub
import com.android.ide.common.gradle.model.stubs.ProjectSyncIssuesStub
import com.android.ide.common.gradle.model.stubs.SigningConfigStub
import com.android.ide.common.gradle.model.stubs.SourceProviderContainerStub
import com.android.ide.common.gradle.model.stubs.SourceProviderStub
import com.android.ide.common.gradle.model.stubs.SyncIssueStub
import com.android.ide.common.gradle.model.stubs.TestOptionsStub
import com.android.ide.common.gradle.model.stubs.TestedTargetVariantStub
import com.android.ide.common.gradle.model.stubs.VariantStub
import com.android.ide.common.gradle.model.stubs.VectorDrawablesOptionsStub
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.ide.common.gradle.model.stubs.l2AndroidLibrary
import com.android.ide.common.gradle.model.stubs.l2JavaLibrary
import com.android.ide.common.gradle.model.stubs.l2ModuleLibrary
import com.android.ide.common.gradle.model.stubs.level2.AndroidLibraryStubBuilder
import com.android.ide.common.gradle.model.stubs.level2.JavaLibraryStubBuilder
import com.android.ide.common.gradle.model.stubs.level2.ModuleLibraryStubBuilder
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub
import com.intellij.serialization.ObjectSerializer
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.SkipNullAndEmptySerializationFilter
import com.intellij.serialization.WriteConfiguration
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.kapt.idea.KaptGradleModelImpl
import org.junit.Test
import java.io.File

/**
 * This test ensures that all model classes within sdk-common.gradle.model can be serialized and deserialized using Intellijs
 * serialization mechanisms for DataNodes. This is used to cache the results of sync when we load the project structure from cache.
 */
class ModelSerializationTest {
  private val modelCache = ModelCache()
  private val dependenciesFactory = IdeDependenciesFactory()
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
    val androidProject = IdeAndroidProjectImpl.create(
      AndroidProjectStub("3.6.0"),
      modelCache,
      dependenciesFactory,
      listOf(VariantStub()),
      ProjectSyncIssuesStub(listOf(SyncIssueStub())))
    AndroidModuleModel.create(
      "moduleName",
      File("some/file/path"),
      androidProject,
      "variantName"
    )
  }

  @Test
  fun ndkModuleModel() = assertSerializable {
    NdkModuleModel("moduleName", File("some/path"), IdeNativeAndroidProjectImpl(NativeAndroidProjectStub()), listOf())
  }

  /*
   * END IDE ONLY MODULES
   * BEGIN LEVEL TWO DEPENDENCY MODELS
   */

  @Test
  fun level2AndroidLibrary() = assertSerializable {
    IdeLibraryFactory().create(
      AndroidLibraryStubBuilder().build()) as com.android.ide.common.gradle.model.level2.IdeAndroidLibrary
  }

  @Test
  fun level2JavaLibrary() = assertSerializable {
    IdeLibraryFactory().create(JavaLibraryStubBuilder().build()) as com.android.ide.common.gradle.model.level2.IdeJavaLibrary
  }

  @Test
  fun level2ModuleLibrary() = assertSerializable {
    IdeLibraryFactory().create(ModuleLibraryStubBuilder().build()) as IdeModuleLibrary
  }

  @Test
  fun level2Dependencies() = assertSerializable {
    // We use a local one to avoid changing the global one that is used for other tests.
    val localDependenciesFactory = IdeDependenciesFactory()
    val javaGraphItem = GraphItemStub("javaLibrary", listOf(), "")
    val androidGraphItem = GraphItemStub("androidLibrary", listOf(), "")
    val moduleGraphItem = GraphItemStub("module", listOf(), "")

    val level2JavaLibrary = l2JavaLibrary("javaLibrary")
    val level2AndroidLibrary = l2AndroidLibrary("androidLibrary")
    val level2ModuleLibrary = l2ModuleLibrary("module")

    val graphStub = DependencyGraphsStub(listOf(javaGraphItem, androidGraphItem, moduleGraphItem), listOf(), listOf(), listOf())
    localDependenciesFactory.setUpGlobalLibraryMap(listOf(GlobalLibraryMapStub(
      mapOf("javaLibrary" to level2JavaLibrary, "androidLibrary" to level2AndroidLibrary, "module" to level2ModuleLibrary))))
    localDependenciesFactory.createFromDependencyGraphs(graphStub) as com.android.ide.common.gradle.model.level2.IdeDependenciesImpl
  }

  /*
   * END LEVEL2 DEPENDENCY MODELS
   * BEGIN OTHER SHARED (IDE+LINT) MODELS
   */

  @Test
  fun aaptOptions() =
    assertSerializable { IdeAaptOptions(AaptOptionsStub()) }

  @Test
  fun androidArtifact() =
    assertSerializable { IdeAndroidArtifactImpl(AndroidArtifactStub(), modelCache, dependenciesFactory, gradleVersion) }

  @Test
  fun androidArtifactOutput() =
    assertSerializable { IdeAndroidArtifactOutput(AndroidArtifactOutputStub(), modelCache) }

  @Test
  fun androidLibrary() =
    assertSerializable { IdeAndroidLibrary(AndroidLibraryStub(), modelCache) }

  @Test
  fun androidProject() =
    assertSerializable {
      IdeAndroidProjectImpl.create(
        AndroidProjectStub("3.6.0"),
        modelCache,
        dependenciesFactory,
        listOf(VariantStub()),
        ProjectSyncIssuesStub(listOf(SyncIssueStub())))
    }

  @Test
  fun apiVersion() =
    assertSerializable { IdeApiVersion(ApiVersionStub()) }

  @Test
  fun buildType() =
    assertSerializable { IdeBuildType(BuildTypeStub(), modelCache) }

  @Test
  fun buildTypeContainer() =
    assertSerializable { IdeBuildTypeContainer(BuildTypeContainerStub(), modelCache) }

  @Test
  fun classField() =
    assertSerializable { IdeClassField(ClassFieldStub()) }

  @Test
  fun dependencies() =
    assertSerializable { IdeDependenciesImpl(DependenciesStub(), modelCache) }

  @Test
  fun dependencyGraphs() =
    assertSerializable { IdeDependencyGraphs(DependencyGraphsStub(), modelCache) }

  @Test
  fun filterData() =
    assertSerializable { IdeFilterData(FilterDataStub()) }

  @Test
  fun graphItem() =
    assertSerializable { IdeGraphItem(GraphItemStub(), modelCache) }

  @Test
  fun instantRun() =
    assertSerializable { IdeInstantRun(InstantRunStub()) }

  @Test
  fun javaArtifact() =
    assertSerializable { IdeJavaArtifact(JavaArtifactStub(), modelCache, dependenciesFactory, gradleVersion) }

  @Test
  fun javaCompileOptions() =
    assertSerializable { IdeJavaCompileOptions(JavaCompileOptionsStub()) }

  @Test
  fun javaLibrary() =
    assertSerializable { IdeJavaLibrary(JavaLibraryStub(), modelCache) }

  @Test
  fun lintOptions() =
    assertSerializable { IdeLintOptions(LintOptionsStub(), gradleVersion) }

  @Test
  fun mavenCoordinates() =
    assertSerializable { IdeMavenCoordinates(MavenCoordinatesStub()) }

  @Test
  fun nativeAndroidProjectImpl() =
    assertSerializable { IdeNativeAndroidProjectImpl(NativeAndroidProjectStub()) }

  @Test
  fun nativeArtifact() =
    assertSerializable { IdeNativeArtifact(NativeArtifactStub(), modelCache) }

  @Test
  fun nativeFile() =
    assertSerializable { IdeNativeFile(NativeFileStub()) }

  @Test
  fun nativeLibrary() =
    assertSerializable { IdeNativeLibrary(NativeLibraryStub()) }

  @Test
  fun nativeSettings() =
    assertSerializable { IdeNativeSettings(NativeSettingsStub()) }

  @Test
  fun nativeToolchain() =
    assertSerializable { IdeNativeToolchain(NativeToolchainStub()) }

  @Test
  fun nativeVariantAbi() =
    assertSerializable { IdeNativeVariantAbi(NativeVariantAbiStub(), modelCache) }

  @Test
  fun outputFile() =
    assertSerializable { IdeOutputFile(OutputFileStub(), modelCache) }

  @Test
  fun productFlavor() =
    assertSerializable { IdeProductFlavor(ProductFlavorStub(), modelCache) }

  @Test
  fun productFlavorContainer() =
    assertSerializable { IdeProductFlavorContainer(ProductFlavorContainerStub(), modelCache) }

  @Test
  fun projectIdentifier() =
    assertSerializable { IdeProjectIdentifierImpl(object : Dependencies.ProjectIdentifier {
      override fun getBuildId() = "/root/project1"

      override fun getProjectPath() = ":"
    }) }

  @Test
  fun projectSyncIssues() =
    assertSerializable { IdeProjectSyncIssues(ProjectSyncIssuesStub(), modelCache) }

  @Test
  fun signingConfig() =
    assertSerializable { IdeSigningConfig(SigningConfigStub()) }

  @Test
  fun sourceProvider() =
    assertSerializable { IdeSourceProvider(SourceProviderStub()) }

  @Test
  fun sourceProviderContainer() =
    assertSerializable { IdeSourceProviderContainer(SourceProviderContainerStub(), modelCache) }

  @Test
  fun syncIssue() =
    assertSerializable { IdeSyncIssue(SyncIssueStub()) }

  @Test
  fun testedTargetVariant() =
    assertSerializable { IdeTestedTargetVariant(TestedTargetVariantStub()) }

  @Test
  fun testOptions() =
    assertSerializable { IdeTestOptions(TestOptionsStub()) }

  @Test
  fun variant() =
    assertSerializable { IdeVariantImpl(VariantStub(), modelCache, dependenciesFactory, gradleVersion) }

  @Test
  fun vectorDrawablesOptions() =
    assertSerializable { IdeVectorDrawablesOptions(VectorDrawablesOptionsStub()) }

  @Test
  fun viewBindingOptions() {
    assertSerializable { IdeViewBindingOptions(ViewBindingOptionsStub()) }
  }

  @Test
  fun gradleVersion() {
    assertSerializable { GradleVersion.parse("4.1.10") }
  }

  /*
   * END OTHER SHARED (IDE + LINT) MODELS
   * BEGIN MISC TESTS
   */

  @Test
  fun map() =
    assertSerializable { TestData(1, mapOf<String, Any>("1" to 1)) }

  /*
   * END MISC TESTS
   */

  data class TestData @JvmOverloads constructor(val v: Any? = null, val map: Map<String, Any>? = null)

  private inline fun <reified T : Any> assertSerializable( disableEqualsCheck : Boolean = false, factory: () -> T) {
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