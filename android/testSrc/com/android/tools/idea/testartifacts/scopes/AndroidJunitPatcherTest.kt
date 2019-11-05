/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.scopes

import com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST
import com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST
import com.android.builder.model.BaseArtifact
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.testutils.TestUtils.getLatestAndroidPlatform
import com.android.testutils.TestUtils.getPlatformFile
import com.android.tools.idea.gradle.TestProjects
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub
import com.android.tools.idea.gradle.stubs.android.JavaArtifactStub
import com.android.tools.idea.gradle.stubs.android.VariantStub
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.testing.Facets.createAndAddGradleFacet
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil.normalize
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ArrayUtil.contains
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.sdk.AndroidPlatform
import java.io.File

/**
 * Tests for [AndroidJunitPatcher].
 */
class AndroidJunitPatcherTest : AndroidTestCase() {

  private lateinit var exampleClassPathSet: Set<String>
  private lateinit var realAndroidJar: String
  private lateinit var mockableAndroidJar: String
  private lateinit var kotlinClasses: String
  private lateinit var testKotlinClasses: String
  private lateinit var resourcesDirs: Collection<String>

  private lateinit var patcher: AndroidJunitPatcher
  private lateinit var javaParameters: JavaParameters
  private lateinit var androidProject: AndroidProjectStub
  private lateinit var root: String
  private lateinit var selectedVariant: VariantStub

  private // Sanity check. These should be fixed by the patcher.
  fun getExampleClasspath(): List<String> {
    root = normalize(androidProject.rootDir.path)
    val exampleClassPath = mutableListOf(
      root + "/build/intermediates/classes/debug",
      root + "/build/intermediates/classes/test/debug",
      root + "/build/intermediates/exploded-aar/com.android.support/appcompat-v7/22.0.0/classes.jar",
      root + "/build/intermediates/exploded-aar/com.android.support/appcompat-v7/22.0.0/res",
      root + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/classes.jar",
      root + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/libs/internal_impl-22.0.0.jar",
      root + "/build/intermediates/exploded-aar/com.android.support/support-v4/22.0.0/res",
      "/home/user/.gradle/caches/modules-2/files-2.1/junit/junit/4.12/2973d150c0dc1fefe998f834810d68f278ea58ec/junit-4.12.jar",
      "/idea/production/java-runtime", "/idea/production/junit_rt")

    mockableAndroidJar = root + "/build/intermediates/mockable-" + getLatestAndroidPlatform() + ".jar"
    kotlinClasses = root + "/build/tmp/kotlin-classes/debug"
    testKotlinClasses = root + "/build/tmp/kotlin-classes/debugUnitTest"
    val androidPlatform = AndroidPlatform.getInstance(myModule)
    TestCase.assertNotNull(androidPlatform)
    realAndroidJar = getPlatformFile("android.jar").toString()
    resourcesDirs = listOf(
      root + "/build/intermediates/javaResources/debug",
      root + "/build/intermediates/javaResources/test/debug"
    )

    exampleClassPath.add(0, mockableAndroidJar)
    exampleClassPath.add(0, realAndroidJar)

    exampleClassPathSet = exampleClassPath.toSet()
    assertThat(exampleClassPath).containsAllOf(realAndroidJar, mockableAndroidJar)
    UsefulTestCase.assertDoesntContain(exampleClassPath, resourcesDirs)
    assertThat(Iterables.getLast(exampleClassPath)).isNotEqualTo(mockableAndroidJar)

    return exampleClassPath
  }

  fun getUnitTestArtifact(): JavaArtifactStub? {
    for (artifact in selectedVariant.extraJavaArtifacts) {
      val stub = artifact as JavaArtifactStub
      if (isTestArtifact(stub)) {
        return stub
      }
    }
    return null
  }

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    setUpIdeaAndroidProject()

    patcher = AndroidJunitPatcher()
    javaParameters = JavaParameters()
    javaParameters.classPath.addAll(getExampleClasspath())

