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

import com.android.testutils.TestUtils.getLatestAndroidPlatform
import com.android.testutils.TestUtils.getPlatformFile
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.buildDependenciesStub
import com.android.tools.idea.testing.buildMainArtifactStub
import com.android.tools.idea.testing.buildUnitTestArtifactStub
import com.android.tools.idea.testing.createAndroidProjectBuilder
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.normalize
import com.intellij.testFramework.UsefulTestCase
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
  private lateinit var root: String

  private // Sanity check. These should be fixed by the patcher.
  fun getExampleClasspath(): List<String> {
    root = project.basePath!!
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
      root + "/build/intermediates/java_res/debug/out",
      root + "/build/intermediates/java_res/debugUnitTest/out"
    )

    exampleClassPath.add(0, mockableAndroidJar)
    exampleClassPath.add(0, realAndroidJar)

    exampleClassPathSet = exampleClassPath.toSet()
    assertThat(exampleClassPath).containsAllOf(realAndroidJar, mockableAndroidJar)
    UsefulTestCase.assertDoesntContain(exampleClassPath, resourcesDirs)
    assertThat(Iterables.getLast(exampleClassPath)).isNotEqualTo(mockableAndroidJar)

    return exampleClassPath
  }

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    patcher = AndroidJunitPatcher()
    javaParameters = JavaParameters()
    javaParameters.classPath.addAll(getExampleClasspath())
  }

  private fun setUpProject(builder: AndroidProjectBuilder) {
    setupTestProjectFromAndroidModel(
      project,
      File(root),
      AndroidModuleModelBuilder(":", "debug", builder)
    )
  }

  fun testPathChanges() {
    setUpProject(createAndroidProjectBuilder())
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
    setUpProject(createAndroidProjectBuilder())
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
    setUpProject(createAndroidProjectBuilder())
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
    setUpProject(createAndroidProjectBuilder(
      unitTestArtifactStub = { buildUnitTestArtifactStub(it, mockablePlatformJar = File(mockableAndroidJar)) }
    ))
    javaParameters.classPath.remove(mockableAndroidJar)

    patcher.patchJavaParameters(myModule, javaParameters)

    TestCase.assertEquals(normalize(mockableAndroidJar), normalize(Iterables.getLast(javaParameters.classPath.pathList)))
  }

  fun testKotlinClasses() {
    val testKotlinClassesDir = File(testKotlinClasses)
    setUpProject(createAndroidProjectBuilder(
      mainArtifactStub = { buildMainArtifactStub(it, classFolders = setOf(File(kotlinClasses))) },
      unitTestArtifactStub = { buildUnitTestArtifactStub(it, classFolders = setOf(testKotlinClassesDir)) }
    ))
    javaParameters.classPath.remove(mockableAndroidJar)

    patcher.patchJavaParameters(myModule, javaParameters)

    assertThat(javaParameters.classPath.pathList).contains(testKotlinClassesDir.path)
  }

  fun testRuntimeClasspath() {
    val runtimeJar = "/tmp/runtime.jar"
    // Fix for Windows since the drive will be prepended
    val canonicalName = FileUtil.toCanonicalPath(File(runtimeJar).absolutePath)
    setUpProject(createAndroidProjectBuilder(
      unitTestArtifactStub = {
        buildUnitTestArtifactStub(it, dependencies = buildDependenciesStub(runtimeOnlyClasses = listOf(File(runtimeJar))))
      }
    ))
    patcher.patchJavaParameters(myModule, javaParameters)
    val result = javaParameters.classPath.pathList.map { normalize(it) }
    assertThat(result).contains(canonicalName)
  }
}
