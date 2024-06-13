/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.runsGradle

import com.android.tools.idea.gradle.util.PropertiesFiles
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.util.io.FileUtilRt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class HttpProxySettingsCleanUpTest {

  @get:Rule
  var myRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    // Ensure that gradle.properties include http proxy settings.
    setupGradleHttpProxySettings()
  }

  @After
  fun tearDown() {
    FileUtilRt.delete(getUserGradlePropertiesFile())
  }

  /**
   * Test that when we run [HttpProxySettingsCleanUp.cleanUp] in headless mode,
   * the function does attempt to display [com.android.tools.idea.gradle.project.ProxySettingsDialog]
   * and throw an exception. @see [](http://issuetracker.google.com/290465997)
   */
  @Test
  fun testHttpProxySettingsCleanUpInHeadlessModeWithGradleProperties() {
    // Check if an headless application can load successfully with http proxy settings specified in
    // Gradle properties.
    myRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION)
  }

  private fun setupGradleHttpProxySettings() {
    val gradlePropertiesFile = getUserGradlePropertiesFile()
    FileUtilRt.createIfNotExists(gradlePropertiesFile)
    val gradleProperties = PropertiesFiles.getProperties(gradlePropertiesFile)
    gradleProperties.setProperty("systemProp.http.proxyHost", "myproxy.test.com")
    gradleProperties.setProperty("systemProp.http.proxyPort", "443")
    PropertiesFiles.savePropertiesToFile(gradleProperties, gradlePropertiesFile, "Http Proxy Settings")
  }

  private fun getUserGradlePropertiesFile(): File {
    val home = System.getProperty("user.home")
    return File(File(home), FileUtilRt.toSystemDependentName(".gradle/gradle.properties"))
  }

}