    // Adding the facet makes Projects#isBuildWithGradle return 'true'.
    createAndAddGradleFacet(myModule)
  }

  private fun setUpIdeaAndroidProject() {
    androidProject = TestProjects.createBasicProject()
    createAndSetAndroidModel()
    for (module in ModuleManager.getInstance(project).modules) {
      GradleTestArtifactSearchScopes.initializeScope(module)
    }
  }

  fun testPathChanges() {
    patcher.patchJavaParameters(myModule, javaParameters)
    val result = javaParameters.classPath.pathList.map { normalize(it) }
    val resultSet = result.toSet()
    assertThat(result).doesNotContain(realAndroidJar)

    // Mockable JAR is at the end:
    TestCase.assertEquals(mockableAndroidJar, Iterables.getLast(result))
    // Only the real android.jar was removed:
    assertThat(Sets.difference(exampleClassPathSet, resultSet)).contains(realAndroidJar)
    // Only expected entries were added:
    assertThat(Sets.difference(resultSet, exampleClassPathSet)).containsAllIn(resourcesDirs)
  }

  fun testCaseInsensitivity() {
    if (!SystemInfo.isWindows) {
      // This test only makes sense on Windows.
      println("Skipping AndroidJunitPatcherTest#testCaseInsensitivity: not running on Windows.")
      return
    }

    javaParameters.classPath.remove(realAndroidJar)
    // It's still the same file on Windows:
    val alsoRealAndroidJar = realAndroidJar.replace("platforms", "Platforms")
    javaParameters.classPath.addFirst(alsoRealAndroidJar)

    patcher.patchJavaParameters(myModule, javaParameters)
    val result = javaParameters.classPath.pathList
    assertThat(result).containsNoneOf(alsoRealAndroidJar, realAndroidJar)
  }

  fun testMultipleMockableJars_oldModel() {
    val jar22 = root + "lib1/build/intermediates/mockable-android-22.jar"
    val jar15 = root + "lib2/build/intermediates/mockable-android-15.jar"
    val classPath = javaParameters.classPath
    classPath.addFirst(jar22)
    classPath.addFirst(jar15)

    patcher.patchJavaParameters(myModule, javaParameters)

    val pathList = classPath.pathList
    TestCase.assertEquals(mockableAndroidJar, Iterables.getLast(pathList))
    assertThat(pathList).containsNoneOf(jar15, jar22)
  }

  fun testMultipleMockableJars_newModel() {
    javaParameters.classPath.remove(mockableAndroidJar)

    val testArtifact = getUnitTestArtifact()!!
    testArtifact.mockablePlatformJar = File(mockableAndroidJar)
    createAndSetAndroidModel()

    patcher.patchJavaParameters(myModule, javaParameters)

    TestCase.assertEquals(normalize(mockableAndroidJar), normalize(Iterables.getLast(javaParameters.classPath.pathList)))
  }

  fun testKotlinClasses() {
    javaParameters.classPath.remove(mockableAndroidJar)

    val artifact = selectedVariant.mainArtifact
    artifact.addAdditionalClassesFolder(File(kotlinClasses))
    val testArtifact = getUnitTestArtifact()!!
    val testKotlinClassesDir = File(testKotlinClasses)
    testArtifact.addAdditionalClassesFolder(testKotlinClassesDir)
    createAndSetAndroidModel()

    patcher.patchJavaParameters(myModule, javaParameters)

    assertThat(javaParameters.classPath.pathList).contains(testKotlinClassesDir.path)
  }

  fun testRuntimeClasspath() {
    val runtimeJar = "/tmp/runtime.jar"
    val testArtifact = getUnitTestArtifact()!!
    testArtifact.dependencies.runtimeOnlyClasses.add(File(runtimeJar))
    createAndSetAndroidModel()
    patcher.patchJavaParameters(myModule, javaParameters)
    val result = javaParameters.classPath.pathList.map { normalize(it) }
    assertThat(result).contains(runtimeJar)
  }

  private fun createAndSetAndroidModel() {
    selectedVariant = androidProject.firstVariant!!
    TestCase.assertNotNull(selectedVariant)
    val model = AndroidModuleModel
      .create(androidProject.name, androidProject.rootDir, androidProject, selectedVariant.name, IdeDependenciesFactory())
    AndroidModel.set(myFacet, model)
  }
}

private val TEST_ARTIFACT_NAMES = arrayOf(ARTIFACT_UNIT_TEST, ARTIFACT_ANDROID_TEST)

private fun isTestArtifact(artifact: BaseArtifact): Boolean {
  val artifactName = artifact.name
  return isTestArtifact(artifactName)
}

private fun isTestArtifact(artifactName: String?): Boolean {
  return contains(artifactName, *TEST_ARTIFACT_NAMES)
}
