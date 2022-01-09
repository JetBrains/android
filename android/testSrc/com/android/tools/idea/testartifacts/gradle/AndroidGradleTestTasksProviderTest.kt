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
import com.android.tools.idea.gradle.run.AndroidGradleTestTasksProvider
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createContext
import com.android.tools.idea.testartifacts.createGradleConfigurationFromPsiElement
import com.android.tools.idea.testartifacts.getPsiElement
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_WITH_DUPLICATES
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/**
 * Tests for {@link AndroidGradleTestTasksProvider} to verify that GRADLE can create the right run configurations when having same name.
 */
class AndroidGradleTestTasksProviderTest: AndroidGradleTestCase() {

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestClass() {
    loadProject(SIMPLE_APPLICATION_WITH_DUPLICATES)
    var psiElement = getPsiElement(project, "app/src/test/java/google/simpleapplication/UnitTest.java", false)
    val appGradleTestClassConfiguration = createGradleConfigurationFromPsiElement(project, psiElement)
    assertThat(appGradleTestClassConfiguration).isNotNull()

    psiElement = getPsiElement(project, "libs/src/test/java/google/simpleapplication/UnitTest.java", false)
    var libGradleTestClassConfiguration = findExistingGradleTestConfigurationFromPsiElement(project, psiElement)
    // Verify that Gradle doesn't consider the run configuration in libs module equal to the run configuration in app module.
    // The run configuration is null in this case because we can successfully detect in the tasks name that the modules are different
    // between the two contexts.
    assertThat(libGradleTestClassConfiguration).isNull()

    libGradleTestClassConfiguration = createGradleConfigurationFromPsiElement(project, psiElement)
    assertThat(libGradleTestClassConfiguration).isNotNull()
    assertThat(libGradleTestClassConfiguration).isNotSameAs(appGradleTestClassConfiguration)
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestDirectory() {
    loadProject(SIMPLE_APPLICATION_WITH_DUPLICATES)
    val appModulePsiElement = getPsiElement(project, "app/src/test/java/google/simpleapplication", true)
    val appGradleTestPackageConfiguration = createGradleConfigurationFromPsiElement(project, appModulePsiElement)
    assertThat(appGradleTestPackageConfiguration).isNotNull()

    val libModulePsiLocation = getPsiElement(project, "libs/src/test/java/google/simpleapplication", true)
    val libExistingTestPackageConfiguration = findExistingGradleTestConfigurationFromPsiElement(project, libModulePsiLocation)
    // Verify that Gradle doesn't consider the run configuration in libs module equal to the run configuration in app module.
    // The run configuration is null in this case because we can successfully detect in the tasks name that the modules are different
    // between the two contexts.
    assertThat(libExistingTestPackageConfiguration).isNull()

    val libGradleTestPackageConfiguration = createGradleConfigurationFromPsiElement(project, libModulePsiLocation)
    assertThat(libGradleTestPackageConfiguration).isNotNull()
    assertThat(libGradleTestPackageConfiguration).isNotSameAs(appGradleTestPackageConfiguration)
  }

  @Throws(Exception::class)
  fun testAndroidGradleTestTaskProviderIsNotRegistered() {
    val androidGradleTestTasksProvider = GradleTestTasksProvider.EP_NAME.extensions.filterIsInstance<AndroidGradleTestTasksProvider>()
    assertThat(androidGradleTestTasksProvider).isEmpty()
  }

  private fun findExistingGradleTestConfigurationFromPsiElement(project: Project, psiElement: PsiElement): GradleRunConfiguration? {
    val context = createContext(project, psiElement)
    // Search for any existing run configuration that was created from this context.
    val existing = context.findExisting() ?: return null
    return existing.configuration  as? GradleRunConfiguration
  }
}