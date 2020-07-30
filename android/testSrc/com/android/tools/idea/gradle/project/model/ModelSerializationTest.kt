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

import com.android.ide.common.gradle.model.impl.IdeAaptOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidArtifactImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidArtifactOutputImpl
import com.android.ide.common.gradle.model.impl.IdeAndroidProjectImpl
import com.android.ide.common.gradle.model.impl.IdeApiVersionImpl
import com.android.ide.common.gradle.model.impl.IdeBuildTypeContainerImpl
import com.android.ide.common.gradle.model.impl.IdeBuildTypeImpl
import com.android.ide.common.gradle.model.impl.IdeClassFieldImpl
import com.android.ide.common.gradle.model.impl.IdeFilterDataImpl
import com.android.ide.common.gradle.model.impl.IdeJavaArtifactImpl
import com.android.ide.common.gradle.model.impl.IdeJavaCompileOptionsImpl
import com.android.ide.common.gradle.model.IdeLintOptions
import com.android.ide.common.gradle.model.impl.IdeMavenCoordinatesImpl
import com.android.ide.common.gradle.model.impl.IdeNativeAndroidProjectImpl
import com.android.ide.common.gradle.model.impl.IdeNativeArtifactImpl
import com.android.ide.common.gradle.model.impl.IdeNativeFileImpl
import com.android.ide.common.gradle.model.impl.IdeNativeSettingsImpl
import com.android.ide.common.gradle.model.impl.IdeNativeToolchainImpl
import com.android.ide.common.gradle.model.impl.IdeNativeVariantAbiImpl
import com.android.ide.common.gradle.model.impl.IdeOutputFileImpl
import com.android.ide.common.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.ide.common.gradle.model.impl.IdeProductFlavorImpl
import com.android.ide.common.gradle.model.impl.IdeSigningConfigImpl
import com.android.ide.common.gradle.model.impl.IdeSourceProviderContainerImpl
import com.android.ide.common.gradle.model.impl.IdeSourceProviderImpl
import com.android.ide.common.gradle.model.impl.IdeSyncIssueImpl
import com.android.ide.common.gradle.model.impl.IdeTestOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeTestedTargetVariantImpl
import com.android.ide.common.gradle.model.impl.IdeVariantImpl
import com.android.ide.common.gradle.model.impl.IdeVectorDrawablesOptionsImpl
import com.android.ide.common.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.ide.common.gradle.model.ModelCache
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.ide.common.gradle.model.level2.IdeDependenciesImpl
import com.android.ide.common.gradle.model.level2.IdeModuleLibrary
import com.android.ide.common.gradle.model.stubs.AaptOptionsStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactOutputStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactStub
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
import com.android.ide.common.gradle.model.stubs.NativeAndroidProjectStub
import com.android.ide.common.gradle.model.stubs.NativeArtifactStub
import com.android.ide.common.gradle.model.stubs.NativeFileStub
import com.android.ide.common.gradle.model.stubs.NativeSettingsStub
import com.android.ide.common.gradle.model.stubs.NativeToolchainStub
import com.android.ide.common.gradle.model.stubs.NativeVariantAbiStub
import com.android.ide.common.gradle.model.stubs.OutputFileStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorContainerStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorStub
import com.android.ide.common.gradle.model.stubs.SigningConfigStub
import com.android.ide.common.gradle.model.stubs.SourceProviderContainerStub
import com.android.ide.common.gradle.model.stubs.SourceProviderStub
import com.android.ide.common.gradle.model.stubs.SyncIssueStub
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
      listOf(SyncIssueStub()))
    AndroidModuleModel.create(
      "moduleName",
      File("some/file/path"),
      androidProject,
      "variantName"
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
    com.android.ide.common.gradle.model.level2.IdeAndroidLibrary(
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
    com.android.ide.common.gradle.model.level2.IdeJavaLibrary("artifactAddress", File("artifactFile"), true)
  }

  @Test
  fun level2ModuleLibrary() = assertSerializable {
    IdeModuleLibrary("projectPath", "artifactAddress", "buildId")
  }

  @Test
  fun level2Dependencies() = assertSerializable {
    // We use a local one to avoid changing the global one that is used for other tests.
    val localDependenciesFactory = IdeDependenciesFactory()
    localDependenciesFactory.create(AndroidArtifactStub()) as IdeDependenciesImpl
  }

  /*
   * END LEVEL2 DEPENDENCY MODELS
   * BEGIN OTHER SHARED (IDE+LINT) MODELS
   */

  @Test
  fun aaptOptions() =
    assertSerializable { IdeAaptOptionsImpl(AaptOptionsStub()) }

  @Test
  fun androidArtifact() =
    assertSerializable {
      IdeAndroidArtifactImpl(AndroidArtifactStub(), modelCache,
                                                                                         dependenciesFactory, gradleVersion)
    }

  @Test
  fun androidArtifactOutput() =
    assertSerializable {
      IdeAndroidArtifactOutputImpl(AndroidArtifactOutputStub(), modelCache)
    }

  @Test
  fun androidProject() =
    assertSerializable {
      IdeAndroidProjectImpl.create(
        AndroidProjectStub("3.6.0"),
        modelCache,
        dependenciesFactory,
        listOf(VariantStub()),
        listOf(SyncIssueStub()))
    }

  @Test
  fun apiVersion() =
    assertSerializable { IdeApiVersionImpl(ApiVersionStub()) }

  @Test
  fun buildType() =
    assertSerializable { IdeBuildTypeImpl(BuildTypeStub(), modelCache) }

  @Test
  fun buildTypeContainer() =
    assertSerializable {
      IdeBuildTypeContainerImpl(BuildTypeContainerStub(), modelCache)
    }

  @Test
  fun classField() =
    assertSerializable { IdeClassFieldImpl(ClassFieldStub()) }

  @Test
  fun filterData() =
    assertSerializable { IdeFilterDataImpl(FilterDataStub()) }

  @Test
  fun javaArtifact() =
    assertSerializable {
      IdeJavaArtifactImpl(JavaArtifactStub(), modelCache, dependenciesFactory,
                                                                                      gradleVersion)
    }

  @Test
  fun javaCompileOptions() =
    assertSerializable { IdeJavaCompileOptionsImpl(JavaCompileOptionsStub()) }

  @Test
  fun lintOptions() =
    assertSerializable { IdeLintOptions(LintOptionsStub(), gradleVersion) }

  @Test
  fun mavenCoordinates() =
    assertSerializable { IdeMavenCoordinatesImpl(MavenCoordinatesStub()) }

  @Test
  fun nativeAndroidProjectImpl() =
    assertSerializable {
      IdeNativeAndroidProjectImpl(NativeAndroidProjectStub())
    }

  @Test
  fun nativeArtifact() =
    assertSerializable {
      IdeNativeArtifactImpl(NativeArtifactStub(), modelCache)
    }

  @Test
  fun nativeFile() =
    assertSerializable { IdeNativeFileImpl(NativeFileStub()) }

  @Test
  fun nativeSettings() =
    assertSerializable { IdeNativeSettingsImpl(NativeSettingsStub()) }

  @Test
  fun nativeToolchain() =
    assertSerializable { IdeNativeToolchainImpl(NativeToolchainStub()) }

  @Test
  fun nativeVariantAbi() =
    assertSerializable {
      IdeNativeVariantAbiImpl(NativeVariantAbiStub(), modelCache)
    }

  @Test
  fun outputFile() =
    assertSerializable { IdeOutputFileImpl(OutputFileStub(), modelCache) }

  @Test
  fun productFlavor() =
    assertSerializable { IdeProductFlavorImpl(ProductFlavorStub(), modelCache) }

  @Test
  fun productFlavorContainer() =
    assertSerializable {
      IdeProductFlavorContainerImpl(ProductFlavorContainerStub(), modelCache)
    }

  @Test
  fun signingConfig() =
    assertSerializable { IdeSigningConfigImpl(SigningConfigStub()) }

  @Test
  fun sourceProvider() =
    assertSerializable { IdeSourceProviderImpl.create(SourceProviderStub(), deduplicate = { this }) }

  @Test
  fun sourceProviderContainer() =
    assertSerializable {
      IdeSourceProviderContainerImpl(SourceProviderContainerStub(), modelCache)
    }

  @Test
  fun syncIssue() =
    assertSerializable { IdeSyncIssueImpl(SyncIssueStub()) }

  @Test
  fun testedTargetVariant() =
    assertSerializable { IdeTestedTargetVariantImpl(TestedTargetVariantStub()) }

  @Test
  fun testOptions() =
    assertSerializable { IdeTestOptionsImpl(TestOptionsStub()) }

  @Test
  fun variant() =
    assertSerializable {
      IdeVariantImpl(VariantStub(), modelCache, dependenciesFactory,
                                                                                 gradleVersion)
    }

  @Test
  fun vectorDrawablesOptions() =
    assertSerializable {
      IdeVectorDrawablesOptionsImpl(VectorDrawablesOptionsStub())
    }

  @Test
  fun viewBindingOptions() {
    assertSerializable { IdeViewBindingOptionsImpl(ViewBindingOptionsStub()) }
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
