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
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.AndroidModuleDescriptors
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModuleDefaultConfigDescriptors
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.withoutKtsRelatedIndexing
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ExtractVariableWorkerTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private fun doTestExtractVariable(resolvedProject: Project) {

    val project = PsProjectImpl(resolvedProject)
    val appModule = project.findModuleByName("app") as PsAndroidModule
    val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule)

    run {
      val worker = ExtractVariableWorker(compileSdkVersion)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("compileSdkVersion"))
      assertThat(newProperty.getParsedValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString().asParsed().annotated()))

      worker.commit("compileSdkVersion")
      assertThat(compileSdkVersion.getParsedValue(),
                 equalTo(ParsedValue.Set.Parsed(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
                                                DslText.Reference("compileSdkVersion"))
                                                           .annotated()))
      assertThat(appModule.variables.getOrCreateVariable("compileSdkVersion").value,
                 equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.asParsed<Any>()))
    }

    run {
      val worker = ExtractVariableWorker(compileSdkVersion)
      val (newName, newProperty) = worker.changeScope(appModule.variables, "")
      assertThat(newName, equalTo("compileSdkVersion1"))   // The second suggested name is the preferredName + "1".
      assertThat(newProperty.getParsedValue(),
                 equalTo(ParsedValue.Set.Parsed(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
                                                DslText.Reference("compileSdkVersion"))
                                                           .annotated()))

      worker.commit("otherName")
      assertThat(compileSdkVersion.getParsedValue(),
                 equalTo(ParsedValue.Set.Parsed(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
                                                DslText.Reference("otherName"))
                                                           .annotated()))
      assertThat(appModule.variables.getOrCreateVariable("otherName").value,
                 equalTo(
                   ParsedValue.Set.Parsed(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, DslText.Reference("compileSdkVersion"))))
    }
  }

  @Test
  fun testExtractVariableGroovy() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      doTestExtractVariable(resolvedProject)
    }
  }

  @Test
  fun testExtractVariableKotlin() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_KOTLIN, "p")
    preparedProject.open(updateOptions = OpenPreparedProjectOptions::withoutKtsRelatedIndexing) { resolvedProject ->
      doTestExtractVariable(resolvedProject)
    }
  }

  @Test
  fun testExtractVariable_projectLevel() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject)
      val appModule = project.findModuleByName("app") as PsAndroidModule
      val compileSdkVersion = AndroidModuleDescriptors.compileSdkVersion.bind(appModule)

      run {
        val worker = ExtractVariableWorker(compileSdkVersion)
        val (newName, newProperty) = worker.changeScope(appModule.variables, "")
        assertThat(newName, equalTo("compileSdkVersion"))
        assertThat(newProperty.getParsedValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString().asParsed().annotated()))


        val (newName2, newProperty2) = worker.changeScope(project.variables, "renamedName")
        assertThat(newName2, equalTo("renamedName"))
        assertThat(newProperty2.getParsedValue(), equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString().asParsed().annotated()))

        worker.commit("renamedName")
        assertThat(
          compileSdkVersion.getParsedValue(),
          equalTo(
            ParsedValue.Set.Parsed(
              SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString(),
              DslText.Reference("renamedName")
            )
              .annotated()
          )
        )
        assertThat(appModule.variables.getVariable("renamedName"), nullValue())

        assertThat(
          project.variables.getOrCreateVariable("renamedName").value,
          equalTo(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.asParsed<Any>())
        )
      }
    }
  }

  @Test
  fun testExtractEmptyValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject)
      val appModule = project.findModuleByName("app") as PsAndroidModule
      val targetCompatibility = AndroidModuleDescriptors.targetCompatibility.bind(appModule)

      run {
        val worker = ExtractVariableWorker(targetCompatibility)
        val (newName, newProperty) = worker.changeScope(appModule.variables, "")
        assertThat(newName, equalTo("targetCompatibility"))
        assertThat(newProperty.getParsedValue(), equalTo(ParsedValue.NotSet.annotated()))

        assertThat(worker.validate("targetCompatibility"), equalTo("Cannot bind a variable to an empty value."))
      }
    }
  }

  @Test
  fun testExtractVariableWithBlankName() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject)
      val appModule = project.findModuleByName("app") as PsAndroidModule
      val targetCompatibility = AndroidModuleDescriptors.targetCompatibility.bind(appModule)

      run {
        val worker = ExtractVariableWorker(targetCompatibility)
        val (newName, newProperty) = worker.changeScope(appModule.variables, "")
        assertThat(newName, equalTo("targetCompatibility"))
        assertThat(newProperty.getParsedValue(), equalTo(ParsedValue.NotSet.annotated()))

        assertThat(worker.validate(" "), equalTo("Variable name is required."))
      }
    }
  }

  @Test
  fun testExtractAndroidModuleDependencyVersion() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.UNIT_TESTING, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject)
      val appModule = project.findModuleByName("app") as PsAndroidModule
      val junit = appModule.dependencies.findLibraryDependencies("junit:junit:4.12").firstOrNull()
      val mockito = appModule.dependencies.findLibraryDependencies("org.mockito:mockito-core:4.8.0").firstOrNull()

      assertThat(junit, notNullValue())
      assertThat(mockito, notNullValue())

      run {
        val junitVersion = junit!!.versionProperty.bind(Unit)
        val worker = ExtractVariableWorker(junitVersion)
        val (newName, newProperty) = worker.changeScope(appModule.variables, "")
        assertThat(newName, equalTo("junitVersion"))
        assertThat(
          newProperty.getParsedValue(),
          equalTo(ParsedValue.Set.Parsed("4.12", DslText.Literal).annotated())
        )
        worker.commit("junitVersion")

        assertThat(
          appModule.variables.getOrCreateVariable("junitVersion").value,
          equalTo(ParsedValue.Set.Parsed("4.12", DslText.Literal))
        )
      }

      run {
        val mockitoVersion = mockito!!.versionProperty.bind(Unit)
        val worker = ExtractVariableWorker(mockitoVersion)
        val (newName, newProperty) = worker.changeScope(appModule.variables, "")
        assertThat(newName, equalTo("mockitoCoreVersion"))
        assertThat(
          newProperty.getParsedValue(),
          equalTo(ParsedValue.Set.Parsed("4.8.0", DslText.Literal).annotated())
        )
        worker.commit("mockitoCoreVersion")

        assertThat(
          appModule.variables.getOrCreateVariable("mockitoCoreVersion").value,
          equalTo(ParsedValue.Set.Parsed("4.8.0", DslText.Literal))
        )
      }
    }
  }

  @Test
  fun testExtractJavaModuleDependencyVersion() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.UNIT_TESTING, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject)
      val javaModule = project.findModuleByName("javalib") as PsJavaModule
      val junit = javaModule.dependencies.findLibraryDependencies("junit:junit:4.12").firstOrNull()
      val mockito = javaModule.dependencies.findLibraryDependencies("org.mockito:mockito-core:4.8.0").firstOrNull()

      assertThat(junit, notNullValue())
      assertThat(mockito, notNullValue())

      run {
        val junitVersion = junit!!.versionProperty.bind(Unit)
        val worker = ExtractVariableWorker(junitVersion)
        val (newName, newProperty) = worker.changeScope(javaModule.variables, "")
        assertThat(newName, equalTo("junitVersion"))
        assertThat(
          newProperty.getParsedValue(),
          equalTo(ParsedValue.Set.Parsed("4.12", DslText.Literal).annotated())
        )
        worker.commit("junitVersion")

        assertThat(
          javaModule.variables.getOrCreateVariable("junitVersion").value,
          equalTo(ParsedValue.Set.Parsed("4.12", DslText.Literal))
        )
      }

      run {
        val mockitoVersion = mockito!!.versionProperty.bind(Unit)
        val worker = ExtractVariableWorker(mockitoVersion)
        val (newName, newProperty) = worker.changeScope(javaModule.variables, "")
        assertThat(newName, equalTo("mockitoCoreVersion"))
        assertThat(
          newProperty.getParsedValue(),
          equalTo(ParsedValue.Set.Parsed("4.8.0", DslText.Literal).annotated())
        )
        worker.commit("mockitoCoreVersion")

        assertThat(
          javaModule.variables.getOrCreateVariable("mockitoCoreVersion").value,
          equalTo(ParsedValue.Set.Parsed("4.8.0", DslText.Literal))
        )
      }
    }
  }

  @Test
  fun testPreferredVariableNames() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject)
      val appModule = project.findModuleByName("app") as PsAndroidModule

      fun <T : Any> checkPreferredName(property: ModelPropertyCore<T>, expectedName: String, expectedValue: T? = null) {
        val worker = ExtractVariableWorker(property)
        val (newName, newProperty) = worker.changeScope(appModule.variables, "")
        assertThat(newName, equalTo(expectedName))
        if (expectedValue != null) {
          assertThat(newProperty.getParsedValue().value.maybeValue, equalTo(expectedValue))
        }
        worker.cancel()
      }

      // applicationId
      run {
        val applicationId = PsAndroidModuleDefaultConfigDescriptors.applicationId.bind(appModule.defaultConfig)
        checkPreferredName(applicationId, "defaultApplicationId", "com.example.psd.sample.app.default")
      }
      run {
        val applicationId =
          PsProductFlavor.ProductFlavorDescriptors.applicationId.bind(appModule.productFlavors.find { it.name == "paid" }!!)
        checkPreferredName(applicationId, "paidApplicationId", "com.example.psd.sample.app.paid")
      }

      // applicationIdSuffix
      run {
        val applicationIdSuffix = PsAndroidModuleDefaultConfigDescriptors.applicationIdSuffix.bind(appModule.defaultConfig)
        checkPreferredName(applicationIdSuffix, "defaultApplicationIdSuffix")
      }
      run {
        val applicationIdSuffix =
          PsBuildType.BuildTypeDescriptors.applicationIdSuffix.bind(appModule.buildTypes.find { it.name == "release" }!!)
        checkPreferredName(applicationIdSuffix, "releaseApplicationIdSuffix", "suffix")
      }
      run {
        val applicationIdSuffix =
          PsProductFlavor.ProductFlavorDescriptors.applicationIdSuffix.bind(appModule.productFlavors.find { it.name == "bar" }!!)
        checkPreferredName(applicationIdSuffix, "barApplicationIdSuffix", "barSuffix")
      }

      // multiDexEnabled
      run {
        val multiDexEnabled = PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled.bind(appModule.defaultConfig)
        checkPreferredName(multiDexEnabled, "defaultMultiDexEnabled")
      }
      run {
        val multiDexEnabled = PsBuildType.BuildTypeDescriptors.multiDexEnabled.bind(appModule.buildTypes.find { it.name == "release" }!!)
        checkPreferredName(multiDexEnabled, "releaseMultiDexEnabled")
      }
      run {
        val multiDexEnabled = PsProductFlavor.ProductFlavorDescriptors.multiDexEnabled.bind(
          appModule.productFlavors.find { it.name == "bar" }!!
        )
        checkPreferredName(multiDexEnabled, "barMultiDexEnabled")
      }

      // {max,min,target}SdkVersion
      run {
        val maxSdkVersion = PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion.bind(appModule.defaultConfig)
        val minSdkVersion = PsAndroidModuleDefaultConfigDescriptors.minSdkVersion.bind(appModule.defaultConfig)
        val targetSdkVersion = PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion.bind(appModule.defaultConfig)
        checkPreferredName(maxSdkVersion, "defaultMaxSdkVersion", 26)
        // do not be fooled by the literal 9 in psdSample/app/build.gradle: it gets overwritten on project setup
        // (see AndroidGradleTests.updateMinSdkVersion)
        checkPreferredName(minSdkVersion, "defaultMinSdkVersion", SdkVersionInfo.LOWEST_ACTIVE_API.toString())
        checkPreferredName(targetSdkVersion, "defaultTargetSdkVersion", SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString())
      }
      run {
        val paidProductFlavor = appModule.productFlavors.find { it.name == "paid" }!!
        val maxSdkVersion = PsProductFlavor.ProductFlavorDescriptors.maxSdkVersion.bind(paidProductFlavor)
        val minSdkVersion = PsProductFlavor.ProductFlavorDescriptors.minSdkVersion.bind(paidProductFlavor)
        val targetSdkVersion = PsProductFlavor.ProductFlavorDescriptors.targetSdkVersion.bind(paidProductFlavor)
        checkPreferredName(maxSdkVersion, "paidMaxSdkVersion", 25)
        checkPreferredName(minSdkVersion, "paidMinSdkVersion", "10")
        checkPreferredName(targetSdkVersion, "paidTargetSdkVersion", "20")
      }

      // test{ApplicationId,FunctionalTest,HandleProfiling,InstrumentationRunner}
      run {
        val testApplicationId = PsAndroidModuleDefaultConfigDescriptors.testApplicationId.bind(appModule.defaultConfig)
        val testFunctionalTest = PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest.bind(appModule.defaultConfig)
        val testHandleProfiling = PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling.bind(appModule.defaultConfig)
        val testInstrumentationRunner = PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner.bind(appModule.defaultConfig)
        checkPreferredName(testApplicationId, "defaultTestApplicationId", "com.example.psd.sample.app.default.test")
        checkPreferredName(testFunctionalTest, "defaultTestFunctionalTest", false)
        checkPreferredName(testHandleProfiling, "defaultTestHandleProfiling")
        checkPreferredName(testInstrumentationRunner, "defaultTestInstrumentationRunner")
      }
      run {
        val paidProductFlavor = appModule.productFlavors.find { it.name == "paid" }!!
        val testApplicationId = PsProductFlavor.ProductFlavorDescriptors.testApplicationId.bind(paidProductFlavor)
        val testFunctionalTest = PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest.bind(paidProductFlavor)
        val testHandleProfiling = PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling.bind(paidProductFlavor)
        val testInstrumentationRunner = PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunner.bind(paidProductFlavor)
        checkPreferredName(testApplicationId, "paidTestApplicationId", "com.example.psd.sample.app.paid.test")
        checkPreferredName(testFunctionalTest, "paidTestFunctionalTest", true)
        checkPreferredName(testHandleProfiling, "paidTestHandleProfiling", true)
        checkPreferredName(testInstrumentationRunner, "paidTestInstrumentationRunner")
      }

      // version{Code,Name,NameSuffix}
      run {
        val versionCode = PsAndroidModuleDefaultConfigDescriptors.versionCode.bind(appModule.defaultConfig)
        val versionName = PsAndroidModuleDefaultConfigDescriptors.versionName.bind(appModule.defaultConfig)
        val versionNameSuffix = PsAndroidModuleDefaultConfigDescriptors.versionNameSuffix.bind(appModule.defaultConfig)
        checkPreferredName(versionCode, "defaultVersionCode", 1)
        checkPreferredName(versionName, "defaultVersionName", "1.0")
        checkPreferredName(versionNameSuffix, "defaultVersionNameSuffix", "vns")
      }
      run {
        val versionNameSuffix =
          PsBuildType.BuildTypeDescriptors.versionNameSuffix.bind(appModule.buildTypes.find { it.name == "release" }!!)
        checkPreferredName(versionNameSuffix, "releaseVersionNameSuffix", "vsuffix")
      }
      run {
        val paidProductFlavor = appModule.productFlavors.find { it.name == "paid" }!!
        val versionCode = PsProductFlavor.ProductFlavorDescriptors.versionCode.bind(paidProductFlavor)
        val versionName = PsProductFlavor.ProductFlavorDescriptors.versionName.bind(paidProductFlavor)
        val versionNameSuffix = PsProductFlavor.ProductFlavorDescriptors.versionNameSuffix.bind(paidProductFlavor)
        checkPreferredName(versionCode, "paidVersionCode", 2)
        checkPreferredName(versionName, "paidVersionName", "2.0")
        checkPreferredName(versionNameSuffix, "paidVersionNameSuffix", "vnsFoo")
      }

      // buildType-only properties
      run {
        val releaseBuildType = appModule.buildTypes.find { it.name == "release" }!!
        val debuggable = PsBuildType.BuildTypeDescriptors.debuggable.bind(releaseBuildType)
        val jniDebuggable = PsBuildType.BuildTypeDescriptors.jniDebuggable.bind(releaseBuildType)
        val minifyEnabled = PsBuildType.BuildTypeDescriptors.minifyEnabled.bind(releaseBuildType)
        val renderscriptDebuggable = PsBuildType.BuildTypeDescriptors.renderscriptDebuggable.bind(releaseBuildType)
        val renderscriptOptimLevel = PsBuildType.BuildTypeDescriptors.renderscriptOptimLevel.bind(releaseBuildType)
        val testCoverageEnabled = PsBuildType.BuildTypeDescriptors.testCoverageEnabled.bind(releaseBuildType)
        checkPreferredName(debuggable, "releaseDebuggable", false)
        checkPreferredName(jniDebuggable, "releaseJniDebuggable", false)
        checkPreferredName(minifyEnabled, "releaseMinifyEnabled", false)
        checkPreferredName(renderscriptDebuggable, "releaseRenderscriptDebuggable")
        checkPreferredName(renderscriptOptimLevel, "releaseRenderscriptOptimLevel", 2)
        checkPreferredName(testCoverageEnabled, "releaseTestCoverageEnabled")
      }
    }
  }
}
