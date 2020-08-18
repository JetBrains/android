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
package com.android.tools.idea.testartifacts.junit

import com.android.tools.idea.testartifacts.TestConfigurationTesting.createContext
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings

// TODO (karimai) : delete this when test Runner is only through Gradle.
class AndroidTestObjectTest : AndroidGradleTestCase() {
  fun testGetListener() {
    loadSimpleApplication()

    val testClass = myFixture.findClass("google.simpleapplication.UnitTest")
    val testMethod = testClass.findMethodsByName("passingTest", false).single()
    val testPackage = myFixture.findPackage("google.simpleapplication")

    val runManager = RunManager.getInstance(project)
    val testClassRunnerAndSettings = createContext(project, testClass).configuration!!.also(runManager::addConfiguration)
    val testMethodRunnerAndSettings = createContext(project, testMethod).configuration!!.also(runManager::addConfiguration)
    val testPackageRunnerAndSettings = createContext(project, testPackage).configuration!!.also(runManager::addConfiguration)

    // Different configurations may care or not about these, we exercise both code paths.
    myFixture.renameElement(testClass, "RenamedUnitTest")
    myFixture.renameElement(testMethod, "someTest")
    myFixture.renameElement(testPackage, "mypackage")

    assertThat(testClassRunnerAndSettings.toAndroidJUnit().persistentData.mainClassName)
      .isEqualTo("google.mypackage.RenamedUnitTest")

    assertThat(testMethodRunnerAndSettings.toAndroidJUnit().persistentData.mainClassName)
      .isEqualTo("google.mypackage.RenamedUnitTest")
    assertThat(testMethodRunnerAndSettings.toAndroidJUnit().persistentData.methodName)
      .isEqualTo("someTest")

    assertThat(testPackageRunnerAndSettings.toAndroidJUnit().persistentData.packageName)
      .isEqualTo("google.mypackage")
  }

  private fun RunnerAndConfigurationSettings.toAndroidJUnit() = configuration as AndroidJUnitConfiguration
}