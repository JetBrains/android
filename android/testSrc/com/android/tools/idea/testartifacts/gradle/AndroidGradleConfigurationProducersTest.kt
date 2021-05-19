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
package com.android.tools.idea.testartifacts.gradle

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testartifacts.TestConfigurationTesting

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import junit.framework.TestCase
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Assert

/**
 * Tests for producing Gradle Run Configuration for Android unit test.
 */
class AndroidGradleConfigurationProducersTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.GRADLE_UNIT_TESTING.override(true)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    super.tearDown()
    StudioFlags.GRADLE_UNIT_TESTING.clearOverride()
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestClass() {
    loadSimpleApplication()
    TestCase.assertNotNull(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestClass() {
    loadSimpleApplication()
    TestCase.assertNull(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.ApplicationTest"))
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestDirectory() {
    loadSimpleApplication()
    TestCase.assertNotNull(createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestDirectory() {
    loadSimpleApplication()
    TestCase.assertNull(createAndroidGradleConfigurationFromDirectory(project, "app/src/androidTest/java"))
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestDirectoryKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNotNull(createAndroidGradleConfigurationFromDirectory(
      project, "app/src/test/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestDirectoryKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNull(createAndroidGradleConfigurationFromDirectory(
      project, "app/src/androidTest/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestClassKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNull(createAndroidGradleConfigurationFromFile(
      project, "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"))
  }

  private fun createAndroidGradleTestConfigurationFromClass(project: Project, qualifiedName: String) : GradleRunConfiguration? {
    val element = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
    Assert.assertNotNull(element)
    return createGradleConfigurationFromPsiElement(project, element!!)
  }

  private fun createAndroidGradleConfigurationFromDirectory(project: Project, directory: String) : GradleRunConfiguration? {
    val element = getPsiElement(project, directory, true)
    return createGradleConfigurationFromPsiElement(project, element)
  }

  private fun createAndroidGradleConfigurationFromFile(project: Project, file: String) : GradleRunConfiguration? {
    val element = getPsiElement(project, file, false)
    return createGradleConfigurationFromPsiElement(project, element)
  }

  private fun createGradleConfigurationFromPsiElement(project: Project, psiElement: PsiElement) : GradleRunConfiguration? {
    val context = TestConfigurationTesting.createContext(project, psiElement)
    val settings = context.configuration ?: return null
    val configuration = settings.configuration
    if (configuration is GradleRunConfiguration) return configuration else return null
  }

  private fun getPsiElement(project: Project, file: String, isDirectory: Boolean): PsiElement {
    val virtualFile = VfsUtilCore.findRelativeFile(file, project.baseDir)
    Assert.assertNotNull(virtualFile)
    val element: PsiElement? = if (isDirectory) PsiManager.getInstance(project).findDirectory(virtualFile!!)
    else PsiManager.getInstance(project).findFile(virtualFile!!)
    Assert.assertNotNull(element)
    return element!!
  }
}