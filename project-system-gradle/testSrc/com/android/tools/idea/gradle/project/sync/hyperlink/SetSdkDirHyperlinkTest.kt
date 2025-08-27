/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.tools.idea.testing.FileSubject.file
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.onEdt
import com.android.utils.FileUtils
import com.android.utils.SdkUtils.escapePropertyValue
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil.loadFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import java.io.File
import javax.swing.event.HyperlinkEvent
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SetSdkDirHyperlinkTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }
  val projectFolderPath by lazy { File(project.basePath!!) }

  val integrationTestEnvironment = object : IntegrationTestEnvironment {
    override fun getBaseTestPath() = FileUtils.toSystemIndependentPath(fixture.tempDirPath)
  }

  @Before
  fun setup() {
    val application = ApplicationManager.getApplication()
    application.replaceService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker(), fixture.testRootDisposable)
  }

  @Test
  fun testSdkDirHyperlinkUpdatesOnePropertiesFile() {
    integrationTestEnvironment.prepareTestProject(AndroidCoreTestProject.COMPOSITE_BUILD, syncReady = false)

    // Delete the main local.properties file
    val localPropertiesPath = File(projectFolderPath, FN_LOCAL_PROPERTIES)
    deletePropertiesFile(localPropertiesPath)

    val hyperlink = SetSdkDirHyperlink(project, listOf(localPropertiesPath.absolutePath))
    assertThat(hyperlink.executeIfClicked(project, HyperlinkEvent(this, null, null, hyperlink.url))).isTrue()
    assertAbout(file()).that(localPropertiesPath).isFile()
    assertThat(loadFile(localPropertiesPath)).named("Local properties must contain sdk.dir")
      .contains("sdk.dir=${escapePropertyValue(AndroidSdks.getInstance().tryToChooseAndroidSdk()!!.location.toString())}")
  }

  @Test
  fun testSdkDirHyperlinkUpdatesMultiplePropertiesFiles() {
    integrationTestEnvironment.prepareTestProject(AndroidCoreTestProject.COMPOSITE_BUILD, syncReady = false)

    // Delete all the properties files we want to re-create
    val localPropertiesPath = File(projectFolderPath, FN_LOCAL_PROPERTIES)
    val localPropertiesPathTwo = File(projectFolderPath.parent, "TestCompositeLib1/$FN_LOCAL_PROPERTIES")
    val localPropertiesPathThree = File(projectFolderPath.parent, "TestCompositeLib3/$FN_LOCAL_PROPERTIES")
    deletePropertiesFile(localPropertiesPath)
    deletePropertiesFile(localPropertiesPathTwo)
    deletePropertiesFile(localPropertiesPathThree)

    val hyperlink = SetSdkDirHyperlink(project,
      listOf(localPropertiesPath.absolutePath, localPropertiesPathTwo.absolutePath, localPropertiesPathThree.absolutePath))
    assertThat(hyperlink.executeIfClicked(project, HyperlinkEvent(this, null, null, hyperlink.url))).isTrue()

    val sdkLocation = escapePropertyValue(AndroidSdks.getInstance().tryToChooseAndroidSdk()!!.location.toString())
    assertAbout(file()).that(localPropertiesPath).isFile()
    assertThat(loadFile(localPropertiesPath)).named("Local properties must contain sdk.dir")
      .contains("sdk.dir=${sdkLocation}")
    assertAbout(file()).that(localPropertiesPathTwo).isFile()
    assertThat(loadFile(localPropertiesPathTwo)).named("Local properties must contain sdk.dir")
      .contains("sdk.dir=${sdkLocation}")
    assertAbout(file()).that(localPropertiesPathThree).isFile()
    assertThat(loadFile(localPropertiesPathThree)).named("Local properties must contain sdk.dir")
      .contains("sdk.dir=${sdkLocation}")
  }

  private fun deletePropertiesFile(localPropertiesPath: File) {
    if (localPropertiesPath.exists()) {
      assertThat(localPropertiesPath.delete()).isTrue()
    }
  }
}