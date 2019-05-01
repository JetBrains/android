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
package com.android.tools.idea.tests.gui.npw

import com.android.tools.idea.flags.StudioFlags.NPW_OFFLINE_REPO_CHECKBOX
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class OfflineRepoTest {
  @JvmField @Rule
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    NPW_OFFLINE_REPO_CHECKBOX.override(true)
  }

  @After
  fun tearDown() {
    NPW_OFFLINE_REPO_CHECKBOX.clearOverride()
  }

  @Test
  @Throws(Exception::class)
  fun testOfflineRepoEnabled() {
    newProject(true)

    val buildGradleText = guiTest.getProjectFileText("build.gradle")
    assertThat(buildGradleText).contains("Properties properties = new Properties()")
    assertThat(buildGradleText).contains("""properties.getProperty("offline.repo").split(",").each { repo ->""")
  }

  @Test
  @Throws(Exception::class)
  fun testOfflineRepoDisabled() {
    newProject(false)

    val buildGradleText = guiTest.getProjectFileText("build.gradle")
    assertThat(buildGradleText).doesNotContain("Properties properties = new Properties()")
    assertThat(buildGradleText).doesNotContain("""properties.getProperty("offline.repo").split(",").each { repo ->""")
  }

  private fun newProject(offlineRepoEnabled: Boolean) {
    guiTest
      .welcomeFrame()
      .createNewProject()
      .clickNext()
      .configureNewAndroidProjectStep
      .selectMinimumSdkApi("27")
      .setSourceLanguage("Java")
      .setUseOfflineRepo(offlineRepoEnabled)
      .wizard()
      .clickFinish()
  }
}
